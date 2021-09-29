import * as cdk from '@aws-cdk/core'
import * as cassandra from '@aws-cdk/aws-cassandra'
import * as iam from '@aws-cdk/aws-iam'
import * as ecr from '@aws-cdk/aws-ecr'
import * as ecs from '@aws-cdk/aws-ecs'
import * as logs from '@aws-cdk/aws-logs'
import * as s3 from '@aws-cdk/aws-s3'

type TiuhaStackProps = cdk.StackProps & {
  envName: string
  repository: ecr.IRepository
  versionTag: string
}

export class TiuhaStack extends cdk.Stack {
  constructor(scope: cdk.Construct, id: string, props: TiuhaStackProps) {
    super(scope, id, props)

    const measurementsKeyspace = new cassandra.CfnKeyspace(this, 'Measurements', {
      keyspaceName: 'measurements',
    })

    measurementsKeyspace.applyRemovalPolicy(cdk.RemovalPolicy.RETAIN)

    const envName = props.envName.toLowerCase()
    const measurementsBucket = new s3.Bucket(this, 'measurementsBucket', {
      bucketName: `fmi-tiuha-measurements-${envName}`,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
    })

    const cluster = new ecs.Cluster(this, 'Cluster', {})
    const image = ecs.ContainerImage.fromEcrRepository(props.repository, props.versionTag)
    this.createFargateService(cluster, image, measurementsBucket)
  }

  createFargateService(cluster: ecs.ICluster, containerImage: ecs.ContainerImage, measurementsBucket: s3.Bucket) {
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
      })
    })

    taskDefinition.addToTaskRolePolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: ['cassandra:*'],
      resources: ['*'],
    }))

    measurementsBucket.grantReadWrite(taskDefinition.taskRole)

    taskDefinition.addToTaskRolePolicy(new iam.PolicyStatement({
      effect: iam.Effect.ALLOW,
      actions: ['secretsmanager:GetSecretValue'],
      resources: ['*']
    }))
  }
}