package com.nofar.core.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface NetworkConnectivityMonitor {
    fun isNetworkAvailable(): Boolean

    fun isCellularNetwork(): Boolean
}

@Singleton
class DefaultNetworkConnectivityMonitor
@Inject
constructor(@ApplicationContext private val context: Context) :
    NetworkConnectivityMonitor {
    override fun isNetworkAvailable(): Boolean {
        val capabilities = activeCapabilities() ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun isCellularNetwork(): Boolean {
        val capabilities = activeCapabilities() ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun activeCapabilities(): NetworkCapabilities? {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager?.activeNetwork
        return network?.let { connectivityManager.getNetworkCapabilities(it) }
    }
}
