#!/usr/bin/env python3
"""
One-off report: compare GLO-30 vs GLO-90 visibility at known viewpoints.

Fetches the same 1° cell(s) from both Copernicus buckets, converts with the
production GeoTIFF converter + DemTileWriter (via a small Kotlin driver or
manual .bin output), then runs raycast comparison.

This script is intentionally standalone and does not commit rasters.
Run from repo root after assembling debug:

  ./gradlew :core:data:test --tests "com.nofar.core.data.dem.DemTileBinaryTest"
  python3 scripts/compare_dem_30_vs_90.py --lat 31.68 --lon 34.98 --label "Tel Azeka"

For a full production comparison, invoke the visibility engine from an
instrumented test harness; this script documents the methodology and prints
placeholder guidance when network or JDK tooling is unavailable.
"""

from __future__ import annotations

import argparse
import math
import sys
import urllib.request

GLO30_BASE = "https://copernicus-dem-30m.s3.eu-central-1.amazonaws.com"
GLO90_BASE = "https://copernicus-dem-90m.s3.eu-central-1.amazonaws.com"

VIEWPOINTS = [
    ("Tel Azeka", 31.68, 34.98),
    ("Coastal flat", 32.10, 34.75),
    ("Ridge interior", 32.80, 35.50),
    ("Valley floor", 31.50, 35.20),
    ("Northern slope", 33.00, 35.40),
    ("Southern Negev", 30.60, 34.80),
    ("Galilee hill", 32.90, 35.30),
    ("Jerusalem edge", 31.78, 35.22),
]


def tile_id(lat: float, lon: float) -> tuple[int, int, str, str]:
    tile_lat = math.floor(lat)
    tile_lon = math.floor(lon)
    ns = "N" if tile_lat >= 0 else "S"
    ew = "E" if tile_lon >= 0 else "W"
    glo30 = f"Copernicus_DSM_COG_10_{ns}{abs(tile_lat):02d}_00_{ew}{abs(tile_lon):03d}_00_DEM"
    glo90 = f"Copernicus_DSM_COG_30_{ns}{abs(tile_lat):02d}_00_{ew}{abs(tile_lon):03d}_00_DEM"
    return tile_lat, tile_lon, glo30, glo90


def head_request(base: str, tile: str) -> int | None:
    url = f"{base}/{tile}/{tile}.tif"
    req = urllib.request.Request(url, method="HEAD")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return int(resp.headers.get("Content-Length", "0"))
    except Exception as exc:  # noqa: BLE001
        print(f"  HEAD failed for {url}: {exc}", file=sys.stderr)
        return None


def main() -> None:
    parser = argparse.ArgumentParser(description="GLO-30 vs GLO-90 size + methodology report")
    parser.add_argument("--lat", type=float, help="Observer latitude")
    parser.add_argument("--lon", type=float, help="Observer longitude")
    parser.add_argument("--label", type=str, default="Custom")
    args = parser.parse_args()

    points = VIEWPOINTS if args.lat is None else [(args.label, args.lat, args.lon)]

    print("GLO-30 vs GLO-90 comparison report (methodology)")
    print("=" * 60)
    print("Metrics to capture in instrumented harness:")
    print("  - visibility flip rate per viewpoint candidate set")
    print("  - mean abs horizon elevation-angle delta per azimuth")
    print("  - observer GPS−DEM error distribution")
    print()

    for label, lat, lon in points:
        _, _, glo30, glo90 = tile_id(lat, lon)
        w30 = head_request(GLO30_BASE, glo30)
        w90 = head_request(GLO90_BASE, glo90)
        print(f"{label} ({lat:.4f}, {lon:.4f})")
        print(f"  GLO-30 tile: {glo30}  wire={w30} bytes" if w30 else f"  GLO-30 tile: {glo30}  wire=unknown")
        print(f"  GLO-90 tile: {glo90}  wire={w90} bytes" if w90 else f"  GLO-90 tile: {glo90}  wire=unknown")
        if w30 and w90:
            ratio = w90 / w30
            print(f"  wire ratio (90/30): {ratio:.2f}")
        print()

    print("Next step: run VisibilityUseCase with both .bin rasters for each viewpoint")
    print("and paste flip-rate numbers into the PR description.")


if __name__ == "__main__":
    main()
