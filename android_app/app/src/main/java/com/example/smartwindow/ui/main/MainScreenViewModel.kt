package com.example.smartwindow.ui.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartwindow.data.ApiResponse
import com.example.smartwindow.data.PairingResponse
import com.example.smartwindow.data.SmartWindowRepository
import com.example.smartwindow.data.SmartWindowStatus
import com.example.smartwindow.data.WifiNetwork
import com.example.smartwindow.network.MqttManager
import com.example.smartwindow.notifications.NotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {

    val repository: SmartWindowRepository = SmartWindowRepository(application.applicationContext)
    private val mqttManager = MqttManager(viewModelScope)
    private val prefs = application.getSharedPreferences("iris_prefs", Context.MODE_PRIVATE)

    // Current Device ID defaults to SW-686E40 (as detected from user's ESP32 hotspot!)
    private val _deviceId = MutableStateFlow(prefs.getString("device_id", "SW-686E40") ?: "SW-686E40")
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    // One-Time Setup Completed Flag
    private val _isSetupCompleted = MutableStateFlow(prefs.getBoolean("setup_completed", false))
    val isSetupCompleted: StateFlow<Boolean> = _isSetupCompleted.asStateFlow()

    // Navigation Tab (0: Dashboard / Home, 1: Sensors, 2: Settings)
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _status = MutableStateFlow(SmartWindowStatus())
    val status: StateFlow<SmartWindowStatus> = _status.asStateFlow()

    // Default to Local Mode (false) or previously saved mode so connection is immediate
    private val _isCloudMode = MutableStateFlow(prefs.getBoolean("is_cloud_mode", false))
    val isCloudMode: StateFlow<Boolean> = _isCloudMode.asStateFlow()

    // Saved Master PIN for automatic authorization (default: 2580)
    private val _savedPin = MutableStateFlow(prefs.getString("user_pin", "2580") ?: "2580")
    val savedPin: StateFlow<String> = _savedPin.asStateFlow()

    fun updateSavedPin(pin: String) {
        _savedPin.value = pin
        prefs.edit().putString("user_pin", pin).apply()
    }

    val isCloudConnected: StateFlow<Boolean> = mqttManager.isCloudConnected

    private val _wifiNetworks = MutableStateFlow<List<WifiNetwork>>(emptyList())
    val wifiNetworks: StateFlow<List<WifiNetwork>> = _wifiNetworks.asStateFlow()

    private val _isScanningWifi = MutableStateFlow(false)
    val isScanningWifi: StateFlow<Boolean> = _isScanningWifi.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    // Notification Preferences
    private val _notificationsEnabled = MutableStateFlow(prefs.getBoolean("notif_enabled", true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _notifyRain = MutableStateFlow(prefs.getBoolean("notify_rain", true))
    val notifyRain: StateFlow<Boolean> = _notifyRain.asStateFlow()

    private val _notifyDisconnect = MutableStateFlow(prefs.getBoolean("notify_disconnect", true))
    val notifyDisconnect: StateFlow<Boolean> = _notifyDisconnect.asStateFlow()

    private val _notifyWindowMove = MutableStateFlow(prefs.getBoolean("notify_window_move", true))
    val notifyWindowMove: StateFlow<Boolean> = _notifyWindowMove.asStateFlow()

    private val _notifyTempDiff = MutableStateFlow(prefs.getBoolean("notify_temp_diff", false))
    val notifyTempDiff: StateFlow<Boolean> = _notifyTempDiff.asStateFlow()

    fun updateNotificationPref(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        when (key) {
            "notif_enabled" -> _notificationsEnabled.value = value
            "notify_rain" -> _notifyRain.value = value
            "notify_disconnect" -> _notifyDisconnect.value = value
            "notify_window_move" -> _notifyWindowMove.value = value
            "notify_temp_diff" -> _notifyTempDiff.value = value
        }
    }

    // Notification Dispatcher
    private val notificationHelper = NotificationHelper(application)
    private var lastRainingState: Boolean? = null
    private var lastOnlineState: Boolean? = null
    private var lastWindowState: String? = null
    private var lastTempDiffAlertTime: Long = 0

    private fun checkAndDispatchNotifications(newStatus: SmartWindowStatus) {
        if (!_notificationsEnabled.value) return

        // 1. Rain Alert
        if (_notifyRain.value) {
            if (lastRainingState == false && newStatus.isRaining) {
                notificationHelper.sendRainAlert()
            }
        }
        lastRainingState = newStatus.isRaining

        // 2. Disconnect Alert (only if was previously connected and now went offline)
        val isCurrentOnline = if (_isCloudMode.value) isCloudConnected.value else newStatus.isOnline
        if (_notifyDisconnect.value) {
            if (lastOnlineState == true && !isCurrentOnline) {
                notificationHelper.sendDisconnectAlert()
            }
        }
        lastOnlineState = isCurrentOnline

        // 3. Window State Movement Alert
        if (_notifyWindowMove.value) {
            if (lastWindowState != null && lastWindowState != newStatus.window) {
                notificationHelper.sendWindowMovement(newStatus.window == "OPEN")
            }
        }
        lastWindowState = newStatus.window

        // 4. Temp Diff Alert (> 3.5C difference, throttle 5 minutes)
        if (_notifyTempDiff.value) {
            val diff = kotlin.math.abs(newStatus.outsideTemp - newStatus.insideTemp)
            if (diff >= 3.5f && (System.currentTimeMillis() - lastTempDiffAlertTime > 300000)) {
                lastTempDiffAlertTime = System.currentTimeMillis()
                notificationHelper.sendTempAlert(diff)
            }
        }
    }

    init {
        if (_isCloudMode.value) {
            mqttManager.connect(_deviceId.value)
        } else {
            repository.bindProcessToWifi()
        }

        // Collect Cloud Telemetry
        viewModelScope.launch {
            mqttManager.cloudStatus.collect { cloudStat ->
                if (cloudStat != null && _isCloudMode.value) {
                    _status.value = cloudStat
                    checkAndDispatchNotifications(cloudStat)
                }
            }
        }

        // Start Local Polling
        startLocalPolling()
    }

    private fun startLocalPolling() {
        viewModelScope.launch {
            while (true) {
                try {
                    if (!_isCloudMode.value || !isCloudConnected.value) {
                        if (!_isCloudMode.value) {
                            repository.bindProcessToWifi()
                        }
                        val localStat = repository.fetchStatus()
                        if (localStat.isOnline) {
                            _status.value = localStat
                            checkAndDispatchNotifications(localStat)
                            if (localStat.deviceId.isNotBlank() && localStat.deviceId != _deviceId.value) {
                                setDeviceId(localStat.deviceId)
                            }
                        } else if (!_isCloudMode.value) {
                            _status.value = _status.value.copy(
                                isOnline = false,
                                errorMessage = localStat.errorMessage
                            )
                            checkAndDispatchNotifications(_status.value)
                        }
                    }
                } catch (e: Exception) {
                    // Safe guard against unhandled coroutine exception
                }
                // Snappy 1-second polling in Local mode for real-time responsiveness!
                delay(if (!_isCloudMode.value) 1000 else 2500)
            }
        }
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun setDeviceId(newId: String) {
        val trimmed = newId.trim()
        if (trimmed.isNotBlank() && trimmed != _deviceId.value) {
            _deviceId.value = trimmed
            prefs.edit().putString("device_id", trimmed).apply()
            mqttManager.connect(trimmed)
        }
    }

    fun completeSetup(deviceId: String) {
        setDeviceId(deviceId)
        _isSetupCompleted.value = true
        prefs.edit().putBoolean("setup_completed", true).apply()
        _isCloudMode.value = true
        prefs.edit().putBoolean("is_cloud_mode", true).apply()
        repository.unbindProcessNetwork()
        mqttManager.connect(deviceId)
        _userMessage.value = "Setup completed! Switched to Cloud Mode."
    }

    fun toggleConnectionMode() {
        val newMode = !_isCloudMode.value
        _isCloudMode.value = newMode
        prefs.edit().putBoolean("is_cloud_mode", newMode).apply()

        if (newMode) {
            repository.unbindProcessNetwork()
            mqttManager.connect(_deviceId.value)
            _userMessage.value = "Switched to Cloud Mode (HiveMQ) ☁️"
        } else {
            val bound = repository.bindProcessToWifi()
            _userMessage.value = if (bound) "Switched to Local Mode (Direct Wi-Fi ⚡)" else "Switched to Local Mode (Connecting...)"
            refreshLocal()
        }
    }

    fun setHostIp(newIp: String) {
        repository.hostIp = newIp.trim()
        refreshLocal()
    }

    fun refresh() {
        if (_isCloudMode.value) {
            mqttManager.publishCommand("STATUS", "2580")
        } else {
            refreshLocal()
        }
    }

    fun refreshLocal() {
        viewModelScope.launch {
            repository.bindProcessToWifi()
            val stat = repository.fetchStatus()
            _status.value = stat
            if (!stat.isOnline) {
                _userMessage.value = "Local connection: ${stat.errorMessage}"
            }
        }
    }

    fun sendWindowAction(action: String, pin: String) {
        viewModelScope.launch {
            if (_isCloudMode.value && isCloudConnected.value) {
                mqttManager.publishCommand(action, pin)
                _userMessage.value = "Action '$action' sent via Cloud (4G/5G) 🚀"
            } else {
                repository.bindProcessToWifi()
                val response = repository.sendControl(action, pin)
                _userMessage.value = response.message
                val updated = repository.fetchStatus()
                if (updated.isOnline) {
                    _status.value = updated
                }
            }
        }
    }

    fun toggleSystem(pin: String) {
        viewModelScope.launch {
            if (_isCloudMode.value && isCloudConnected.value) {
                mqttManager.publishCommand("SYSTEM_TOGGLE", pin)
                _userMessage.value = "System power toggle sent via Cloud ⚙️"
            } else {
                repository.bindProcessToWifi()
                val response = repository.sendControl("SYSTEM_TOGGLE", pin)
                _userMessage.value = response.message
                val updated = repository.fetchStatus()
                if (updated.isOnline) {
                    _status.value = updated
                }
            }
        }
    }

    fun setClockTime(time: String, pin: String) {
        viewModelScope.launch {
            val normalizedTime = time.trim().replace('.', ':')
            if (_isCloudMode.value && isCloudConnected.value) {
                mqttManager.publishCommand("SET_TIME", pin, normalizedTime)
                _userMessage.value = "Clock updated to '$normalizedTime' via Cloud ⏱️"
            } else {
                repository.bindProcessToWifi()
                val response = repository.sendControl("SET_TIME", pin, normalizedTime)
                _userMessage.value = response.message
                val updated = repository.fetchStatus()
                if (updated.isOnline) {
                    _status.value = updated
                }
            }
        }
    }

    fun scanWifi() {
        viewModelScope.launch {
            repository.bindProcessToWifi()
            _isScanningWifi.value = true
            _wifiNetworks.value = repository.scanWifi()
            _isScanningWifi.value = false
        }
    }

    fun pairDevice(ssid: String, pass: String, pin: String, onResult: (PairingResponse) -> Unit) {
        viewModelScope.launch {
            repository.bindProcessToWifi()
            val response = repository.pairDevice(ssid, pass, pin)
            if (response.success && response.deviceId.isNotBlank()) {
                completeSetup(response.deviceId)
            }
            onResult(response)
        }
    }

    fun updateSecurity(curPin: String, newPin: String, newPass: String) {
        viewModelScope.launch {
            if (newPin.isNotBlank()) {
                updateSavedPin(newPin)
            }
            if (_isCloudMode.value && isCloudConnected.value) {
                if (newPin.isNotBlank()) {
                    mqttManager.publishCommand("CHANGE_PIN", curPin, newPin = newPin)
                    _userMessage.value = "Security PIN updated successfully via Cloud! 🔒"
                }
            } else {
                repository.bindProcessToWifi()
                val response = repository.updateSecurity(curPin, newPin, newPass)
                _userMessage.value = response.message
            }
        }
    }

    fun clearMessage() {
        _userMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        repository.unbindProcessNetwork()
        mqttManager.disconnect()
    }
}
