#!/usr/bin/env bash
set -o nounset -o errexit -o pipefail
repo="$( cd "$( dirname "$0" )" && pwd )"
source "$repo/scripts/common-functions.sh"

function main {
	parse_env_from_script_name
	configure_aws_credentials "fmi-tiuha-$ENV"
	use_correct_node_version

	"$repo/run-tests.sh"

	export VERSION_TAG="local-$( timestamp )-$( git rev-parse HEAD )"

	export AWS_ACCOUNT_ID="$( get_account_id )"

	cdk_bootstrap
	deploy_cdk_app Repository
	build_and_upload_measurement_api "$VERSION_TAG"
	build_and_upload_titan_container "$VERSION_TAG"
	deploy_cdk_app Tiuha
}

function deploy_cdk_app {
	pushd "$repo/infra/"
	npm_ci_if_package_lock_has_changed
	npx aws-cdk deploy --app "npx ts-node app.ts" --require-approval never "$@"
	popd
}

function diff_cdk_app {
	pushd "$repo/infra/"
	npm_ci_if_package_lock_has_changed
	npx aws-cdk diff --app "npx ts-node app.ts" --require-approval never "$@"
	popd
}

function cdk_bootstrap {
	pushd "$repo/infra/"
	npm_ci_if_package_lock_has_changed
	npx aws-cdk bootstrap "aws://$AWS_ACCOUNT_ID/$AWS_REGION"
	popd
}

function get_account_id {
  aws sts get-caller-identity --query "Account" --output text
}

function build_and_upload_measurement_api {
	local -r tag="$1"

	pushd "$repo/measurement-api"
	mvn package -DskipTests
	build_and_upload_container_image "$tag" "measurement-api"
	popd
}

function build_and_upload_titan_container {
	local -r tag="$1"

	pushd "$repo/qc"
	build_and_upload_container_image "$tag" "titan-qc"
	popd
}

function build_and_upload_container_image {
	local -r tag="$1"
	local -r repository_name="$2"
	local -r repository_uri="$( get_repository_uri "$repository_name" )"

	docker build --tag "${repository_uri}:${tag}" .
	aws ecr get-login-password --region "$AWS_REGION" \
		| docker login --username AWS --password-stdin "$repository_uri"
	docker push "${repository_uri}:${tag}"
}

function get_repository_uri {
	local -r repository_name="$1"
	aws ecr describe-repositories --query "repositories[?repositoryName=='${repository_name}'].repositoryUri | [0]" --output text
}

function timestamp {
	date +"%Y%m%d%H%M%S"
}

main "$@"
