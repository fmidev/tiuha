#!/usr/bin/env bash
set -o nounset -o errexit -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/common-functions.sh"

function main {
  local -r app_clas="$1"
  cd "$repo/measurement-api"

  parse_env_from_script_name
  configure_aws_credentials "fmi-tiuha-${ENV}"

  DATABASE_HOST="localhost" \
  DATABASE_PORT="1111" \
  DATABASE_NAME="$( get_secret_value 'tiuha-database-credentials' | jq -r '.dbname' )" \
  DATABASE_USERNAME="$( get_secret_value 'tiuha-database-credentials' | jq -r '.username' )" \
  DATABASE_PASSWORD="$( get_secret_value 'tiuha-database-credentials' | jq -r '.password' )" \
  IMPORT_BUCKET="fmi-tiuha-import-${ENV}" \
  mvn compile exec:java -Dexec.mainClass="$1"
}

main "$@"
