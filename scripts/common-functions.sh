readonly repo="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd .. && pwd )"

function configure_aws_credentials {
  export AWS_PROFILE="$1"
  export AWS_REGION="eu-west-1"
  export AWS_DEFAULT_REGION="${AWS_REGION}"

  info "Using AWS_PROFILE: $AWS_PROFILE"
  aws sts get-caller-identity || fatal "Failed to use AWS_PROFILE $AWS_PROFILE"
}

function aws {
  docker run \
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
    --volume "$( pwd ):/aws" \
    --rm -i amazon/aws-cli:2.2.35 "$@"
}

function info {
  log "INFO" "$1"
}

function fatal {
  log "ERROR" "$1"
  exit 1
}

function log {
  local -r level="$1"
  local -r message="$2"
  local -r timestamp="$( date +"%Y-%m-%d %H:%M:%S" )"

  >&2 echo -e "${timestamp} ${level} ${message}"
}
