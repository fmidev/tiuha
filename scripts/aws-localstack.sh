#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/common-functions.sh"

function aws-local {
  export AWS_ACCESS_KEY_ID="accesskey"
  export AWS_SECRET_ACCESS_KEY="secretkey"
  aws --endpoint-url="http://localhost:4566" "$@"
}

function main {
  require_command docker
  aws-local "$@"
}

main "$@"
