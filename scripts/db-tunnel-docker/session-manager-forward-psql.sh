#!/usr/bin/env bash
set -o nounset -o errexit -o pipefail

function main {
  local instance_id="$(aws ec2 describe-instances --output text \
    --filter Name=tag:Name,Values=BastionHost \
    --query 'Reservations[0].Instances[0].InstanceId')"

  local availability_zone="$(aws ec2 describe-instances --output text \
    --filter Name=tag:Name,Values=BastionHost \
    --query 'Reservations[0].Instances[0].Placement.AvailabilityZone')"

  local cluster_identifier_start="tiuha-databasecluster"
  local postgres_host="$(aws rds describe-db-clusters --output text --query "DBClusters[? starts_with(DBClusterIdentifier, '${cluster_identifier_start}')].Endpoint")"

  ssh-keygen -q -t rsa -f temporary_key -N ''

  aws ec2-instance-connect send-ssh-public-key \
    --instance-id $instance_id \
    --availability-zone $availability_zone \
    --instance-os-user ec2-user \
    --ssh-public-key file://temporary_key.pub

  ssh -i temporary_key \
    -o StrictHostKeyChecking=no \
    -o ProxyCommand="aws ssm start-session --target %h --document-name AWS-StartSSHSession --parameters 'portNumber=%p'" \
    -N -L 0.0.0.0:1111:$postgres_host:5432 \
    "ec2-user@${instance_id}"
}

main "$@"
