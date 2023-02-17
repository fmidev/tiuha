import { aws_ecr as ecr } from 'aws-cdk-lib'
import * as cdk from 'aws-cdk-lib'
import { Construct } from 'constructs'

export class RepositoryStack extends cdk.Stack {
  measurementApiRepository: ecr.IRepository
  titanQCRepository: ecr.IRepository

  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props)

    this.measurementApiRepository = new ecr.Repository(this, 'MeasurementApiRepository', {
      repositoryName: 'measurement-api'
    })
    this.titanQCRepository = new ecr.Repository(this, 'TitanQCRepository', {
      repositoryName: 'titan-qc'
    })
  }
}
