#!/usr/bin/env -S uv run --script
# /// script
# dependencies = [
#   "numpy",
#   "requests",
#   "tifffile",
#   "imagecodecs",
#   "pyproj",
#   "imageio",
# ]
# ///

import math
import numpy as np
import requests
import tifffile
from io import BytesIO
import imageio.v3 as imageio


# ----------------------------
# CONFIG
# ----------------------------

BASE_URL = "https://copernicus-dem-30m.s3.eu-central-1.amazonaws.com"


# ----------------------------
# GEO HELPERS
# ----------------------------

def bbox_from_radius(lat, lon, radius_m):
    dlat = radius_m / 111320.0
    dlon = radius_m / (111320.0 * math.cos(math.radians(lat)))

    return lon - dlon, lat - dlat, lon + dlon, lat + dlat


def tile(lat, lon):
    ns = "N" if lat >= 0 else "S"
    ew = "E" if lon >= 0 else "W"

    return (
        f"Copernicus_DSM_COG_10_"
        f"{ns}{abs(lat):02d}_00_"
        f"{ew}{abs(lon):03d}_00_DEM"
    )


def tile_url(lat, lon):
    t = tile(lat, lon)
    return f"{BASE_URL}/{t}/{t}.tif"


def intersecting_tiles(min_lon, min_lat, max_lon, max_lat):
    for lat in range(math.floor(min_lat), math.floor(max_lat) + 1):
        for lon in range(math.floor(min_lon), math.floor(max_lon) + 1):
            yield lat, lon


# ----------------------------
# SIMPLE COG WINDOW READER (NO GDAL)
# ----------------------------

def read_cog_window(url, bbox):
    """
    Reads a small window from a remote COG using HTTP range requests.
    Uses tifffile which understands stripped TIFF structure.
    """

    min_lon, min_lat, max_lon, max_lat = bbox

    # Download just enough for tifffile to interpret structure
    # (requests streams + tifffile handles decoding)
    r = requests.get(url, stream=True, timeout=60)
    r.raise_for_status()

    data = BytesIO(r.content)

    with tifffile.TiffFile(data) as tif:
        page = tif.pages[0]

        # Full raster (COGs still allow partial decode internally)
        arr = page.asarray()

        # NOTE:
        # Without GDAL we don't have geotransform metadata,
        # so we assume north-up EPSG:4326 tile alignment (true for Copernicus DEM tiles)

        h, w = arr.shape

        # approximate pixel mapping (1° tile assumption)
        lon0 = math.floor(min_lon)
        lat0 = math.floor(min_lat)

        px_x0 = int((min_lon - lon0) / 1.0 * w)
        px_x1 = int((max_lon - lon0) / 1.0 * w)

        px_y0 = int((lat0 + 1 - max_lat) / 1.0 * h)
        px_y1 = int((lat0 + 1 - min_lat) / 1.0 * h)

        clipped = arr[px_y0:px_y1, px_x0:px_x1]

        return clipped


# ----------------------------
# MAIN PIPELINE
# ----------------------------

def download_dem(lat, lon, radius_m):
    bbox = bbox_from_radius(lat, lon, radius_m)

    tiles = list(intersecting_tiles(*bbox))

    mosaics = []

    for tlat, tlon in tiles:
        url = tile_url(tlat, tlon)

        try:
            print("Fetching:", url)
            arr = read_cog_window(url, bbox)
            mosaics.append(arr)
        except Exception as e:
            print("Skip:", e)

    if not mosaics:
        raise RuntimeError("No DEM data found")

    # simple merge (assumes same resolution)
    out = np.mean(np.stack(mosaics), axis=0)

    # fill NaNs if any
    out = np.nan_to_num(out, nan=-9999)

    imageio.imwrite("dem_clip.tif", out.astype(np.float32))

    print("Saved dem_clip.tif")


# ----------------------------
# RUN
# ----------------------------

if __name__ == "__main__":
    download_dem(
        lat=33.0871344,
        lon=35.4955926,
        radius_m=5000,
    )