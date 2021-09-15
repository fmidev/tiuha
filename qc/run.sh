#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/../scripts/common-functions.sh"

function main {
  cd "$repo/qc"
  docker build . --tag qc:local
  docker run --rm -it qc:local
}

main "$@"
