#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from '@aws-cdk/core';
import { GeoMesaStack } from './GeoMesaStack';

const app = new cdk.App();
new GeoMesaStack(app, 'Tiuha', {
  env: {
    region: 'eu-west-1',
  }
});

app.synth()
