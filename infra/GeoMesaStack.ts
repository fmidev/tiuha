import * as cdk from '@aws-cdk/core'
import * as cassandra from '@aws-cdk/aws-cassandra'

export class GeoMesaStack extends cdk.Stack {
  constructor(scope: cdk.Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props)

    const measurementsKeyspace = new cassandra.CfnKeyspace(this, 'Measurements', {
      keyspaceName: 'measurements',
    })

    measurementsKeyspace.applyRemovalPolicy(cdk.RemovalPolicy.RETAIN)
  }
}
