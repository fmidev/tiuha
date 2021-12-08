import * as cdk from '@aws-cdk/core'
import { Tags } from '@aws-cdk/core'
import * as cassandra from '@aws-cdk/aws-cassandra'
import * as iam from '@aws-cdk/aws-iam'
import * as ec2 from '@aws-cdk/aws-ec2'
import * as ecr from '@aws-cdk/aws-ecr'
import * as ecs from '@aws-cdk/aws-ecs'
import * as logs from '@aws-cdk/aws-logs'
import * as s3 from '@aws-cdk/aws-s3'
import * as secretsmanager from '@aws-cdk/aws-secretsmanager'
import * as rds from '@aws-cdk/aws-rds'
import * as elb from '@aws-cdk/aws-elasticloadbalancingv2'
import * as route53 from '@aws-cdk/aws-route53'
import * as certificatemanager from '@aws-cdk/aws-certificatemanager'

type TiuhaStackProps = cdk.StackProps & {
  envName: string
  measurementApiRepository: ecr.IRepository
  titanQCRepository: ecr.IRepository
  versionTag: string
}

export class TiuhaStack extends cdk.Stack {
  apiPortNumber = 8383
  envName: string
  vpc: ec2.Vpc
  importBucket: s3.Bucket
  hostedZone: route53.HostedZone

  constructor(scope: cdk.Construct, id: string, props: TiuhaStackProps) {
    super(scope, id, props)
    this.envName = props.envName.toLowerCase()

    this.hostedZone = new route53.HostedZone(this, "TiuhaHostedZone", {
      zoneName: this.envName === "prod" ? "tiuha.fmi.fi" : `tiuha-${this.envName}.fmi.fi`
    })

    const measurementsKeyspace = new cassandra.CfnKeyspace(this, 'Measurements', {
      keyspaceName: 'measurements',
    })

    measurementsKeyspace.applyRemovalPolicy(cdk.RemovalPolicy.RETAIN)

    this.vpc = this.createVpc()

    const measurementsBucket = new s3.Bucket(this, 'measurementsBucket', {
      bucketName: `fmi-tiuha-measurements-${this.envName}`,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
    })

    this.importBucket = new s3.Bucket(this, 'ImportBucket', {
      bucketName: `fmi-tiuha-import-${this.envName}`,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
    })

    const { taskDefinition: titanTaskDefinition } = this.createTitanlibTask(props.titanQCRepository, props.versionTag)

    const dbCredentials = rds.Credentials.fromGeneratedSecret('tiuha', {
      secretName: 'tiuha-database-credentials'
    })
    const [db, dbSG] = this.createDatabase(this.vpc, dbCredentials)

    const [service, serviceSG, apiPort] = this.createFargateService(
      this.vpc, props.measurementApiRepository, props.versionTag, db, measurementsBucket, titanTaskDefinition
    )

    const bastionSecurityGroup = this.createBastionHost(this.vpc)
    serviceSG.addIngressRule(bastionSecurityGroup, apiPort)

    const postgresPort = ec2.Port.tcp(5432)
    dbSG.addIngressRule(bastionSecurityGroup, postgresPort)
    dbSG.addIngressRule(serviceSG, postgresPort)

    this.createPublicLoadBalancer(service)
  }

  createPublicLoadBalancer(service: ecs.FargateService) {
    const domainName = `${this.envName}-tiuha.com`

    const loadBalancer = new elb.ApplicationLoadBalancer(this, "LoadBalancer", {
      vpc: service.cluster.vpc,
      internetFacing: true,
    })

    // only dev has own domain for demo purposes
    if (this.envName === "dev") {
      const tempHostedZone = route53.HostedZone.fromHostedZoneAttributes(this, "HostedZone", {
        hostedZoneId: "Z10485242JB98Z7I2HV6U",
        zoneName: domainName,
      })
      const apiDomainName = `api.${domainName}`

      const certificate = new certificatemanager.DnsValidatedCertificate(this, "Certificate", {
        hostedZone: tempHostedZone,
        domainName: apiDomainName,
      })

      const listener = loadBalancer.addListener("PublicListener", {
        protocol: elb.ApplicationProtocol.HTTPS,
        certificates: [certificate],
        open: true,
      })

      const targetGroup = listener.addTargets("FargateService", {
        port: this.apiPortNumber,
        protocol: elb.ApplicationProtocol.HTTP,
        targets: [service],
        healthCheck: {
          interval: cdk.Duration.seconds(30),
          protocol: elb.Protocol.HTTP,
          path: "/healthcheck",
          healthyHttpCodes: "200",
          port: this.apiPortNumber.toString(),
          timeout: cdk.Duration.seconds(10),
          healthyThresholdCount: 2,
          unhealthyThresholdCount: 2,
        }
      })

      new route53.CnameRecord(this, "LoadBalancerDnsRecord", {
        zone: tempHostedZone,
        recordName: apiDomainName,
        domainName: loadBalancer.loadBalancerDnsName,
      })

      new route53.CnameRecord(this, "LoadBalancerTiuhaDnsRecord", {
        zone: this.hostedZone,
        recordName: apiDomainName,
        domainName: loadBalancer.loadBalancerDnsName,
      })
    }
  }

  createVpc(): ec2.Vpc {
    const vpc = new ec2.Vpc(this, "DefaultVpc", {})

    vpc.addGatewayEndpoint("S3", {
      service: ec2.GatewayVpcEndpointAwsService.S3
    })

    return vpc
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

  createTitanlibTask(titanQCRepository: ecr.IRepository, versionTag: string): { taskDefinition: ecs.ITaskDefinition } {
    const image = ecs.ContainerImage.fromEcrRepository(titanQCRepository, versionTag)

    const logGroup = new logs.LogGroup(this, 'QCLogGroup', {
      logGroupName: 'qc',
      retention: logs.RetentionDays.INFINITE,
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    })

    const taskDefinition = new ecs.FargateTaskDefinition(this, 'TitanlibTask', {
      cpu: 512,
      memoryLimitMiB: 2048,
    })
    taskDefinition.addContainer('TitanlibContainer', {
      image,
      logging: new ecs.AwsLogDriver({
        logGroup,
        streamPrefix: 'titan'
      }),
      environment: {
        VERSION: versionTag
      }
    })

    this.importBucket.grantReadWrite(taskDefinition.taskRole)
    Tags.of(taskDefinition).add('qc_process', 'titan')

    return { taskDefinition }
  }


  createFargateService(
    vpc: ec2.IVpc,
    measurementApiRepository: ecr.IRepository,
    versionTag: string,
    db: rds.DatabaseCluster,
    measurementsBucket: s3.Bucket,
    titanTaskDefinition: ecs.ITaskDefinition,
  ): [ecs.FargateService, ec2.SecurityGroup, ec2.Port] {
    const cluster = new ecs.Cluster(this, 'Cluster', { vpc })
    const apiImage = ecs.ContainerImage.fromEcrRepository(measurementApiRepository, versionTag)

    const geomesaDbPassword = new secretsmanager.Secret(this, 'GeomesaDbPassword')

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
      image: apiImage,
      logging: new ecs.AwsLogDriver({
        logGroup,
        streamPrefix: 'measurement-api',
      }),
      portMappings: [{
        protocol: ecs.Protocol.TCP,
        containerPort: this.apiPortNumber,
      }],
      environment: {
        ENV: this.envName,
        IMPORT_BUCKET: this.importBucket.bucketName,
        MEASUREMENTS_BUCKET: measurementsBucket.bucketName,
        TITAN_TASK_DEFINITION_ARN: titanTaskDefinition.taskDefinitionArn,
        TITAN_CLUSTER_ARN: cluster.clusterArn, // QC tasks run in the same cluster as the API for now, but it could have its own cluster
        TITAN_TASK_SUBNET: vpc.privateSubnets[0].subnetId,
      },
      secrets: {
        DATABASE_NAME: ecs.Secret.fromSecretsManager(db.secret!, 'dbname'),
        DATABASE_HOST: ecs.Secret.fromSecretsManager(db.secret!, 'host'),
        DATABASE_PORT: ecs.Secret.fromSecretsManager(db.secret!, 'port'),
        DATABASE_USERNAME: ecs.Secret.fromSecretsManager(db.secret!, 'username'),
        DATABASE_PASSWORD: ecs.Secret.fromSecretsManager(db.secret!, 'password'),
        GEOMESA_DB_PASSWORD: ecs.Secret.fromSecretsManager(geomesaDbPassword),
      }
    })
    geomesaDbPassword.grantRead(taskDefinition.taskRole)

    taskDefinition.addToTaskRolePolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: ['cassandra:*'],
      resources: ['*'],
    }))

    taskDefinition.addToTaskRolePolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: ['ecs:RunTask'],
      resources: [titanTaskDefinition.taskDefinitionArn]
    }))

    titanTaskDefinition.executionRole?.grantPassRole(taskDefinition.taskRole)
    titanTaskDefinition.taskRole?.grantPassRole(taskDefinition.taskRole)

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

    return [service, securityGroup, ec2.Port.tcp(this.apiPortNumber)]
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