readonly repo="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && cd .. && pwd )"

NODE_VERSION="16.9.0"

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

function parse_env_from_script_name {
  local FILE_NAME=$(basename "$0")
  if echo ${FILE_NAME}| grep -E -q '.+-.{2,4}\.sh$'; then
    export ENV=$(echo ${FILE_NAME}| sed -E -e 's|.+-(.{2,4})\.sh$|\1|g')
  else
    echo >&2 "Don't call this script directly"
    exit 1
  fi
}

function use_correct_node_version {
  export NVM_DIR="${NVM_DIR:-$HOME/.cache/nvm}"
  source "$repo/scripts/nvm.sh"
  nvm use "$NODE_VERSION" || nvm install "$NODE_VERSION"
}

function npm_ci_if_package_lock_has_changed {
  info "Checking if npm ci needs to be run"
  require_command shasum
  local -r checksum_file=".package-lock.json.checksum"

  function run_npm_ci {
    npm ci
    shasum package-lock.json > "$checksum_file"
  }

  if [ ! -f "$checksum_file" ]; then
    echo "new package-lock.json; running npm ci"
    run_npm_ci
  elif ! shasum --check "$checksum_file"; then
    info "package-lock.json seems to have changed, running npm ci"
    run_npm_ci
  else
    info "package-lock.json doesn't seem to have changed, skipping npm ci"
  fi
}

function require_command {
  if ! command -v "$1" > /dev/null; then
    fatal "I require $1 but it's not installed. Aborting."
  fi
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
