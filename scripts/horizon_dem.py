#!/usr/bin/env -S uv run --script
# /// script
# dependencies = [
#   "numpy",
#   "requests",
#   "tifffile",
#   "imageio",
#   "imagecodecs",
# ]
# ///

import argparse
import math
from io import BytesIO
from pathlib import Path

CACHE_DIR = Path(__file__).resolve().parent / "dem_cache"

import imageio.v3 as imageio
import numpy as np
import requests
import tifffile

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:  # pragma: no cover - depends on environment
    Image = None
    ImageDraw = None
    ImageFont = None


BASE_URL = "https://copernicus-dem-30m.s3.eu-central-1.amazonaws.com"


def haversine_m(lat1, lon1, lat2, lon2):
    radius_m = 6371000.0
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)

    a = math.sin(dphi / 2.0) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2.0) ** 2
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return radius_m * c


def destination_point(lat, lon, bearing_deg, distance_m):
    radius_m = 6371000.0
    angular_distance = distance_m / radius_m
    bearing = math.radians(bearing_deg)
    phi1 = math.radians(lat)
    lambda1 = math.radians(lon)

    phi2 = math.asin(
        math.sin(phi1) * math.cos(angular_distance)
        + math.cos(phi1) * math.sin(angular_distance) * math.cos(bearing)
    )
    lambda2 = lambda1 + math.atan2(
        math.sin(bearing) * math.sin(angular_distance) * math.cos(phi1),
        math.cos(angular_distance) - math.sin(phi1) * math.sin(phi2),
    )

    return math.degrees(phi2), math.degrees(lambda2)


def tile_name(lat, lon):
    ns = "N" if lat >= 0 else "S"
    ew = "E" if lon >= 0 else "W"
    return (
        f"Copernicus_DSM_COG_10_"
        f"{ns}{abs(int(math.floor(lat))):02d}_00_"
        f"{ew}{abs(int(math.floor(lon))):03d}_00_DEM"
    )


def tile_url(lat, lon):
    return f"{BASE_URL}/{tile_name(lat, lon)}/{tile_name(lat, lon)}.tif"


def fetch_tile_array(lat, lon):
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    tile = tile_name(lat, lon)
    cache_path = CACHE_DIR / f"{tile}.npy"

    if cache_path.exists():
        return np.load(cache_path)

    url = tile_url(lat, lon)
    response = requests.get(url, timeout=60)
    response.raise_for_status()

    with BytesIO(response.content) as stream:
        with tifffile.TiffFile(stream) as tif:
            page = tif.pages[0]
            arr = page.asarray()

    if arr.ndim == 3:
        arr = arr[0]

    arr = arr.astype(np.float32)
    np.save(cache_path, arr)
    return arr


def sample_elevation_from_tile(arr, lat, lon, tile_lat, tile_lon):
    h, w = arr.shape
    lon0 = float(tile_lon)
    lon1 = float(tile_lon + 1)
    lat0 = float(tile_lat)
    lat1 = float(tile_lat + 1)

    if not (lon0 <= lon < lon1 and lat0 <= lat < lat1):
        return None

    x = int((lon - lon0) / (lon1 - lon0) * (w - 1))
    y = int((lat1 - lat) / (lat1 - lat0) * (h - 1))

    x = int(np.clip(x, 0, w - 1))
    y = int(np.clip(y, 0, h - 1))

    value = float(arr[y, x])
    if not np.isfinite(value):
        return None
    return value


def build_ray_samples(observer_lat, observer_lon, bearing_deg, max_distance_m, step_m):
    samples = []
    distance = 0.0
    while distance <= max_distance_m + 1e-9:
        lat, lon = destination_point(observer_lat, observer_lon, bearing_deg, distance)
        samples.append((distance, lat, lon))
        distance += step_m
    return samples


def sample_elevation_at_point(lat, lon, tile_cache):
    tile_lat = int(math.floor(lat))
    tile_lon = int(math.floor(lon))
    tile_key = (tile_lat, tile_lon)
    if tile_key not in tile_cache:
        tile_cache[tile_key] = fetch_tile_array(tile_lat, tile_lon)
    return sample_elevation_from_tile(tile_cache[tile_key], lat, lon, tile_lat, tile_lon)


def find_horizon_point(observer_lat, observer_lon, bearing_deg, max_distance_m, step_m, tile_cache, observer_height_m=1.7):
    samples = build_ray_samples(observer_lat, observer_lon, bearing_deg, max_distance_m, step_m)
    observer_elevation = None

    for distance, lat, lon in samples:
        if distance <= 0.0:
            tile_lat = int(math.floor(lat))
            tile_lon = int(math.floor(lon))
            tile_key = (tile_lat, tile_lon)
            if tile_key not in tile_cache:
                tile_cache[tile_key] = fetch_tile_array(tile_lat, tile_lon)
            observer_elevation = sample_elevation_from_tile(tile_cache[tile_key], lat, lon, tile_lat, tile_lon)
            break

    if observer_elevation is None:
        observer_elevation = 0.0

    best = None
    best_slope = float("-inf")

    for distance, lat, lon in samples:
        if distance <= 0.0:
            continue

        tile_lat = int(math.floor(lat))
        tile_lon = int(math.floor(lon))
        tile_key = (tile_lat, tile_lon)
        if tile_key not in tile_cache:
            tile_cache[tile_key] = fetch_tile_array(tile_lat, tile_lon)

        elevation = sample_elevation_from_tile(tile_cache[tile_key], lat, lon, tile_lat, tile_lon)
        if elevation is None:
            continue

        slope = (elevation - (observer_elevation + observer_height_m)) / max(distance, 1e-6)
        if slope > best_slope:
            best_slope = slope
            best = {
                "bearing_deg": bearing_deg,
                "distance_m": distance,
                "lat": lat,
                "lon": lon,
                "elevation_m": elevation,
            }

    return best


def direction_label(bearing_deg):
    directions = ["N", "NE", "E", "SE", "S", "SW", "W", "NW"]
    index = int(round(bearing_deg / 45.0)) % 8
    return directions[index]


def render_horizon_image(horizon_points, output_path, observer_lat, observer_lon, max_distance_m=20000, origin_elevation_m=0.0):
    if Image is None or ImageDraw is None or ImageFont is None:
        raise RuntimeError("Pillow is required to render the horizon image.")

    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    width = 1600
    height = 900
    margin_left = 110
    margin_right = 50
    margin_top = 70
    margin_bottom = 120

    image = np.full((height, width, 3), (248, 250, 252), dtype=np.uint8)
    pil_image = Image.fromarray(image, mode="RGB")
    draw = ImageDraw.Draw(pil_image)
    font = ImageFont.load_default()

    if not horizon_points:
        raise RuntimeError("No horizon points were generated from the requested location.")

    elevations = [item["elevation_m"] for item in horizon_points if item.get("elevation_m") is not None]
    if not elevations:
        raise RuntimeError("No elevation values were found for the requested horizon.")

    min_elev = min(min(elevations), origin_elevation_m - 50.0) - 20.0
    max_elev = max(max(elevations), origin_elevation_m + 50.0) + 20.0
    if max_elev == min_elev:
        max_elev = min_elev + 1.0

    plot_width = width - margin_left - margin_right
    plot_height = height - margin_top - margin_bottom

    draw.line((margin_left, margin_top, margin_left, height - margin_bottom), fill=(70, 70, 70), width=2)
    draw.line((margin_left, height - margin_bottom, width - margin_right, height - margin_bottom), fill=(70, 70, 70), width=2)

    origin_y = margin_top + (max_elev - origin_elevation_m) / (max_elev - min_elev) * plot_height
    draw.line((margin_left, int(origin_y), width - margin_right, int(origin_y)), fill=(180, 50, 50), width=2)
    draw.text((margin_left + 8, int(origin_y) - 18), "Origin", fill=(180, 50, 50), font=font)

    for tick in range(6):
        value = min_elev + (max_elev - min_elev) * tick / 5.0
        y = margin_top + (max_elev - value) / (max_elev - min_elev) * plot_height
        draw.line((margin_left - 8, int(y), margin_left, int(y)), fill=(100, 100, 100), width=1)
        draw.text((margin_left - 90, int(y) - 8), f"{int(round(value))} m", fill=(30, 30, 30), font=font)

    for bearing_deg in range(0, 360 + 10, 10):
        x = margin_left + (bearing_deg % 360) / 360.0 * plot_width
        draw.line((int(x), height - margin_bottom, int(x), height - margin_bottom + 6), fill=(100, 100, 100), width=1)

        if bearing_deg == 0 or bearing_deg == 360:
            label = "N 0°"
        else:
            label = f"{direction_label(bearing_deg)} {bearing_deg}°"
        draw.text((int(x) - 18, height - margin_bottom + 12), label, fill=(30, 30, 30), font=font)

    x_points = []
    y_points = []
    for item in horizon_points:
        bearing_deg = float(item["bearing_deg"])
        x = margin_left + (bearing_deg % 360) / 360.0 * plot_width
        y = margin_top + (max_elev - item["elevation_m"]) / (max_elev - min_elev) * plot_height
        x_points.append(x)
        y_points.append(y)

    if len(x_points) >= 2:
        x_points.append(x_points[0])
        y_points.append(y_points[0])
        for i in range(2, len(x_points)):
            draw.line((int(x_points[i - 1]), int(y_points[i - 1]), int(x_points[i]), int(y_points[i])), fill=(40, 40, 180), width=2)

    draw.text((20, 20), f"Horizon around {observer_lat:.4f}, {observer_lon:.4f}", fill=(20, 20, 20), font=font)
    draw.text((20, height - 40), f"Range: {max_distance_m/1000:.1f} km", fill=(40, 40, 40), font=font)

    pil_image.save(str(output_path))


def parse_args():
    parser = argparse.ArgumentParser(description="Render a 360-degree horizon image around a single DEM coordinate.")
    parser.add_argument("lat", type=float, help="Latitude of the observer")
    parser.add_argument("lon", type=float, help="Longitude of the observer")
    parser.add_argument("--max-distance-m", type=float, default=20000.0, help="Maximum ray length in meters (default: 20000)")
    parser.add_argument("--step-m", type=float, default=100.0, help="Distance between terrain samples along each ray in meters (default: 100)")
    parser.add_argument("--azimuth-step-deg", type=float, default=0.5, help="Angular step in degrees for the horizon scan (default: 0.5)")
    parser.add_argument("--observer-height-m", type=float, default=1.7, help="Observer height above ground in meters (default: 1.7)")
    parser.add_argument("--output-image", type=str, default="horizon.png", help="Path to write the horizon image")
    return parser.parse_args()


def main():
    args = parse_args()

    tile_cache = {}
    horizon_points = []
    origin_elevation = sample_elevation_at_point(args.lat, args.lon, tile_cache)
    if origin_elevation is None:
        origin_elevation = 0.0

    for bearing_deg in np.arange(0.0, 360.0, args.azimuth_step_deg):
        horizon = find_horizon_point(
            args.lat,
            args.lon,
            float(bearing_deg),
            args.max_distance_m,
            args.step_m,
            tile_cache,
            observer_height_m=args.observer_height_m,
        )
        if horizon is not None:
            horizon_points.append(horizon)

    if horizon_points:
        render_horizon_image(
            horizon_points,
            args.output_image,
            args.lat,
            args.lon,
            args.max_distance_m,
            origin_elevation_m=origin_elevation,
        )
    else:
        raise RuntimeError("No horizon points were generated from the requested location.")

    print(f"Wrote horizon image to {args.output_image}")


if __name__ == "__main__":
    main()
