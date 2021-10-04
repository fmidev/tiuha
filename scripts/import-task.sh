#!/usr/bin/env bash
set -o nounset -o errexit -o pipefail
repo="$( cd "$( dirname "$0" )" && pwd )"
source "$repo/common-functions.sh"


function get_resource_id {
  local LOGICAL_RESOURCE_ID="$1"
  local STACK_NAME="Tiuha"
  aws cloudformation describe-stack-resource \
    --stack-name $STACK_NAME \
    --logical-resource-id $LOGICAL_RESOURCE_ID \
    --query "StackResourceDetail.PhysicalResourceId" \
    --output text
}

function main {
  if [[ $# -ne 1 ]]; then
    echo "Specify file to import"
    exit 1
  fi

  parse_env_from_script_name
  configure_aws_credentials "fmi-tiuha-$ENV"

  local MEMORY=4096
  local CPU=2048
  local TASK_DEFINITION=$(get_resource_id "MeasurementApiTaskA563DF9D")
  local CLUSTER=$(get_resource_id "ClusterEB0386A7")
  local SUBNET_NAME=$(get_resource_id "ClusterVpcPrivateSubnet1SubnetA4EB481A")
  local FILE_TO_IMPORT="$1"

  aws ecs run-task \
    --task-definition $TASK_DEFINITION \
    --cluster $CLUSTER \
    --network-configuration "awsvpcConfiguration={subnets=[$SUBNET_NAME],assignPublicIp=DISABLED}" \
    --launch-type FARGATE \
    --overrides "{ \"memory\": \"$MEMORY\", \"cpu\": \"$CPU\", \"containerOverrides\": [{ \"name\": \"MeasurementApiContainer\", \"command\": [\"java\", \"-Xmx3500M\", \"-Xms3500M\", \"-jar\", \"/app/server.jar\", \"--import\", \"${FILE_TO_IMPORT}\"] }] }"
}

main "$@"
