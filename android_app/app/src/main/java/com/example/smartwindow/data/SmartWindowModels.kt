package com.example.smartwindow.data

data class SmartWindowStatus(
    val deviceId: String = "SW-ESP32",
    val time: String = "14:00:00",
    val mode: String = "AUTO",
    val window: String = "CLOSED",
    val angle: Int = 0,
    val outsideTemp: Float = 0f,
    val insideTemp: Float = 0f,
    val outsideHumidity: Float = 0f,
    val insideHumidity: Float = 0f,
    val isOutsideDark: Boolean = false,
    val isInsideDark: Boolean = false,
    val rainRaw: Int = 4095,
    val isRaining: Boolean = false,
    val isNight: Boolean = false,
    val timerActive: Boolean = false,
    val pendingTarget: String = "CLOSE",
    val timerRemaining: Int = 0,
    val reason: String = "System Ready",
    val wifiConnected: Boolean = false,
    val staIP: String = "",
    val isOnline: Boolean = false,
    val cloudConnected: Boolean = false,
    val systemEnabled: Boolean = true,   // Physical GPIO25 switch: true = System ON
    val connectionType: String = "CLOUD", // "CLOUD" (MQTT 4G/5G) or "LOCAL" (Direct Wi-Fi)
    val errorMessage: String? = null
)

data class WifiNetwork(
    val ssid: String,
    val rssi: Int,
    val secure: Boolean = true
)

data class ApiResponse(
    val success: Boolean,
    val message: String
)

data class PairingResponse(
    val success: Boolean,
    val deviceId: String = "",
    val staIP: String = "",
    val cloudConnected: Boolean = false,
    val message: String = ""
)

data class DeviceInfo(
    val deviceId: String = "",
    val apSSID: String = "",
    val staSSID: String = "",
    val staConnected: Boolean = false,
    val staIP: String = "",
    val cloudConnected: Boolean = false,
    val topicStatus: String = "",
    val topicControl: String = ""
)
