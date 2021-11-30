#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/../scripts/common-functions.sh"

function main {
  cd "$repo/measurement-api"
  mvn package -DskipTests

  DATABASE_HOST="localhost" \
  DATABASE_PORT="5444" \
  DATABASE_NAME="tiuha" \
  DATABASE_USERNAME="tiuha" \
  DATABASE_PASSWORD="tiuha" \
  GEOMESA_DB_PASSWORD="geomesa" \
  IMPORT_BUCKET="fmi-tiuha-import-local" \
  MEASUREMENTS_BUCKET="fmi-tiuha-measurements-local" \
  ENV="local" \
  AWS_ACCESS_KEY_ID="access_key" \
  AWS_SECRET_ACCESS_KEY="secret_key" \
  java -jar \
    "$repo/measurement-api/target/measurement-api-1.0-SNAPSHOT-shaded.jar" \
    "$@"
}

main "$@"
