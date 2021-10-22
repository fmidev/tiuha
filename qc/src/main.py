import argparse
import boto3
import gzip
import json
import numbers
import sys
import temperature

class FeatureCollection:
    def __init__(self, collection):
        self.collection = collection
        self.features = collection["features"]
        self.init_data_vectors()

    def init_data_vectors(self):
        self.lats = []
        self.lons = []
        self.elevs = []
        self.values = []

        for feature in self.features:
            lon, lat, elev = feature["geometry"]["coordinates"]
            self.lats.append(lat)
            self.lons.append(lon)
            self.elevs.append(elev)
            self.values.append(feature["properties"]["result"])


def check_features(input_str):
    featureCollection = FeatureCollection(json.JSONDecoder().decode(input_str))
    flags = temperature.check(featureCollection.lats, featureCollection.lons, featureCollection.elevs, featureCollection.values)

    for feature, flag in zip(featureCollection.features, flags):
        feature["properties"]["qcFlag"] = flag.item()

    return json.JSONEncoder().encode(featureCollection.collection)

def read_input_from_s3(bucket, input_key):
    s3 = boto3.resource('s3')
    input_obj = s3.Object(bucket, input_key)
    content = input_obj.get()['Body'].read()
    if input_key.endswith('.gz'):
        content = gzip.decompress(content)
    return content.decode('utf-8')

def read_input_from_stdin():
    with sys.stdin as input_file:
        return input_file.read()

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

    if args.bucket and (args.inputKey == args.outputKey):
        print('Input and output key are the same, input would get overwritten', file=sys.stderr)
        return 1
    
    bucket_missing = (args.inputKey or args.outputKey) and not args.bucket
    input_or_output_missing = args.bucket and (not args.inputKey or not args.outputKey)
    if bucket_missing or input_or_output_missing:
        print('To read/write from/to S3, please specify --bucket and --inputKey, --outputKey or both', file=sys.stderr)
        return 2

    if args.bucket and args.inputKey:
        input_str = read_input_from_s3(args.bucket, args.inputKey)
    else:
        input_str = read_input_from_stdin()

    output_str = check_features(input_str)

    if args.bucket and args.outputKey:
        write_output_to_s3(output_str, args.bucket, args.outputKey)
    else:
        write_output_to_stdout(output_str)
if __name__ == "__main__":
    main()
