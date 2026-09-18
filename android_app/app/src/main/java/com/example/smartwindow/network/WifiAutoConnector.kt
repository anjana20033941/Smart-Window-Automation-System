package com.example.smartwindow.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.PatternMatcher
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WifiAutoConnector(private val context: Context) {
    private val TAG = "WifiAutoConnector"
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val _isConnectedToHotspot = MutableStateFlow(false)
    val isConnectedToHotspot: StateFlow<Boolean> = _isConnectedToHotspot.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Ready")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    fun connectToSmartWindow(
        onConnected: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            release() // Clear any existing callback

            _connectionStatus.value = "Requesting connection to SmartWindow..."
            Log.d(TAG, "Requesting network via WifiNetworkSpecifier for SmartWindow* ...")

            val specifier = WifiNetworkSpecifier.Builder()
                .setSsidPattern(PatternMatcher("SmartWindow", PatternMatcher.PATTERN_PREFIX))
                .setWpa2Passphrase("12345678")
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) // Allow network with no internet!
                .setNetworkSpecifier(specifier)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Log.d(TAG, "SmartWindow Wi-Fi network acquired! Binding process network...")

                    // CRITICAL: Bind entire app process to this Wi-Fi network!
                    // This prevents Android from disconnecting due to "No Internet Access"
                    // and forces all HTTP requests to 192.168.4.1 to go through this Wi-Fi!
                    val bound = connectivityManager.bindProcessToNetwork(network)
                    Log.d(TAG, "Process bound to network: $bound")

                    _isConnectedToHotspot.value = true
                    _connectionStatus.value = "Connected & Bound to SmartWindow Wi-Fi"
                    onConnected()
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    Log.w(TAG, "SmartWindow Wi-Fi network lost")
                    connectivityManager.bindProcessToNetwork(null)
                    _isConnectedToHotspot.value = false
                    _connectionStatus.value = "Disconnected"
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    Log.e(TAG, "SmartWindow Wi-Fi request unavailable or cancelled by user")
                    _isConnectedToHotspot.value = false
                    _connectionStatus.value = "Connection cancelled or device not in range"
                    onError("SmartWindow Wi-Fi was not found or connection was cancelled.")
                }
            }

            connectivityManager.requestNetwork(request, networkCallback!!)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate WifiNetworkSpecifier: ${e.message}", e)
            _connectionStatus.value = "Error: ${e.message}"
            onError(e.message ?: "Failed to connect Wi-Fi")
        }
    }

    fun release() {
        try {
            connectivityManager.bindProcessToNetwork(null)
            networkCallback?.let {
                connectivityManager.unregisterNetworkCallback(it)
                networkCallback = null
            }
            _isConnectedToHotspot.value = false
            _connectionStatus.value = "Released"
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing network callback: ${e.message}")
        }
    }
}
