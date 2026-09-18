package com.example.smartwindow.network

import android.util.Log
import com.example.smartwindow.data.SmartWindowStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.UUID

class MqttManager(
    private val scope: CoroutineScope
) {
    private val TAG = "MqttManager"
    private val BROKER_URL = "tcp://broker.hivemq.com:1883"

    private var client: MqttClient? = null
    private var currentDeviceId: String = ""

    private val _cloudStatus = MutableStateFlow<SmartWindowStatus?>(null)
    val cloudStatus: StateFlow<SmartWindowStatus?> = _cloudStatus.asStateFlow()

    private val _isCloudConnected = MutableStateFlow(false)
    val isCloudConnected: StateFlow<Boolean> = _isCloudConnected.asStateFlow()

    fun connect(deviceId: String) {
        if (deviceId.isBlank()) return
        currentDeviceId = deviceId.trim()

        scope.launch(Dispatchers.IO) {
            try {
                // If already connected to the same device, no need to recreate
                if (client != null && client?.isConnected == true) {
                    subscribeToTopics()
                    return@launch
                }

                val clientId = "SmartWindowApp-" + UUID.randomUUID().toString().substring(0, 8)
                client = MqttClient(BROKER_URL, clientId, MemoryPersistence())

                val options = MqttConnectOptions().apply {
                    isAutomaticReconnect = true
                    isCleanSession = true
                    connectionTimeout = 10
                    keepAliveInterval = 20
                }

                client?.setCallback(object : MqttCallbackExtended {
                    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                        Log.d(TAG, "Connected to HiveMQ Cloud Broker: $serverURI (reconnect: $reconnect)")
                        _isCloudConnected.value = true
                        subscribeToTopics()
                    }

                    override fun connectionLost(cause: Throwable?) {
                        Log.w(TAG, "Connection lost: ${cause?.message}")
                        _isCloudConnected.value = false
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        if (message == null) return
                        val payload = String(message.payload)
                        Log.d(TAG, "Cloud message received on $topic: $payload")
                        parseStatusPayload(payload)
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                })

                Log.d(TAG, "Connecting to $BROKER_URL as $clientId ...")
                client?.connect(options)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect MQTT: ${e.message}", e)
                _isCloudConnected.value = false
            }
        }
    }

    private fun subscribeToTopics() {
        if (currentDeviceId.isBlank()) return
        val statusTopic = "smartwindow/$currentDeviceId/status"
        try {
            client?.subscribe(statusTopic, 1)
            Log.d(TAG, "Subscribed to $statusTopic")

            // Request immediate status
            publishCommand("STATUS", "2580")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to $statusTopic: ${e.message}", e)
        }
    }

    fun publishCommand(action: String, pin: String, time: String? = null, newPin: String? = null) {
        if (currentDeviceId.isBlank()) return
        scope.launch(Dispatchers.IO) {
            try {
                if (client?.isConnected != true) {
                    connect(currentDeviceId)
                }

                val controlTopic = "smartwindow/$currentDeviceId/control"
                val payload = when {
                    time != null -> "{\"action\":\"$action\",\"pin\":\"$pin\",\"time\":\"$time\"}"
                    newPin != null -> "{\"action\":\"$action\",\"pin\":\"$pin\",\"newPin\":\"$newPin\"}"
                    else -> "{\"action\":\"$action\",\"pin\":\"$pin\"}"
                }
                val message = MqttMessage(payload.toByteArray()).apply {
                    qos = 1
                }
                client?.publish(controlTopic, message)
                Log.d(TAG, "Published to $controlTopic: $payload")
            } catch (e: Exception) {
                Log.e(TAG, "Error publishing command: ${e.message}", e)
            }
        }
    }

    private fun parseStatusPayload(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val devId = json.optString("deviceId", currentDeviceId)
            val status = SmartWindowStatus(
                deviceId = devId,
                time = json.optString("clockTime", "14:00:00"),
                mode = json.optString("mode", "AUTO"),
                window = json.optString("windowState", "CLOSED"),
                angle = json.optInt("currentAngle", 0),
                outsideTemp = json.optDouble("outsideTemp", 0.0).toFloat(),
                insideTemp = json.optDouble("insideTemp", 0.0).toFloat(),
                outsideHumidity = json.optDouble("outsideHumidity", 0.0).toFloat(),
                insideHumidity = json.optDouble("insideHumidity", 0.0).toFloat(),
                isOutsideDark = if (json.has("isOutsideDark")) json.optBoolean("isOutsideDark") else (json.optInt("outsideLDR", 0) > 2200),
                isInsideDark = if (json.has("isInsideDark")) json.optBoolean("isInsideDark") else (json.optInt("insideLDR", 0) > 2200),
                rainRaw = json.optInt("rainRaw", 4095),
                isRaining = json.optBoolean("isRaining", false),
                isNight = json.optBoolean("isNight", false),
                timerActive = json.optBoolean("timerActive", false),
                timerRemaining = json.optInt("timerRemaining", 0),
                reason = json.optString("reason", "OK"),
                wifiConnected = true,
                isOnline = true,
                cloudConnected = true,
                systemEnabled = json.optBoolean("systemEnabled", true),
                connectionType = "CLOUD"
            )
            _cloudStatus.value = status
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON status: ${e.message}", e)
        }
    }

    fun disconnect() {
        scope.launch(Dispatchers.IO) {
            try {
                client?.disconnect()
                _isCloudConnected.value = false
            } catch (e: Exception) {
                Log.w(TAG, "Disconnect error: ${e.message}")
            }
        }
    }
}
