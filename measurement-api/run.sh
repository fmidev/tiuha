#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/../scripts/common-functions.sh"

function main {
  cd "$repo/measurement-api"
  mvn clean package
  configure_aws_credentials "$AWS_PROFILE"

  DATABASE_HOST="localhost" \
  DATABASE_PORT="5444" \
  DATABASE_NAME="tiuha" \
  DATABASE_USERNAME="tiuha" \
  DATABASE_PASSWORD="tiuha" \
  IMPORT_BUCKET="fmi-tiuha-import-local" \
  java -jar \
    "$repo/measurement-api/target/measurement-api-1.0-SNAPSHOT-jar-with-dependencies.jar" \
    "$@"
}

main "$@"
