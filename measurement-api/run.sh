#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/../scripts/common-functions.sh"

function main {
  cd "$repo/measurement-api"
  mvn package
  java -jar "$repo/measurement-api/target/measurement-api-1.0-SNAPSHOT-jar-with-dependencies.jar"
}

main "$@"
