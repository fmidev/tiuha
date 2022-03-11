#!/usr/bin/env bash
set -o nounset -o errexit -o pipefail
source "$( dirname "${BASH_SOURCE[0]}" )/common-functions.sh"

function main {
  parse_env_from_script_name
  if [[ $# -ne 2 ]]; then
    fatal "Usage: $0 <client_id> <api_key>"
  fi

  local -r client_id="$1"
  local -r api_key="$2"

  "$repo/scripts/run-migration-app-${ENV}.sh" fi.fmi.tiuha.app.CreateApiClientApp "$client_id" "$api_key"
}

main "$@"
