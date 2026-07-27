#!/usr/bin/env python3
"""Verify the arithmetic behind the README desk-value snapshot."""

import csv
import math
from pathlib import Path


CSV_PATH = Path(__file__).parents[1] / "docs/marketing/desk_value_snapshot.csv"


def mean(values):
    values = list(values)
    return sum(values) / len(values)


with CSV_PATH.open(newline="") as handle:
    rows = list(csv.DictReader(handle))

mice = [float(row["price_usd"]) for row in rows if row["category"] == "mouse"]
pads = [float(row["price_usd"]) for row in rows if row["category"] == "mousepad"]
desks = [row for row in rows if row["category"] == "desk"]
sizes = [row for row in rows if row["category"] == "mousepad_size"]

assert len(mice) == 18
assert len(pads) == 2
assert len(desks) == 3
assert len(sizes) == 2
assert math.isclose(mean(mice), 65.55, abs_tol=0.005)
assert math.isclose(mean(pads), 17.50, abs_tol=0.005)
assert math.isclose(mean(mice) + mean(pads), 83.05, abs_tol=0.005)

desk_area = mean(float(row["area_in2"]) for row in desks)
desk_price = mean(float(row["price_usd"]) for row in desks)
assert math.isclose(desk_area, 846.40, abs_tol=0.01)
assert math.isclose(desk_price / desk_area, 0.1536, abs_tol=0.0001)

for row in sizes:
    assert math.isclose(
        float(row["width_in"]) * float(row["depth_in"]),
        float(row["area_in2"]),
        abs_tol=0.01,
    )

print("desk value snapshot OK")
