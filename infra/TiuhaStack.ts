import * as cdk from '@aws-cdk/core'
import * as cassandra from '@aws-cdk/aws-cassandra'
import * as iam from '@aws-cdk/aws-iam'
import * as ec2 from '@aws-cdk/aws-ec2'
import * as ecr from '@aws-cdk/aws-ecr'
import * as ecs from '@aws-cdk/aws-ecs'
import * as logs from '@aws-cdk/aws-logs'
import * as s3 from '@aws-cdk/aws-s3'
import * as rds from '@aws-cdk/aws-rds'

type TiuhaStackProps = cdk.StackProps & {
  envName: string
  measurementApiRepository: ecr.IRepository
  titanQCRepository: ecr.IRepository
  versionTag: string
}

export class TiuhaStack extends cdk.Stack {
  importBucket: s3.Bucket

  constructor(scope: cdk.Construct, id: string, props: TiuhaStackProps) {
    super(scope, id, props)

    const measurementsKeyspace = new cassandra.CfnKeyspace(this, 'Measurements', {
      keyspaceName: 'measurements',
    })

    measurementsKeyspace.applyRemovalPolicy(cdk.RemovalPolicy.RETAIN)

    const vpc = this.createVpc()

    const envName = props.envName.toLowerCase()
    const measurementsBucket = new s3.Bucket(this, 'measurementsBucket', {
      bucketName: `fmi-tiuha-measurements-${envName}`,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
    })

    this.importBucket = new s3.Bucket(this, 'ImportBucket', {
      bucketName: `fmi-tiuha-import-${envName}`,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
    })

    const dbCredentials = rds.Credentials.fromGeneratedSecret('tiuha', {
      secretName: 'tiuha-database-credentials'
    })

    const [db, dbSG] = this.createDatabase(vpc, dbCredentials)

    const cluster = new ecs.Cluster(this, 'Cluster', { vpc })
    const apiImage = ecs.ContainerImage.fromEcrRepository(props.measurementApiRepository, props.versionTag)
    const [service, serviceSG] = this.createFargateService(cluster, apiImage, db, measurementsBucket)

    const titanImage = ecs.ContainerImage.fromEcrRepository(props.titanQCRepository, props.versionTag)
    this.createTitanlibTask(titanImage)

    const bastionSecurityGroup = this.createBastionHost(vpc)

    const postgresPort = ec2.Port.tcp(5432)
    dbSG.addIngressRule(bastionSecurityGroup, postgresPort)
    dbSG.addIngressRule(serviceSG, postgresPort)
  }

  createVpc(): ec2.IVpc {
    return new ec2.Vpc(this, "DefaultVpc", {})
  }

  createDatabase(vpc: ec2.IVpc, credentials: rds.Credentials): [rds.DatabaseCluster, ec2.SecurityGroup] {

    const securtiyGroup = new ec2.SecurityGroup(this, 'DatabaseClusterSecurityGroup', { vpc })

    const engine = rds.DatabaseClusterEngine.auroraPostgres({
      version: rds.AuroraPostgresEngineVersion.VER_12_6,
    })

    const cluster = new rds.DatabaseCluster(this, 'DatabaseCluster', {
      engine,
      defaultDatabaseName: 'tiuha',
      credentials,
      instances: 2,
      instanceProps: {
        vpc,
        securityGroups: [securtiyGroup],
        vpcSubnets: {
          subnetType: ec2.SubnetType.PRIVATE_WITH_NAT,
        },
        instanceType: ec2.InstanceType.of(ec2.InstanceClass.BURSTABLE3, ec2.InstanceSize.MEDIUM),
      },
      cloudwatchLogsRetention: logs.RetentionDays.INFINITE,
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    })

    if (!cluster.secret) throw new Error("Expected DatabaseCluster.secret to be undefined")


    return [cluster, securtiyGroup]
  }

  createTitanlibTask(image: ecs.ContainerImage) {
    const logGroup = new logs.LogGroup(this, 'QCLogGroup', {
      logGroupName: 'qc',
      retention: logs.RetentionDays.INFINITE,
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    })

    const taskDefinition = new ecs.FargateTaskDefinition(this, 'TitanlibTask', {
      cpu: 256,
      memoryLimitMiB: 512,
    })
    taskDefinition.addContainer('TitanlibContainer', {
      image,
      logging: new ecs.AwsLogDriver({
        logGroup,
        streamPrefix: 'titan'
      })
    })

    this.importBucket.grantRead(taskDefinition.taskRole)
  }

  createFargateService(
    cluster: ecs.ICluster,
    containerImage: ecs.ContainerImage,
    db: rds.DatabaseCluster,
    measurementsBucket: s3.Bucket
  ): [ecs.FargateService, ec2.SecurityGroup] {
    const logGroup = new logs.LogGroup(this, 'LogGroup', {
      logGroupName: 'measurement-api',
      retention: logs.RetentionDays.INFINITE,
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    })

    const taskDefinition = new ecs.FargateTaskDefinition(this, 'MeasurementApiTask', {
      cpu: 1024,
      memoryLimitMiB: 2048,
    })

    taskDefinition.addContainer('MeasurementApiContainer', {
      image: containerImage,
      logging: new ecs.AwsLogDriver({
        logGroup,
        streamPrefix: 'measurement-api',
      }),
      environment: {
        IMPORT_BUCKET: this.importBucket.bucketName,
      },
      secrets: {
        DATABASE_NAME: ecs.Secret.fromSecretsManager(db.secret!, 'dbname'),
        DATABASE_HOST: ecs.Secret.fromSecretsManager(db.secret!, 'host'),
        DATABASE_PORT: ecs.Secret.fromSecretsManager(db.secret!, 'port'),
        DATABASE_USERNAME: ecs.Secret.fromSecretsManager(db.secret!, 'username'),
        DATABASE_PASSWORD: ecs.Secret.fromSecretsManager(db.secret!, 'password'),
      }
    })

    taskDefinition.addToTaskRolePolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: ['cassandra:*'],
      resources: ['*'],
    }))

    measurementsBucket.grantReadWrite(taskDefinition.taskRole)
    this.importBucket.grantReadWrite(taskDefinition.taskRole)

    taskDefinition.addToTaskRolePolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: ['secretsmanager:GetSecretValue'],
      resources: ['*']
    }))


    const securityGroup = new ec2.SecurityGroup(this, 'MeasurementApiSecurityGroup', { vpc: cluster.vpc })

    const service = new ecs.FargateService(this, 'MeasurementApiService', {
      cluster,
      taskDefinition,
      securityGroups: [securityGroup],
      desiredCount: 2,
      minHealthyPercent: 100,
      maxHealthyPercent: 200,
      platformVersion: ecs.FargatePlatformVersion.VERSION1_4,
    })

    return [service, securityGroup]
  }

  createBastionHost(vpc: ec2.IVpc): ec2.ISecurityGroup {
    const bastionSecurityGroup = new ec2.SecurityGroup(this, 'BastionSecurityGroup', {
      vpc,
      description: 'Security group for accessing services using bastion host',
    })
    const bastionHost = new ec2.BastionHostLinux(this, 'bastion', {
      vpc,
      securityGroup: bastionSecurityGroup,
    })

    return bastionSecurityGroup
  }
}