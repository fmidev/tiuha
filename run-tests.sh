#!/usr/bin/env bash
set -o nounset -o errexit -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/scripts/common-functions.sh"

function main {
  cd "$repo/measurement-api/"

  DATABASE_HOST="localhost" \
  DATABASE_PORT="5444" \
  DATABASE_NAME="tiuha" \
  DATABASE_USERNAME="tiuha" \
  DATABASE_PASSWORD="tiuha" \
  GEOMESA_DB_PASSWORD="geomesa" \
  IMPORT_BUCKET="fmi-tiuha-import-local" \
  TITAN_TASK_SUBNET="titan-subnet" \
  TITAN_TASK_DEFINITION_ARN="arn:titantask" \
  TITAN_CLUSTER_ARN="arn:titancluster" \
  ENV=local \
  mvn clean test "$@"
}

main "$@"
