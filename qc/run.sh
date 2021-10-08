#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/../scripts/common-functions.sh"

function main {
  cd "$repo/qc"
  docker build . --tag qc:local
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
    --rm -it qc:local "$@"
}

main "$@"
