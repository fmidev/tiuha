import titanlib

isolation_check_radius = 15000
isolation_check_num_min = 5

def check(lats, lons, elevs, values):
  points = titanlib.Points(lats, lons, elevs)
  return titanlib.isolation_check(points, isolation_check_num_min, isolation_check_radius)
