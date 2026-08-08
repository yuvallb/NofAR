---
layout: default
title: Privacy Policy
permalink: /privacy/
---

# NofAR Privacy Policy

**Last updated:** 8 August 2026

NofAR (“the app”) is an offline-first Android application. This policy describes what data the app accesses and how it is used.

## Summary

- No accounts, no sign-in, no cloud backend operated by NofAR.
- No analytics, advertising, or crash-reporting SDKs that phone home.
- Location (GPS) and camera are used **on your device** for Explore and related features.
- Network access is used **only in Prepare mode** to download map and elevation data you request.

## Data the app accesses

### Location (precise / GPS)

Used to detect whether you are inside a downloaded region, to place AR labels, and to define Prepare regions. Location is processed on-device. The app does not continuously upload a GPS track.

During **Prepare** downloads, Overpass API requests include the circular region’s bounding box (an approximate geographic area you chose), not a live GPS trail. See [Legal & data sources](#legal--data-sources).

### Camera

Used in **Explore** to show the live camera feed under AR labels. Frames are not uploaded.

### Network

Used only when you download or update a region in **Prepare**:

- OpenStreetMap place/peak data via public Overpass API mirrors
- Copernicus DEM elevation tiles

Home and Explore modes are designed to work offline once a region is ready.

### On-device storage

Downloaded regions, OSM entities, and elevation rasters are stored locally on your device. You can remove them from the app. The app does not sync this data to a NofAR server (there is none).

## Data sharing

NofAR does not sell or share personal data with advertisers or analytics vendors.

Third-party services contacted **only during Prepare**, at your request:

| Service | What is sent | Purpose |
|---------|----------------|---------|
| Overpass API mirrors | Overpass QL query including the region bounding box | Fetch OSM places and peaks |
| Copernicus DEM distribution | Tile download requests for the region | Fetch elevation data |

Those operators’ own policies apply to their servers. Prefer downloading over trusted networks.

## Permissions

The app may request:

- **Location** — region detection and AR placement
- **Camera** — Explore overlay
- **Internet / network state** — Prepare downloads only

## Children

NofAR is not directed at children and does not knowingly collect personal information from children.

## Changes

We may update this policy when app behavior changes. The “Last updated” date above will change accordingly. The current version is always published with the app’s source repository.

## Contact

Source and issues: [https://github.com/yuvallb/NofAR](https://github.com/yuvallb/NofAR)

## Legal & data sources

- Map data © OpenStreetMap contributors, [ODbL](https://www.openstreetmap.org/copyright).
- Elevation: Copernicus DEM, ESA / Airbus.
- App license: [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
