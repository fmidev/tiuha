import argparse
import boto3
import json
import numbers
import titanlib
import gzip

radius = [50000]
num_min = [2]
threshold = 2
max_elev_diff = 200
elev_gradient = 0
min_std = 1
num_iterations = 2

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--bucket', required=True)
    parser.add_argument('--prefix', required=True)
    parser.add_argument('--filename', required=True)
    args = parser.parse_args()

    input_filename = 'temp.geojson.gz'
    s3 = boto3.client('s3')
    s3.download_file(args.bucket, args.prefix + args.filename, input_filename)

    output_filename = 'result.geojson.gz'
    with gzip.open(input_filename, mode="rt") as input_file:
        with gzip.open(output_filename, mode="wt") as output_file:
            featureCollection = json.JSONDecoder().decode(input_file.read())
            features = featureCollection["features"]

            lats = []
            lons = []
            elevations = []
            values = []

            for feature in features:
                lon, lat, elev = feature["geometry"]["coordinates"]
                lats.append(lat)
                lons.append(lon)
                elevations.append(elev)
                values.append(feature["properties"]["result"])

            points = titanlib.Points(lats, lons, elevations)
            flags = titanlib.buddy_check(points, values, radius, num_min,threshold, max_elev_diff, elev_gradient, min_std, num_iterations)

            for feature, flag in zip(features, flags):
                feature["properties"]["qcFlag"] = flag.item()

            output_file.write(json.JSONEncoder().encode(featureCollection))

    s3.upload_file(output_filename, args.bucket, args.prefix + "qc-" + args.filename)



if __name__ == "__main__":
    main()
