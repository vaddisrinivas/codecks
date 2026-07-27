#!/usr/bin/env python3
"""Verify the arithmetic behind the README desk-value evidence."""

import csv
import math
from pathlib import Path


CSV_PATH = Path(__file__).parents[1] / "docs/marketing/desk_value_snapshot.csv"


with CSV_PATH.open(newline="") as handle:
    rows = list(csv.DictReader(handle))


def row_for(metric):
    matches = [row for row in rows if row["metric"] == metric]
    assert len(matches) == 1, (metric, len(matches))
    return matches[0]


mouse_asp = float(row_for("mouse_asp")["value"])
pad_range = row_for("standard_pad_asp_range")
pad_low = float(pad_range["lower"])
pad_high = float(pad_range["upper"])

assert math.isclose(mouse_asp, 38.0)
assert math.isclose(mouse_asp + pad_low, 43.0)
assert math.isclose(mouse_asp + pad_high, 53.0)

pad_areas = sorted(
    float(row["value"]) for row in rows if row["metric"] == "pad_area"
)
work_surfaces = sorted(
    float(row["value"]) for row in rows if row["metric"] == "work_surface_area"
)
assert pad_areas == [153.46, 278.74]
assert work_surfaces == [1152.0, 1800.0]

shares = [
    100 * pad_area / work_surface
    for pad_area in pad_areas
    for work_surface in work_surfaces
]
assert math.isclose(min(shares), 8.5256, abs_tol=0.0001)
assert math.isclose(max(shares), 24.1962, abs_tol=0.0001)

workspace_rows = [row for row in rows if row["category"] == "workspace"]
assert len(workspace_rows) == 4
workspace_total = sum(float(row["value"]) for row in workspace_rows)
without_own_office = workspace_total - float(row_for("own_office")["value"])
assert math.isclose(workspace_total, 100.0, abs_tol=0.01)
assert math.isclose(without_own_office, 58.9, abs_tol=0.01)

print("desk value evidence OK")
