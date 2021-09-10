#!/usr/bin/env bash
set -o nounset -o errexit -o pipefail
repo="$( cd "$( dirname "$0" )" && pwd )"
source "$repo/scripts/common-functions.sh"

function deploy_cdk_app {
	cd "$repo/infra/"
	npm_ci_if_package_lock_has_changed
	npx aws-cdk deploy --app "npx ts-node app.ts"
}

function main {
	parse_env_from_script_name
	configure_aws_credentials "fmi-tiuha-$ENV"
	use_correct_node_version

	deploy_cdk_app
}

main "$@"