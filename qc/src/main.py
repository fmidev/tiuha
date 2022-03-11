from itertools import groupby
import argparse
import boto3
import gzip
import json
import os
import sys

import netatmo_qc

VERSION = os.environ.get("VERSION", None)


class FeatureCollection:
    def __init__(self, collection):
        self.collection = collection
        self.features = collection["features"]
        self.grouped = self.group_by_source_and_property(self.features)

    def group_by_source_and_property(self, features):
        def keyfunc(x):
            source = x[1]["properties"].get("sourceId", "netatmo")
            property = x[1]["properties"]["observedPropertyTitle"]
            return (source, property)

        sorted_by_property = sorted(enumerate(features), key=keyfunc)
        result = {}
        for key, group in groupby(sorted_by_property, key=keyfunc):
            result[key] = list(group)
        return result



def check_features(input_str):
    print("Parsing input GeoJSON")
    featureCollection = FeatureCollection(json.JSONDecoder().decode(input_str))
    print(f"Checking {len(featureCollection.features)} features")
    for key, features_with_index in featureCollection.grouped.items():
        print(f"Processing {len(features_with_index)} features with property {key}")
        indexes = list(map(lambda x: x[0], features_with_index))
        features = list(map(lambda x: x[1], features_with_index))

        if key == ("netatmo", "Air temperature"):
            print(f"Running QC for {key}")
            check_results = netatmo_qc.temperature(features)
            assign_qc_results("titanlib-temperature", featureCollection, indexes, check_results)
        elif key == ("netatmo", "Relative Humidity"):
            print(f"Running QC for {key}")
            check_results = netatmo_qc.humidity(features)
            assign_qc_results("titanlib-humidity", featureCollection, indexes, check_results)
        elif key == ("netatmo", "Air Pressure"):
            print(f"Running QC for {key}")
            check_results = netatmo_qc.airpressure(features)
            assign_qc_results("titanlib-airpressure", featureCollection, indexes, check_results)
        else:
            print(f"No QC available for {key}")

    return json.JSONEncoder().encode(featureCollection.collection)

def assign_qc_results(method, featureCollection, indexes, check_results):
    for idx, flags in zip(indexes, check_results):
        feature = featureCollection.features[idx]
        all_checks_passed = all(f["passed"] for f in flags)
        feature["properties"]["qcPassed"] = all_checks_passed
        feature["properties"]["qcDetails"] = {
            "method": method,
            "version": VERSION,
            "flags": flags,
        }

def read_input_from_s3(bucket, input_key):
    s3 = boto3.resource('s3')
    input_obj = s3.Object(bucket, input_key)
    content = input_obj.get()['Body'].read()
    if input_key.endswith('.gz'):
        content = gzip.decompress(content)
    return content.decode('utf-8')

def read_input_from_stdin():
    return sys.stdin.buffer.read()

def write_output_to_s3(output_str, bucket, output_key):
    s3 = boto3.resource('s3')
    output_obj = s3.Object(bucket, output_key)
    output_body = output_str.encode()
    if output_key.endswith('.gz'):
        output_body = gzip.compress(output_body)
    output_obj.put(Body=output_body)

def write_output_to_stdout(output_str):
    with sys.stdout as output_file:
        output_file.write(output_str)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--bucket')
    parser.add_argument('--inputKey')
    parser.add_argument('--outputKey')
    args = parser.parse_args()
    print("Running with args", args)

    if args.bucket and (args.inputKey == args.outputKey):
        print('Input and output key are the same, input would get overwritten', file=sys.stderr)
        return 1
    
    bucket_missing = (args.inputKey or args.outputKey) and not args.bucket
    input_or_output_missing = args.bucket and not (args.inputKey or args.outputKey)
    if bucket_missing or input_or_output_missing:
        print('To read/write from/to S3, please specify --bucket and --inputKey, --outputKey or both', file=sys.stderr)
        return 2

    if args.bucket and args.inputKey:
        input_str = read_input_from_s3(args.bucket, args.inputKey)
    else:
        input_str = read_input_from_stdin()
        # Try to decompress e.g. if piping from S3
        try:
            input_str = gzip.decompress(input_str)
        except Exception as e:
            pass
        input_str = input_str.decode("utf-8")

    output_str = check_features(input_str)

    if args.bucket and args.outputKey:
        write_output_to_s3(output_str, args.bucket, args.outputKey)
    else:
        write_output_to_stdout(output_str)
if __name__ == "__main__":
    main()
