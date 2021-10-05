#!/usr/bin/env bash
set -o errexit -o nounset -o pipefail
source "$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )/scripts/common-functions.sh"

function main {
  require_command tmux
  require_command docker

  docker ps > /dev/null 2>&1 || { echo >&2 "Running 'docker ps' failed. Is docker daemon running? Aborting."; exit 1; }

  session="tiuha"

  tmux kill-session -t $session || true
  tmux start-server
  tmux new-session -d -s $session

  tmux select-pane -t 0
  tmux send-keys "$repo/scripts/run-database.sh" C-m

  tmux splitw -v
  tmux send-keys "$repo/measurement-api/run.sh" C-m

  tmux select-pane -t 0
  tmux attach-session -t $session
}

main "$@"
