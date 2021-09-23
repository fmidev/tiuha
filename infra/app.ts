#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from '@aws-cdk/core';
import { TiuhaStack } from './TiuhaStack';
import { RepositoryStack } from './RepositoryStack'


const app = new cdk.App();
const { repository } = new RepositoryStack(app, 'Repository', {
  env: {
    region: 'eu-west-1',
  }
})
new TiuhaStack(app, 'Tiuha', {
  env: {
    region: 'eu-west-1',
  },
  repository,
  versionTag: requireEnv("VERSION_TAG"),
});

app.synth()

function requireEnv(key: string): string {
  const value = process.env[key]
  if (!value) {
    throw new Error(`Environment variable ${key} required`)
  }
  return value
}