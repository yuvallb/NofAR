package com.nofar.core.sensors

import android.content.Context
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

fun interface DisplayRotationReader {
    fun currentRotation(): Int
}

@Singleton
class AndroidDisplayRotationReader
@Inject
constructor(@ApplicationContext private val context: Context) :
    DisplayRotationReader {
    override fun currentRotation(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display.rotation
    } else {
        @Suppress("DEPRECATION")
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.rotation
    }
}

internal fun DisplayRotationReader.currentRotationOrDefault(): Int =
    runCatching { currentRotation() }.getOrDefault(Surface.ROTATION_0)
