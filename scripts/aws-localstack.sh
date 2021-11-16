#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/common-functions.sh"

function aws-local {
  export AWS_ACCESS_KEY_ID="accesskey"
  export AWS_SECRET_ACCESS_KEY="secretkey"
  docker run \
    --env AWS_ACCESS_KEY_ID \
    --env AWS_SECRET_ACCESS_KEY \
    --network host \
    --rm -i amazon/aws-cli:2.2.35 --endpoint-url="http://localhost:4566" "$@"
}

function main {
  require_command docker
  aws-local "$@"
}

main "$@"
