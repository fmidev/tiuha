#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/../scripts/common-functions.sh"

function main {
  docker run \
    --rm -it \
    --volume "$( pwd ):/pwd" \
    qc:local /bin/bash
}

main "$@"
