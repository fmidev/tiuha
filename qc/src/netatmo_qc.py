import titanlib

isolation_check_radius = 15000
isolation_check_num_min = 5


def temperature(features):
    lats = list(map(lambda f: f["geometry"]["coordinates"][0], features))
    lons = list(map(lambda f: f["geometry"]["coordinates"][1], features))
    elevs = list(map(lambda f: f["geometry"]["coordinates"][2], features))
    values = list(map(lambda f: f["properties"]["result"], features))

    points = titanlib.Points(lats, lons, elevs)
    results = []
    for flag in titanlib.isolation_check(points, isolation_check_num_min, isolation_check_radius):
        results.append([{
            "check": "isolation_check",
            "passed": bool(flag == 0),
            "result": int(flag),
        }])
    return results

def humidity(features):
    lats = list(map(lambda f: f["geometry"]["coordinates"][0], features))
    lons = list(map(lambda f: f["geometry"]["coordinates"][1], features))
    elevs = list(map(lambda f: f["geometry"]["coordinates"][2], features))
    values = list(map(lambda f: f["properties"]["result"], features))

    points = titanlib.Points(lats, lons, elevs)
    results = []
    for flag in titanlib.isolation_check(points, isolation_check_num_min, isolation_check_radius):
        results.append([{
            "check": "isolation_check",
            "passed": bool(flag == 0),
            "result": int(flag),
        }])
    return results

def airpressure(features):
    lats = list(map(lambda f: f["geometry"]["coordinates"][0], features))
    lons = list(map(lambda f: f["geometry"]["coordinates"][1], features))
    elevs = list(map(lambda f: f["geometry"]["coordinates"][2], features))
    values = list(map(lambda f: f["properties"]["result"], features))

    points = titanlib.Points(lats, lons, elevs)
    results = []
    for flag in titanlib.isolation_check(points, isolation_check_num_min, isolation_check_radius):
        results.append([{
            "check": "isolation_check",
            "passed": bool(flag == 0),
            "result": int(flag),
        }])
    return results
