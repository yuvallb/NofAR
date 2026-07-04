#!/usr/bin/env -S uv run --script
# /// script
# dependencies = [
#   "numpy",
#   "requests",
#   "tifffile",
#   "imageio",
# ]
# ///

import argparse
import math
from io import BytesIO
from pathlib import Path

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


def initial_bearing(lat1, lon1, lat2, lon2):
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    lambda1 = math.radians(lon1)
    lambda2 = math.radians(lon2)

    y = math.sin(lambda2 - lambda1) * math.cos(phi2)
    x = math.cos(phi1) * math.sin(phi2) - math.sin(phi1) * math.cos(phi2) * math.cos(lambda2 - lambda1)
    return (math.degrees(math.atan2(y, x)) + 360.0) % 360.0


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
    url = tile_url(lat, lon)
    response = requests.get(url, timeout=60)
    response.raise_for_status()

    with BytesIO(response.content) as stream:
        with tifffile.TiffFile(stream) as tif:
            page = tif.pages[0]
            arr = page.asarray()

    if arr.ndim == 3:
        arr = arr[0]

    return arr.astype(np.float32)


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


def build_sample_points(lat1, lon1, lat2, lon2, step_m):
    total_distance = haversine_m(lat1, lon1, lat2, lon2)
    if total_distance <= 0:
        return [(lat1, lon1)]

    bearing = initial_bearing(lat1, lon1, lat2, lon2)
    steps = max(1, int(math.floor(total_distance / step_m)))
    points = []
    for i in range(steps + 1):
        dist = min(i * step_m, total_distance)
        lat, lon = destination_point(lat1, lon1, bearing, dist)
        points.append((lat, lon))

    if points[-1] != (lat2, lon2):
        points.append((lat2, lon2))

    return points


def collect_elevations(lat1, lon1, lat2, lon2, step_m):
    points = build_sample_points(lat1, lon1, lat2, lon2, step_m)
    tile_cache = {}
    elevations = []

    for lat, lon in points:
        tile_lat = int(math.floor(lat))
        tile_lon = int(math.floor(lon))
        tile_key = (tile_lat, tile_lon)

        if tile_key not in tile_cache:
            tile_cache[tile_key] = fetch_tile_array(tile_lat, tile_lon)

        sample = sample_elevation_from_tile(tile_cache[tile_key], lat, lon, tile_lat, tile_lon)
        elevations.append(sample)

    return points, elevations


def find_horizon(elevations, fixed_distance, observer_height=1.7):
    """
    Finds the index of the highest visible point (horizon) from the start of the array.

    :param elevations: List of elevations at fixed intervals
    :param fixed_distance: The horizontal distance between each array element
    :param observer_height: Height of the observer's eyes above the ground at index 0
    :return: Index of the horizon point
    """
    if len(elevations) < 2:
        return 0

    x0 = 0
    y0 = elevations[0] + observer_height

    max_slope = float('-inf')
    horizon_index = 0

    for i in range(1, len(elevations)):
        xi = i * fixed_distance
        yi = elevations[i]

        slope = (yi - y0) / (xi - x0)

        if slope > max_slope:
            max_slope = slope
            horizon_index = i

    return horizon_index


def render_profile_image(distances_m, elevations, output_path, start_coord, end_coord, line_of_sight, visible_peak):
    if Image is None or ImageDraw is None or ImageFont is None:
        raise RuntimeError("Pillow is required to render the annotated profile image.")

    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    width = 1400
    height = 700
    margin = 90
    image = np.full((height, width, 3), 248, dtype=np.uint8)

    valid = [(idx, distance, value) for idx, (distance, value) in enumerate(zip(distances_m, elevations)) if value is not None]
    if not valid:
        raise RuntimeError("No valid elevations were found for the requested line.")

    min_elev = min(value for _, _, value in valid)
    max_elev = max(value for _, _, value in valid)
    if max_elev == min_elev:
        max_elev = min_elev + 1.0

    total_distance = max(distances_m[-1], 1.0)
    xs = []
    ys = []
    for _, distance, value in valid:
        x = margin + (distance / total_distance) * (width - 2 * margin)
        y = margin + (max_elev - value) / (max_elev - min_elev) * (height - 2 * margin)
        xs.append(float(x))
        ys.append(float(y))

    for i in range(1, len(xs)):
        x0, x1 = int(xs[i - 1]), int(xs[i])
        y0, y1 = int(ys[i - 1]), int(ys[i])
        if x0 == x1 and y0 == y1:
            image[y0, x0] = [40, 40, 180]
            continue
        dx = abs(x1 - x0)
        dy = -abs(y1 - y0)
        sx = 1 if x0 < x1 else -1
        sy = 1 if y0 < y1 else -1
        err = dx + dy
        while True:
            if 0 <= x0 < width and 0 <= y0 < height:
                image[y0, x0] = [40, 40, 180]
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    pil_image = Image.fromarray(image, mode="RGB")
    draw = ImageDraw.Draw(pil_image)
    font = ImageFont.load_default()

    start_label = f"Start: {start_coord[0]:.4f}, {start_coord[1]:.4f}"
    end_label = f"End: {end_coord[0]:.4f}, {end_coord[1]:.4f}"
    start_bbox = draw.textbbox((0, 0), start_label, font=font)
    end_bbox = draw.textbbox((0, 0), end_label, font=font)
    start_w = start_bbox[2] - start_bbox[0]
    end_w = end_bbox[2] - end_bbox[0]

    draw.text((20, height - 32), start_label, fill=(20, 20, 20), font=font)
    draw.text((width - end_w - 20, height - 32), end_label, fill=(20, 20, 20), font=font)

    for tick in range(6):
        value = min_elev + (max_elev - min_elev) * tick / 5.0
        y = margin + (max_elev - value) / (max_elev - min_elev) * (height - 2 * margin)
        draw.line((20, int(y), 38, int(y)), fill=(90, 90, 90), width=1)
        draw.text((42, int(y) - 8), f"{int(round(value))} m", fill=(30, 30, 30), font=font)

    status_text = "Line of sight: Yes"
    status_color = (30, 120, 30)
    if not line_of_sight:
        status_text = "Line of sight: No"
        status_color = (170, 40, 40)
    draw.text((margin, 18), status_text, fill=status_color, font=font)

    if visible_peak is not None:
        peak_x = margin + (visible_peak["distance_m"] / total_distance) * (width - 2 * margin)
        peak_y = margin + (max_elev - visible_peak["elevation_m"]) / (max_elev - min_elev) * (height - 2 * margin)
        draw.ellipse((peak_x - 6, peak_y - 6, peak_x + 6, peak_y + 6), fill=(220, 40, 40))
        draw.text((int(peak_x) + 10, int(peak_y) - 14), "Visible peak", fill=(220, 40, 40), font=font)
        draw.text((int(peak_x) + 10, int(peak_y) + 4), f"{visible_peak['lat']:.4f}, {visible_peak['lon']:.4f}", fill=(220, 40, 40), font=font)

    pil_image.save(str(output_path))


def parse_args():
    parser = argparse.ArgumentParser(description="Sample DEM elevations along a straight line from public Copernicus DEM S3 tiles.")
    parser.add_argument("lat1", type=float, help="Latitude of the first point")
    parser.add_argument("lon1", type=float, help="Longitude of the first point")
    parser.add_argument("lat2", type=float, help="Latitude of the second point")
    parser.add_argument("lon2", type=float, help="Longitude of the second point")
    parser.add_argument("--step-m", type=float, default=100.0, help="Distance between sampled points in meters (default: 100)")
    parser.add_argument("--output-image", type=str, default="dem_profile.png", help="Path to write the elevation profile image")
    return parser.parse_args()


def main():
    args = parse_args()

    points, elevations = collect_elevations(args.lat1, args.lon1, args.lat2, args.lon2, args.step_m)

    distances = []
    current = 0.0
    distances.append(0.0)
    for i in range(1, len(points)):
        dist = haversine_m(points[i - 1][0], points[i - 1][1], points[i][0], points[i][1])
        current += dist
        distances.append(current)

    start_elev = elevations[0]
    end_elev = elevations[-1]
    total_distance = distances[-1] if distances else 0.0
    line_of_sight = True
    visible_peak = None

    if start_elev is not None and end_elev is not None and total_distance > 0.0:
        for idx, (distance, elevation) in enumerate(zip(distances, elevations)):
            if elevation is None:
                continue
            expected = start_elev + (end_elev - start_elev) * (distance / total_distance)
            if elevation > expected + 1.0:
                line_of_sight = False
                break

    if not line_of_sight:
        valid_elevations = [value for value in elevations if value is not None]
        if valid_elevations and len(valid_elevations) >= 2:
            fixed_distance = total_distance / max(1, len(elevations) - 1)
            horizon_idx = find_horizon(elevations, fixed_distance)
            if 0 < horizon_idx < len(elevations):
                peak_elevation = elevations[horizon_idx]
                peak_distance = distances[horizon_idx]
                visible_peak = {
                    "elevation_m": peak_elevation,
                    "lat": points[horizon_idx][0],
                    "lon": points[horizon_idx][1],
                    "distance_m": peak_distance,
                }

    cleaned = [None if value is None else round(float(value), 2) for value in elevations]
    print(cleaned)

    if line_of_sight:
        print("Line of sight: yes")
    else:
        print("Line of sight: no")
        if visible_peak is not None:
            print(
                f"Highest point visible from both points: {visible_peak['lat']:.6f}, {visible_peak['lon']:.6f} "
                f"(elevation {visible_peak['elevation_m']:.2f} m)"
            )

    render_profile_image(
        distances,
        elevations,
        args.output_image,
        (args.lat1, args.lon1),
        (args.lat2, args.lon2),
        line_of_sight,
        visible_peak,
    )
    print(f"Wrote profile image to {args.output_image}")


if __name__ == "__main__":
    main()
