import * as cdk from '@aws-cdk/core'
import * as ecr from '@aws-cdk/aws-ecr'

export class RepositoryStack extends cdk.Stack {
  repository: ecr.IRepository

  constructor(scope: cdk.Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props)

    this.repository = new ecr.Repository(this, 'MeasurementApiRepository', {
      repositoryName: 'measurement-api'
    })
  }
}
