import titanlib

isolation_check_radius = 15000
isolation_check_num_min = 5

def check(lats, lons, elevs, values):
  points = titanlib.Points(lats, lons, elevs)
  results = []
  for flag in titanlib.isolation_check(points, isolation_check_num_min, isolation_check_radius):
    results.append([{
      "check": "isolation_check",
      "passed": bool(flag == 0),
      "result": int(flag),
    }])
  return results