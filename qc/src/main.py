import titanlib
import numpy as np

points = titanlib.Points(
    [60, 60.1, 60.2],
    [10, 10, 10],
    [0, 0, 0]
)
values = [0, 1, -111]
radius = [50000]
num_min = [2]
threshold = 2
max_elev_diff = 200
elev_gradient = 0
min_std = 1
num_iterations = 2

def main():
    print("Hello, world")

    flags = titanlib.buddy_check(points, values, radius, num_min,threshold, max_elev_diff, elev_gradient, min_std, num_iterations)
    print(flags)

if __name__ == "__main__":
    main()
