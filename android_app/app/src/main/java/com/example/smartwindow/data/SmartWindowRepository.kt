package com.example.smartwindow.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SmartWindowRepository(private val context: Context) {

    private val TAG = "SmartWindowRepo"
    var hostIp: String = "192.168.4.1"

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Resolves the active Wi-Fi Network interface, even if it has NO internet connection
     * (e.g. ESP32 SoftAP SmartWindow-686E40).
     */
    fun getWifiNetwork(): Network? {
        return try {
            connectivityManager.allNetworks.firstOrNull { network ->
                val caps = connectivityManager.getNetworkCapabilities(network)
                caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Wi-Fi network: ${e.message}")
            null
        }
    }

    /**
     * Binds the application process to the Wi-Fi network interface.
     * This forces Android to route all sockets (including DNS & HTTP) over the Wi-Fi interface,
     * completely bypassing Cellular Data (4G/5G).
     */
    fun bindProcessToWifi(): Boolean {
        return try {
            val wifiNet = getWifiNetwork()
            if (wifiNet != null) {
                val bound = connectivityManager.bindProcessToNetwork(wifiNet)
                Log.d(TAG, "Process bound to Wi-Fi network: $bound")
                bound
            } else {
                Log.w(TAG, "No Wi-Fi network available to bind process.")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "bindProcessToWifi failed: ${e.message}")
            false
        }
    }

    /**
     * Restores default system network routing (e.g. for Cloud MQTT over Cellular / normal Wi-Fi).
     */
    fun unbindProcessNetwork() {
        try {
            connectivityManager.bindProcessToNetwork(null)
            Log.d(TAG, "Process unbound from specific network.")
        } catch (e: Exception) {
            Log.w(TAG, "unbindProcessNetwork failed: ${e.message}")
        }
    }

    /**
     * Opens an HttpURLConnection explicitly routed through the Wi-Fi network interface.
     * Even if Mobile Data is active and Wi-Fi has "No Internet Access", this forces the TCP socket
     * directly out the Wi-Fi radio interface (wlan0) to 192.168.4.1.
     */
    private fun openConnection(url: URL): HttpURLConnection {
        val wifiNet = getWifiNetwork()
        val conn = if (wifiNet != null) {
            wifiNet.openConnection(url) as HttpURLConnection
        } else {
            url.openConnection() as HttpURLConnection
        }
        conn.setRequestProperty("Connection", "close")
        conn.setRequestProperty("User-Agent", "Iris-Android-App")
        return conn
    }

    suspend fun fetchStatus(): SmartWindowStatus = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$hostIp/api/status")
            conn = openConnection(url).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("Accept", "application/json")
            }

            val code = conn.responseCode
            if (code == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                SmartWindowStatus(
                    deviceId = json.optString("deviceId", "SW-ESP32"),
                    time = json.optString("clockTime", "14:00:00"),
                    mode = json.optString("mode", "AUTO"),
                    window = json.optString("windowState", "CLOSED"),
                    angle = json.optInt("currentAngle", 0),
                    outsideTemp = json.optDouble("outsideTemp", 0.0).toFloat(),
                    insideTemp = json.optDouble("insideTemp", 0.0).toFloat(),
                    outsideHumidity = json.optDouble("outsideHumidity", 0.0).toFloat(),
                    insideHumidity = json.optDouble("insideHumidity", 0.0).toFloat(),
                    isOutsideDark = json.optBoolean("isOutsideDark", false),
                    isInsideDark = json.optBoolean("isInsideDark", false),
                    rainRaw = json.optInt("rainRaw", 4095),
                    isRaining = json.optBoolean("isRaining", false),
                    isNight = json.optBoolean("isNight", false),
                    timerActive = json.optBoolean("timerActive", false),
                    pendingTarget = if (json.optString("windowState") == "OPEN") "CLOSE" else "OPEN",
                    timerRemaining = json.optInt("timerRemaining", 0),
                    reason = json.optString("reason", "OK"),
                    wifiConnected = true,
                    isOnline = true,
                    cloudConnected = json.optBoolean("cloudConnected", false),
                    systemEnabled = json.optBoolean("systemEnabled", true),
                    connectionType = "LOCAL"
                )
            } else {
                SmartWindowStatus(isOnline = false, errorMessage = "HTTP error: $code")
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchStatus failed: ${e.message}")
            SmartWindowStatus(isOnline = false, errorMessage = e.message ?: "Connection failed")
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun getDeviceInfo(): DeviceInfo = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$hostIp/api/device-info")
            conn = openConnection(url).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                DeviceInfo(
                    deviceId = json.optString("deviceId", "SW-ESP32"),
                    apSSID = json.optString("apSSID", ""),
                    staSSID = json.optString("staSSID", ""),
                    staConnected = json.optBoolean("staConnected", false),
                    staIP = json.optString("staIP", ""),
                    cloudConnected = json.optBoolean("cloudConnected", false),
                    topicStatus = json.optString("topicStatus", ""),
                    topicControl = json.optString("topicControl", "")
                )
            } else {
                DeviceInfo()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getDeviceInfo failed: ${e.message}")
            DeviceInfo()
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun sendControl(action: String, pin: String, time: String? = null): ApiResponse = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$hostIp/api/control")
            conn = openConnection(url).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Accept", "application/json")
            }

            var body = "pin=" + URLEncoder.encode(pin, "UTF-8") + "&action=" + URLEncoder.encode(action, "UTF-8")
            if (time != null) {
                body += "&time=" + URLEncoder.encode(time, "UTF-8")
            }

            val bodyBytes = body.toByteArray(Charsets.UTF_8)
            conn.setFixedLengthStreamingMode(bodyBytes.size)
            conn.outputStream.use { os ->
                os.write(bodyBytes)
                os.flush()
            }

            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""

            val json = JSONObject(responseText)
            ApiResponse(
                success = json.optBoolean("success", false),
                message = json.optString("message", "Response received")
            )
        } catch (e: Exception) {
            Log.e(TAG, "sendControl failed: ${e.message}")
            ApiResponse(success = false, message = e.message ?: "Action failed")
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun scanWifi(): List<WifiNetwork> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$hostIp/api/scan")
            conn = openConnection(url).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(response)
                val list = mutableListOf<WifiNetwork>()
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    list.add(
                        WifiNetwork(
                            ssid = item.optString("ssid", ""),
                            rssi = item.optInt("rssi", -100),
                            secure = item.optBoolean("secure", true)
                        )
                    )
                }
                list.filter { it.ssid.isNotEmpty() }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "scanWifi failed: ${e.message}")
            emptyList()
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun pairDevice(ssid: String, pass: String, pin: String): PairingResponse = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$hostIp/api/pair")
            conn = openConnection(url).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Accept", "application/json")
            }

            val body = "ssid=" + URLEncoder.encode(ssid, "UTF-8") +
                    "&pass=" + URLEncoder.encode(pass, "UTF-8") +
                    "&pin=" + URLEncoder.encode(pin, "UTF-8")

            val bodyBytes = body.toByteArray(Charsets.UTF_8)
            conn.setFixedLengthStreamingMode(bodyBytes.size)
            conn.outputStream.use { os ->
                os.write(bodyBytes)
                os.flush()
            }

            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""

            val json = JSONObject(responseText)
            PairingResponse(
                success = json.optBoolean("success", false),
                deviceId = json.optString("deviceId", ""),
                staIP = json.optString("staIP", ""),
                cloudConnected = json.optBoolean("cloudConnected", false),
                message = json.optString("message", "Pairing completed")
            )
        } catch (e: Exception) {
            Log.e(TAG, "pairDevice failed: ${e.message}")
            PairingResponse(success = false, message = e.message ?: "Pairing request failed")
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun updateSecurity(curPin: String, newPin: String, newApPass: String): ApiResponse = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$hostIp/api/security")
            conn = openConnection(url).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Accept", "application/json")
            }

            val body = "pin=" + URLEncoder.encode(curPin, "UTF-8") +
                    "&new_pin=" + URLEncoder.encode(newPin, "UTF-8") +
                    "&new_ap_pass=" + URLEncoder.encode(newApPass, "UTF-8")

            val bodyBytes = body.toByteArray(Charsets.UTF_8)
            conn.setFixedLengthStreamingMode(bodyBytes.size)
            conn.outputStream.use { os ->
                os.write(bodyBytes)
                os.flush()
            }

            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() } ?: ""

            val json = JSONObject(responseText)
            ApiResponse(
                success = json.optBoolean("success", false),
                message = json.optString("message", "Security updated")
            )
        } catch (e: Exception) {
            Log.e(TAG, "updateSecurity failed: ${e.message}")
            ApiResponse(success = false, message = e.message ?: "Failed to update security")
        } finally {
            conn?.disconnect()
        }
    }
}
