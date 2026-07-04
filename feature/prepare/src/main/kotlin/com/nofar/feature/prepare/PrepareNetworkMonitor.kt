package com.nofar.feature.prepare

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

internal object PrepareNetworkMonitor {
    fun isNetworkAvailable(context: Context): Boolean {
        val capabilities = activeCapabilities(context) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isCellularNetwork(context: Context): Boolean {
        val capabilities = activeCapabilities(context) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private fun activeCapabilities(context: Context): NetworkCapabilities? {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager?.activeNetwork
        return network?.let { connectivityManager.getNetworkCapabilities(it) }
    }
}
