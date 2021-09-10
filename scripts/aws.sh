#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/common-functions.sh"

function main {
  configure_aws_credentials "$AWS_PROFILE"
  aws "$@"
}

main "$@"
