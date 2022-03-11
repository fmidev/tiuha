#!/usr/bin/env bash
set -o nounset -o errexit -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/scripts/common-functions.sh"

function main {
  cd "$repo/measurement-api/"

  DATABASE_HOST="localhost" \
  DATABASE_PORT="5445" \
  DATABASE_NAME="tiuha-test" \
  DATABASE_USERNAME="tiuha-test" \
  DATABASE_PASSWORD="tiuha-test" \
  GEOMESA_DB_PASSWORD="geomesa" \
  IMPORT_BUCKET="fmi-tiuha-import-test" \
  MEASUREMENTS_BUCKET="fmi-tiuha-measurements-test" \
  TITAN_TASK_SUBNET="titan-subnet" \
  TITAN_TASK_DEFINITION_ARN="arn:titantask" \
  TITAN_CLUSTER_ARN="arn:titancluster" \
  ENV=local \
  mvn clean test "$@"
}

main "$@"
