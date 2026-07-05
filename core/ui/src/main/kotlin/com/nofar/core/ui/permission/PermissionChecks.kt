package com.nofar.core.ui.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

internal fun isFineLocationGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

internal fun isCameraGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

internal fun isInternetGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET) ==
        PackageManager.PERMISSION_GRANTED

internal fun isLocationPermanentlyDenied(activity: Activity?): Boolean = activity != null &&
    !ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
