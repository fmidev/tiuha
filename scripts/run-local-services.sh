#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )/common-functions.sh"

function main {
  cd "$repo/local-env"

  docker-compose down --remove-orphans || true
  docker-compose up --build --force-recreate
}

main "$@"
