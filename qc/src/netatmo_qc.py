import titanlib
import numpy

# isolation check:

isolation_check_radius = 15000
isolation_check_num_min = 5

def getCoordinatesIndex(feature, idx):
    try:
        return feature["geometry"]["coordinates"][idx]
    except IndexError:
        return float('nan')

def getElev(feature):
    return getCoordinatesIndex(feature, 2)

def getLon(feature):
    return getCoordinatesIndex(feature, 1)

def getLat(feature):
    return getCoordinatesIndex(feature, 0)

def getTitanlibParams(features):
    lats = list(map(getLat, features))
    lons = list(map(getLon, features))
    elevs = list(map(getElev, features))
    values = list(map(lambda f: f["properties"]["result"], features))

    points = titanlib.Points(lats, lons, elevs)
    return [points, values]

def temperature(features):
    [points, values] = getTitanlibParams(features)
    results = []

    for flag in titanlib.isolation_check(points, isolation_check_num_min, isolation_check_radius):
        results.append([{
            "check": "isolation_check",
            "passed": bool(flag == 0),
            "result": int(flag),
        }])

    # buddy check parameters:
    radius = np.full(points.size(), 10000) # [] if different radius for each check
    buddy_check_num_min = np.full(points.size(), 5)
    threshold = 2
    max_elev_diff = 200
    elev_gradient = -0.0065
    min_std = 1
    num_iterations = 2

    for i, flag in enumerate(titanlib.buddy_check(points, values, radius, buddy_check_num_min, threshold, max_elev_diff, elev_gradient, min_std, num_iterations)):
        results[i].append({
            "check": "buddy_check",
            "passed": bool(flag == 0),
            "result": int(flag),
        })
    return results

def humidity(features):
    [points, values] = getTitanlibParams(features)
    results = []
    for flag in titanlib.isolation_check(points, isolation_check_num_min, isolation_check_radius):
        results.append([{
            "check": "isolation_check",
            "passed": bool(flag == 0),
            "result": int(flag),
        }])
    return results

def airpressure(features):
    [points, values] = getTitanlibParams(features)
    results = []
    for flag in titanlib.isolation_check(points, isolation_check_num_min, isolation_check_radius):
        results.append([{
            "check": "isolation_check",
            "passed": bool(flag == 0),
            "result": int(flag),
        }])
    return results

