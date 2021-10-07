import titanlib
import numpy as np
import json
import numbers

radius = [50000]
num_min = [2]
threshold = 2
max_elev_diff = 200
elev_gradient = 0
min_std = 1
num_iterations = 2

def main():
    with open("/netatmo.geojson") as file:
        featureCollection = json.JSONDecoder().decode(file.read())
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

        print(json.JSONEncoder().encode(featureCollection))



if __name__ == "__main__":
    main()
