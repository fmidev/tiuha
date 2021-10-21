#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/../scripts/common-functions.sh"

function main {
  cd "$repo/qc"
  require_command docker

  docker build . --tag qc:local
  docker run \
    --attach stdin \
    --attach stdout \
    --attach stderr \
    --rm -i qc:local "$@"
}

main "$@"
