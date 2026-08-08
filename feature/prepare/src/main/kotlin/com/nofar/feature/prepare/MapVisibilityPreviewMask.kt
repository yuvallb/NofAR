package com.nofar.feature.prepare

import android.graphics.Bitmap

/** Pre-rasterized viewshed overlay produced off the map draw thread. */
data class MapVisibilityPreviewMask(val bitmap: Bitmap, val bounds: MapVisibilityPreviewMaskBounds)
