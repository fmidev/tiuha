#!/usr/bin/env bash
set -o nounset -o errexit -o pipefail
repo="$( cd "$( dirname "$0" )" && pwd )"
source "$repo/common-functions.sh"

IMAGE_TAG="aws-cli-ssm:local"

function build_session_manager_cli_image {
  info "Building tunnel image"
  cd "$repo/scripts/db-tunnel-docker"
  docker build --quiet --tag $IMAGE_TAG .
}

function is_container_healthy {
  local container_id="$1"
  local status="$(docker inspect --format='{{.State.Health.Status}}' $container_id)"
  if [[ "$status" == "healthy" ]]; then
    return 0
  else
    return 1
  fi
}

function run_tunnel_container {
  local container_id=$(docker run \
      --env AWS_PROFILE \
      --env AWS_REGION \
      --env AWS_DEFAULT_REGION \
      --env AWS_CONTAINER_CREDENTIALS_RELATIVE_URI \
      --env AWS_ACCESS_KEY_ID \
      --env AWS_SECRET_ACCESS_KEY \
      --env AWS_SESSION_TOKEN \
      --env AWS_CONFIG_FILE=/aws_config \
      --volume "$repo/aws_config:/aws_config" \
      --volume "${HOME}/.aws:/root/.aws" \
      --privileged \
      --publish "127.0.0.1:1111:1111" \
      --detach \
      --rm $IMAGE_TAG
    )

  trap "info 'Killing tunnel container' ; docker kill $container_id" EXIT

  info "Waiting until $container_id is healthy"
  while ! is_container_healthy $container_id; do
    sleep 1
  done
}

function start_tunnel_to_rds {
  info "Starting tunnel to RDS database cluster"
  run_tunnel_container
  info "Tunnel started, DB listening on port 1111"
}

function start_psql {
  info "Connecting to localhost:1111"
  local pw="$(aws secretsmanager get-secret-value --secret-id 'tiuha-database-credentials' --query 'SecretString' --output text | jq -r '.password')"

  PSQLRC="$repo/scripts/psqlrc" \
  PGPASSWORD=$pw psql "postgresql://tiuha@localhost:1111/tiuha?ssl=true"
}

function main {
  require_command psql
  require_command jq
  require_command docker
  parse_env_from_script_name
  configure_aws_credentials "fmi-tiuha-$ENV"

  cd "$repo"
  build_session_manager_cli_image
  start_tunnel_to_rds
  start_psql
}

main "$@"
