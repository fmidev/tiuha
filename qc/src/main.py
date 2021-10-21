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

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--bucket')
    parser.add_argument('--inputKey')
    parser.add_argument('--outputKey')
    args = parser.parse_args()

    if args.bucket:
        if args.inputKey == args.outputKey:
            print('Input and output key are the same, input would get overwritten', file=sys.stderr)
            return 1

        s3 = boto3.resource('s3')
        input_obj = s3.Object(args.bucket, args.inputKey)
        content = input_obj.get()['Body'].read()
        if args.inputKey.endswith('.gz'):
            content = gzip.decompress(content)
        
        input_str = content.decode('utf-8')
        output_str = check_features(input_str)

        output_obj = s3.Object(args.bucket, args.outputKey)
        output_body = output_str.encode()
        if args.outputKey.endswith('.gz'):
            output_body = gzip.compress(output_body)
        output_obj.put(Body=output_body)

    else:
        with sys.stdin as input_file, sys.stdout as output_file:
            output_str = check_features(input_file.read())
            output_file.write(output_str)

if __name__ == "__main__":
    main()
