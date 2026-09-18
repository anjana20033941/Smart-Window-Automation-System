#include <Arduino.h>
#include <WiFi.h>
#include <WebServer.h>
#include <Preferences.h>
#include <ESP32Servo.h>
#include <PubSubClient.h>
#include "DHT.h"

// ================= PIN DEFINITIONS =================
#define DHT_OUTSIDE_PIN    27     // Outside DHT11 Data Pin (GPIO 27)
#define DHT_INSIDE_PIN     26     // Inside DHT11 Data Pin (GPIO 26)
#define DHTTYPE            DHT11  // Sensor Model: DHT11

#define SERVO1_PIN         18     // Window Servo 1 Signal (GPIO 18)
#define SERVO2_PIN         19     // Window Servo 2 Signal (GPIO 19)

#define LDR_OUTSIDE_PIN    32     // Outside LDR Analog Pin (GPIO 32 - ADC1)
#define LDR_INSIDE_PIN     33     // Inside LDR Analog Pin (GPIO 33 - ADC1)
#define RAIN_SENSOR_PIN    36     // Rain Sensor Analog Pin (GPIO 36 / VP - ADC1)

#define MANUAL_SWITCH_PIN  25     // Manual Window Open/Close Toggle Switch (GPIO 25)

// ================= WINDOW ANGLE CONFIGURATION ======
#define WINDOW_CLOSED_POS  0      // 0 degrees = Closed
#define WINDOW_OPEN_POS    180    // 180 degrees = Fully Open (180 deg)

// ================= THRESHOLDS & TIMERS =============
#define TEMP_DIFF_THRESHOLD 3.5   // 3.5 °C Temperature Difference Threshold
const unsigned long CONFIRM_TIME_MS = 3500; // 3.5-second confirmation debounce for all sensors

#define RAIN_THRESHOLD      3400  // < 3400 indicates rain detected (high sensitivity)
#define LDR_DARK_THRESHOLD  2200  // > 2200 Dark/Night; < 1800 Bright/Day

// Calibration Offsets (Outside -9.5C calibration)
float insideTempOffset  = 0.0;
float outsideTempOffset = -9.5;

// ================= DEFAULT SECURITY CONSTANTS ======
#define DEFAULT_AP_SSID    "SMART_WINDOW"
#define DEFAULT_AP_PASS    "12345678"
#define DEFAULT_USER_PIN   "2580"

// ================= CLOUD MQTT BROKER CONFIG ========
const char* MQTT_BROKER = "broker.hivemq.com";
const int   MQTT_PORT   = 1883;

// ================= GLOBAL OBJECTS ==================
DHT dhtOutside(DHT_OUTSIDE_PIN, DHTTYPE);
DHT dhtInside(DHT_INSIDE_PIN, DHTTYPE);
Servo windowServo1;
Servo windowServo2;
WebServer server(80);
Preferences prefs;
WiFiClient espClient;
PubSubClient mqttClient(espClient);

// ================= STATE VARIABLES =================
bool isWindowOpen = false;
int currentWindowAngle = WINDOW_CLOSED_POS;
bool isAutoMode = true;
bool isSystemEnabled = true;  // System ON/OFF master switch (GPIO 25 physical button)

// Device ID & MQTT Topics
String deviceId = "";
String topicStatus = "";
String topicControl = "";
String topicInfo = "";

// Active Security Credentials (Loaded from NVS Flash)
String apSSID   = DEFAULT_AP_SSID;
String apPass   = DEFAULT_AP_PASS;
String userPIN  = DEFAULT_USER_PIN;
String homeSSID = "";
String homePass = "";

// Security Lockout Tracking
int failedPinAttempts = 0;
unsigned long lockoutStartTime = 0;
const unsigned long LOCKOUT_DURATION_MS = 60000; // 60 seconds lockout after 5 fails

// Sensor & Timer Tracking
unsigned long lastSensorReadTime = 0;
const unsigned long SENSOR_INTERVAL = 800; // Read sensors every 800ms for fast responsive updates

// Sensor Readings Cache
float cacheOutsideTemp = 0.0, cacheInsideTemp = 0.0;
float cacheOutsideHumidity = 0.0, cacheInsideHumidity = 0.0;
int cacheOutsideLDR = 0, cacheInsideLDR = 0, cacheRain = 0;
bool cacheIsRaining = false, cacheIsOutsideDark = false, cacheIsInsideDark = false;
bool cacheIsNight = false;

// Real-Time Clock Variables (Default starts at 14:00:00 / 2:00 PM)
int clockHour   = 14;
int clockMinute = 0;
int clockSecond = 0;
unsigned long lastClockTick = 0;

// 5-Second Confirmation Timer Tracking
enum WindowState { STATE_CLOSED, STATE_OPEN };
WindowState pendingTargetState = STATE_CLOSED;
bool timerActive = false;
unsigned long conditionStartTime = 0;
String pendingReason = "";

// Button & Factory Reset Tracking
int lastButtonState = HIGH;
unsigned long buttonPressStartTime = 0;
bool buttonIsPressed = false;

// MQTT Reconnect Non-blocking Timer
unsigned long lastMqttReconnectAttempt = 0;
unsigned long lastCloudPublishTime = 0;
unsigned long wifiDisconnectStartTime = 0;

// ================= FUNCTION PROTOTYPES =============
void openWindows();
void closeWindows();
void moveServosSmoothly(int targetAngle);
void updateClock();
void handleSerialCommands();
void handleManualSwitch();
void setSystemState(bool enabled);
void setupWiFi();
void setupWebServer();
void setupMQTT();
void connectMQTT();
void mqttCallback(char* topic, byte* payload, unsigned int length);
void publishStatusCloud();
void loadCredentials();
void saveCredentials();
void factoryReset();
void initDeviceId();

// ================= HTML WEB DASHBOARD ==============
const char INDEX_HTML[] PROGMEM = R"rawliteral(
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Smart Window Automation</title>
<style>
  :root {
    --bg: #0f172a; --card: #1e293b; --text: #f8fafc; --text-muted: #94a3b8;
    --primary: #38bdf8; --success: #22c55e; --warning: #f59e0b; --danger: #ef4444;
    --border: #334155;
  }
  * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
  body { background: var(--bg); color: var(--text); padding: 16px; min-height: 100vh; }
  .container { max-width: 900px; margin: 0 auto; }
  header { display: flex; justify-content: space-between; align-items: center; padding: 12px 0 20px 0; border-bottom: 1px solid var(--border); }
  h1 { font-size: 1.4rem; display: flex; align-items: center; gap: 8px; color: var(--primary); }
  .badge { padding: 4px 10px; border-radius: 9999px; font-size: 0.75rem; font-weight: bold; text-transform: uppercase; }
  .badge-auto { background: #0369a1; color: #bae6fd; }
  .badge-cloud { background: #15803d; color: #bbf7d0; }
  .badge-cloud-off { background: #b91c1c; color: #fecaca; }
  .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 14px; margin-top: 16px; }
  .card { background: var(--card); border: 1px solid var(--border); border-radius: 14px; padding: 16px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.3); }
  .card-title { font-size: 0.8rem; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 6px; }
  .card-value { font-size: 1.7rem; font-weight: bold; }
  .card-sub { font-size: 0.75rem; color: var(--text-muted); margin-top: 4px; }
  .window-hero { grid-column: 1 / -1; display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; background: linear-gradient(135deg, #1e293b, #0f172a); border: 1px solid var(--primary); }
  .window-status-text { font-size: 2.2rem; font-weight: 800; color: var(--primary); }
  .actions { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 10px; }
  button { padding: 10px 18px; border-radius: 10px; font-size: 0.9rem; font-weight: 600; cursor: pointer; border: none; transition: 0.2s; display: inline-flex; align-items: center; gap: 6px; }
  .btn-open { background: var(--success); color: #fff; }
  .btn-close { background: var(--danger); color: #fff; }
  .btn-mode { background: var(--primary); color: #0f172a; }
  .btn-util { background: #334155; color: #fff; }
  button:hover { opacity: 0.9; transform: translateY(-1px); }
  .timer-banner { background: #0284c7; color: #fff; padding: 8px 14px; border-radius: 10px; margin-top: 14px; font-size: 0.85rem; display: none; align-items: center; justify-content: space-between; }
</style>
</head>
<body>
<div class="container">
  <header>
    <div>
      <h1>🪟 Smart Window</h1>
      <div id="deviceLabel" style="font-size: 0.85rem; color: var(--text-muted); margin-top: 4px;">Device ID: SW-Loading...</div>
    </div>
    <div style="display: flex; gap: 8px; align-items: center;">
      <span id="badgeCloud" class="badge badge-cloud">CLOUD READY</span>
      <span id="badgeMode" class="badge badge-auto">AUTO</span>
    </div>
  </header>

  <div id="timerBanner" class="timer-banner">
    <span id="timerText">Verifying condition...</span>
    <strong id="timerCount">5s</strong>
  </div>

  <div class="grid">
    <div class="card window-hero">
      <div>
        <div class="card-title">Window State</div>
        <div id="windowState" class="window-status-text">CLOSED</div>
        <div id="windowSub" class="card-sub">Position: 0° | All Auto Protections Active</div>
      </div>
      <div class="actions">
        <button class="btn-open" onclick="reqAction('OPEN')">🔓 Open (180°)</button>
        <button class="btn-close" onclick="reqAction('CLOSE')">🔒 Close (0°)</button>
        <button class="btn-mode" onclick="reqAction('MODE_TOGGLE')">🔄 Toggle Mode</button>
      </div>
    </div>

    <div class="card">
      <div class="card-title">Temperature (Diff: <span id="tempDiff">0.0</span>°C)</div>
      <div style="display:flex; justify-content:space-between; align-items:baseline;">
        <div>
          <div style="font-size:0.75rem; color:var(--text-muted);">OUTSIDE (-9.5°C)</div>
          <div id="tempOut" class="card-value">--°C</div>
        </div>
        <div>
          <div style="font-size:0.75rem; color:var(--text-muted);">INSIDE</div>
          <div id="tempIn" class="card-value">--°C</div>
        </div>
      </div>
      <div class="card-sub">Threshold: 3.5°C Diff required</div>
    </div>

    <div class="card">
      <div class="card-title">Humidity</div>
      <div style="display:flex; justify-content:space-between; align-items:baseline;">
        <div><div style="font-size:0.75rem; color:var(--text-muted);">OUTSIDE</div><div id="humOut" class="card-value">--%</div></div>
        <div><div style="font-size:0.75rem; color:var(--text-muted);">INSIDE</div><div id="humIn" class="card-value">--%</div></div>
      </div>
      <div class="card-sub">Relative Humidity</div>
    </div>

    <div class="card">
      <div class="card-title">Rain Detection</div>
      <div id="rainStatus" class="card-value" style="color:var(--success);">DRY</div>
      <div id="rainRaw" class="card-sub">Raw: 4095</div>
    </div>

    <div class="card">
      <div class="card-title">Light (LDR Sensors)</div>
      <div style="display:flex; justify-content:space-between; align-items:baseline;">
        <div><div style="font-size:0.75rem; color:var(--text-muted);">OUT</div><div id="lightOut" class="card-value" style="font-size:1.2rem;">--</div></div>
        <div><div style="font-size:0.75rem; color:var(--text-muted);">IN</div><div id="lightIn" class="card-value" style="font-size:1.2rem;">--</div></div>
      </div>
      <div id="nightText" class="card-sub">Daylight Active</div>
    </div>

    <div class="card">
      <div class="card-title">System Clock</div>
      <div id="clockTime" class="card-value">14:00:00</div>
      <div class="card-sub">Night Lock past 18:00</div>
    </div>
  </div>
</div>

<script>
let lastStatus = {};
function fetchStatus() {
  fetch('/api/status')
    .then(r => r.json())
    .then(d => {
      lastStatus = d;
      document.getElementById('deviceLabel').textContent = 'Device ID: ' + (d.deviceId || 'SW-ESP32');
      document.getElementById('windowState').textContent = d.windowState;
      document.getElementById('windowState').style.color = (d.windowState === 'OPEN') ? 'var(--success)' : 'var(--primary)';
      document.getElementById('windowSub').textContent = 'Position: ' + d.currentAngle + '° | ' + d.reason;
      document.getElementById('tempOut').textContent = d.outsideTemp.toFixed(1) + '°C';
      document.getElementById('tempIn').textContent = d.insideTemp.toFixed(1) + '°C';
      document.getElementById('tempDiff').textContent = Math.abs(d.outsideTemp - d.insideTemp).toFixed(1);
      document.getElementById('humOut').textContent = d.outsideHumidity.toFixed(0) + '%';
      document.getElementById('humIn').textContent = d.insideHumidity.toFixed(0) + '%';
      document.getElementById('rainStatus').textContent = d.isRaining ? 'RAINING!' : 'DRY';
      document.getElementById('rainStatus').style.color = d.isRaining ? 'var(--danger)' : 'var(--success)';
      document.getElementById('rainRaw').textContent = 'Raw: ' + d.rainRaw;
      document.getElementById('lightOut').textContent = d.outsideLDR;
      document.getElementById('lightIn').textContent = d.insideLDR;
      document.getElementById('nightText').textContent = d.isNight ? '🌙 Night Detected' : '☀️ Daylight';
      document.getElementById('clockTime').textContent = d.clockTime;
      document.getElementById('badgeMode').textContent = d.mode;
      document.getElementById('badgeCloud').className = d.cloudConnected ? 'badge badge-cloud' : 'badge badge-cloud-off';
      document.getElementById('badgeCloud').textContent = d.cloudConnected ? 'CLOUD ONLINE' : 'LOCAL ONLY';

      const tb = document.getElementById('timerBanner');
      if (d.timerActive) {
        tb.style.display = 'flex';
        document.getElementById('timerText').textContent = 'Action in progress: ' + d.reason;
        document.getElementById('timerCount').textContent = d.timerRemaining + 's';
      } else {
        tb.style.display = 'none';
      }
    }).catch(e => console.error(e));
}

function reqAction(act) {
  let pin = prompt('Enter 4-digit Security PIN:', '2580');
  if (!pin) return;
  fetch('/api/control', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: 'action=' + act + '&pin=' + encodeURIComponent(pin)
  }).then(r => r.json()).then(res => {
    alert(res.message);
    fetchStatus();
  });
}
setInterval(fetchStatus, 1500);
fetchStatus();
</script>
</body>
</html>
)rawliteral";

// ================= DEVICE ID INITIALIZATION ========
void initDeviceId() {
    uint8_t mac[6];
    WiFi.macAddress(mac);
    char buf[16];
    snprintf(buf, sizeof(buf), "SW-%02X%02X%02X", mac[3], mac[4], mac[5]);
    deviceId = String(buf);

    topicStatus  = "smartwindow/" + deviceId + "/status";
    topicControl = "smartwindow/" + deviceId + "/control";
    topicInfo    = "smartwindow/" + deviceId + "/info";

    Serial.printf("[DEVICE] Initialized Device ID: %s\n", deviceId.c_str());
    Serial.printf("[MQTT] Status Topic:  %s\n", topicStatus.c_str());
    Serial.printf("[MQTT] Control Topic: %s\n", topicControl.c_str());
}

// ================= SETUP ===========================
void setup() {
    Serial.begin(115200);
    delay(500);

    Serial.println("\n\n========================================");
    Serial.println("  Smart Window Automation System (Cloud) ");
    Serial.println("========================================");

    // Initialize Device ID & Preferences
    initDeviceId();
    loadCredentials();

    // Allocate Hardware Timers and Attach Servos
    ESP32PWM::allocateTimer(0);
    ESP32PWM::allocateTimer(1);
    ESP32PWM::allocateTimer(2);
    ESP32PWM::allocateTimer(3);
    windowServo1.setPeriodHertz(50);
    windowServo2.setPeriodHertz(50);
    int ch1 = windowServo1.attach(SERVO1_PIN, 500, 2400);
    int ch2 = windowServo2.attach(SERVO2_PIN, 500, 2400);
    Serial.printf("[SERVO] Attached Servo1 to GPIO %d (ch %d), Servo2 to GPIO %d (ch %d)\n", SERVO1_PIN, ch1, SERVO2_PIN, ch2);

    // Initialize Manual Window Switch Pin with internal pullup
    pinMode(MANUAL_SWITCH_PIN, INPUT_PULLUP);

    // Initial position: Closed (0 deg)
    windowServo1.write(WINDOW_CLOSED_POS);
    windowServo2.write(WINDOW_CLOSED_POS);
    currentWindowAngle = WINDOW_CLOSED_POS;
    isWindowOpen = false;

    // Safety: Power was disconnected / restored -> Start safely CLOSED and in System OFF
    setSystemState(false);

    // Initialize DHT11 Sensors with Internal Pullup
    pinMode(DHT_OUTSIDE_PIN, INPUT_PULLUP);
    pinMode(DHT_INSIDE_PIN, INPUT_PULLUP);
    dhtOutside.begin();
    dhtInside.begin();

    // Start Wi-Fi & WebServer
    setupWiFi();
    setupWebServer();

    // Configure Cloud MQTT
    setupMQTT();

    Serial.println("[SETUP] System Initialization Complete!");
}

// ================= MQTT SETUP & CALLBACK ===========
void setupMQTT() {
    mqttClient.setServer(MQTT_BROKER, MQTT_PORT);
    mqttClient.setCallback(mqttCallback);
    mqttClient.setBufferSize(768);
}

void connectMQTT() {
    if (WiFi.status() != WL_CONNECTED) return;
    if (mqttClient.connected()) return;

    unsigned long now = millis();
    if (now - lastMqttReconnectAttempt > 5000) {
        lastMqttReconnectAttempt = now;
        Serial.printf("[MQTT] Connecting to Cloud Broker (%s:%d) as %s ...\n", MQTT_BROKER, MQTT_PORT, deviceId.c_str());

        String clientId = "ESP32-" + deviceId + "-" + String(random(0xffff), HEX);
        if (mqttClient.connect(clientId.c_str())) {
            Serial.println("[MQTT] Cloud Connected Successfully! Subscribing to control topic...");
            mqttClient.subscribe(topicControl.c_str());
            publishStatusCloud();
        } else {
            Serial.printf("[MQTT] Cloud Connect Failed, rc=%d. Will retry in 5s\n", mqttClient.state());
        }
    }
}

void mqttCallback(char* topic, byte* payload, unsigned int length) {
    char message[length + 1];
    memcpy(message, payload, length);
    message[length] = '\0';
    String cmdStr = String(message);
    Serial.printf("[MQTT] Command received on [%s]: %s\n", topic, cmdStr.c_str());

    // Basic JSON or key-value command parsing
    // Formats supported:
    // {"action":"OPEN","pin":"2580"}
    // {"action":"CLOSE","pin":"2580"}
    // {"action":"MODE_TOGGLE","pin":"2580"}
    // {"action":"STATUS"}

    String pin = "";
    int pinIdx = cmdStr.indexOf("\"pin\":\"");
    if (pinIdx != -1) {
        int pinEnd = cmdStr.indexOf("\"", pinIdx + 7);
        if (pinEnd != -1) pin = cmdStr.substring(pinIdx + 7, pinEnd);
    }

    String action = "";
    int actIdx = cmdStr.indexOf("\"action\":\"");
    if (actIdx != -1) {
        int actEnd = cmdStr.indexOf("\"", actIdx + 10);
        if (actEnd != -1) action = cmdStr.substring(actIdx + 10, actEnd);
    }

    String timeVal = "";
    int timeIdx = cmdStr.indexOf("\"time\":\"");
    if (timeIdx != -1) {
        int timeEnd = cmdStr.indexOf("\"", timeIdx + 8);
        if (timeEnd != -1) timeVal = cmdStr.substring(timeIdx + 8, timeEnd);
    }

    if (action == "STATUS") {
        publishStatusCloud();
        return;
    }

    // Security PIN Verification
    if (pin != userPIN) {
        Serial.println("[MQTT] Denied: Invalid PIN!");
        return;
    }

    if (action == "OPEN") {
        isAutoMode = false;
        openWindows();
        publishStatusCloud();
    } else if (action == "CLOSE") {
        isAutoMode = false;
        closeWindows();
        publishStatusCloud();
    } else if (action == "MODE_TOGGLE") {
        isAutoMode = !isAutoMode;
        publishStatusCloud();
    } else if (action == "AUTO_ON") {
        isAutoMode = true;
        publishStatusCloud();
    } else if (action == "SET_TIME") {
        timeVal.replace('.', ':');
        int colon = timeVal.indexOf(':');
        if (colon > 0) {
            clockHour = timeVal.substring(0, colon).toInt();
            clockMinute = timeVal.substring(colon + 1).toInt();
            clockSecond = 0;
            cacheIsNight = (clockHour >= 18 || clockHour < 6);
            if (cacheIsNight && isAutoMode && isWindowOpen) {
                closeWindows();
            }
            Serial.printf("[CLOCK] Time set via MQTT to %02d:%02d:00 (Night=%s)\n", clockHour, clockMinute, cacheIsNight ? "YES" : "NO");
            publishStatusCloud();
        }
    } else if (action == "SYSTEM_TOGGLE") {
        setSystemState(!isSystemEnabled);
        publishStatusCloud();
    } else if (action == "CHANGE_PIN") {
        String newPin = "";
        int newPinIdx = cmdStr.indexOf("\"newPin\":\"");
        if (newPinIdx != -1) {
            int newPinEnd = cmdStr.indexOf("\"", newPinIdx + 10);
            if (newPinEnd != -1) newPin = cmdStr.substring(newPinIdx + 10, newPinEnd);
        }
        if (newPin.length() == 4) {
            userPIN = newPin;
            saveCredentials();
            Serial.printf("[SECURITY] PIN changed via Cloud to: %s\n", userPIN.c_str());
            publishStatusCloud();
        }
    }
}

void publishStatusCloud() {
    if (!mqttClient.connected()) return;

    char payload[512];
    snprintf(payload, sizeof(payload),
        "{\"deviceId\":\"%s\",\"windowState\":\"%s\",\"currentAngle\":%d,\"mode\":\"%s\","
        "\"outsideTemp\":%.1f,\"insideTemp\":%.1f,\"outsideHumidity\":%.1f,\"insideHumidity\":%.1f,"
        "\"outsideLDR\":%d,\"insideLDR\":%d,\"rainRaw\":%d,\"isRaining\":%s,\"isNight\":%s,"
        "\"isOutsideDark\":%s,\"isInsideDark\":%s,"
        "\"clockTime\":\"%02d:%02d:%02d\",\"reason\":\"%s\",\"timerActive\":%s,\"systemEnabled\":%s}",
        deviceId.c_str(),
        isWindowOpen ? "OPEN" : "CLOSED",
        currentWindowAngle,
        isAutoMode ? "AUTO" : "MANUAL",
        cacheOutsideTemp, cacheInsideTemp,
        cacheOutsideHumidity, cacheInsideHumidity,
        cacheOutsideLDR, cacheInsideLDR, cacheRain,
        cacheIsRaining ? "true" : "false",
        cacheIsNight ? "true" : "false",
        cacheIsOutsideDark ? "true" : "false",
        cacheIsInsideDark ? "true" : "false",
        clockHour, clockMinute, clockSecond,
        pendingReason.c_str(),
        timerActive ? "true" : "false",
        isSystemEnabled ? "true" : "false"
    );

    mqttClient.publish(topicStatus.c_str(), payload);
    Serial.println("[MQTT] Telemetry published to Cloud.");
}

// ================= LOOP ============================
void loop() {
    unsigned long currentMillis = millis();

    // 0. Handle Physical Manual Window Switch (GPIO 25)
    handleManualSwitch();

    // 1. Maintain Real-Time Clock
    updateClock();

    // 2. Handle Web Server Requests
    server.handleClient();

    // 3. Handle Cloud MQTT Client
    if (WiFi.status() == WL_CONNECTED) {
        if (!mqttClient.connected()) {
            connectMQTT();
        } else {
            mqttClient.loop();
        }
    }

    // 4. Wi-Fi Disconnect Fail-Safe (If Home Wi-Fi configured and disconnected > 15s)
    if (homeSSID.length() > 0) {
        if (WiFi.status() != WL_CONNECTED) {
            if (wifiDisconnectStartTime == 0) {
                wifiDisconnectStartTime = currentMillis;
            } else if (currentMillis - wifiDisconnectStartTime >= 15000) {
                if (isWindowOpen) {
                    Serial.println("[SAFETY] Wi-Fi disconnected for > 15s! Closing windows immediately.");
                    closeWindows();
                }
                if (isSystemEnabled) {
                    Serial.println("[SAFETY] Disabling system due to Wi-Fi loss. Switched to System OFF.");
                    setSystemState(false);
                    pendingReason = "Safety Lock: Wi-Fi Disconnected. System OFF.";
                }
            }
        } else {
            wifiDisconnectStartTime = 0;
        }
    }

    // 5. Handle Serial Commands
    handleSerialCommands();

    // 6. Periodic Sensor Reading & Automation Logic
    if (currentMillis - lastSensorReadTime >= SENSOR_INTERVAL) {
        lastSensorReadTime = currentMillis;

        // Read Outside DHT11
        float outT = dhtOutside.readTemperature();
        float outH = dhtOutside.readHumidity();
        if (!isnan(outT)) cacheOutsideTemp = outT + outsideTempOffset;
        if (!isnan(outH)) cacheOutsideHumidity = outH;

        // Clean bus timing between the two DHT11 sensors
        delay(50);

        // Read Living Room (Inside) DHT11 with retry
        float inT  = dhtInside.readTemperature();
        float inH  = dhtInside.readHumidity();
        if (isnan(inT) || isnan(inH)) {
            delay(80);
            if (isnan(inT)) inT = dhtInside.readTemperature();
            if (isnan(inH)) inH = dhtInside.readHumidity();
        }

        if (!isnan(inT)) {
            cacheInsideTemp = inT + insideTempOffset;
        } else {
            Serial.println("[DHT] Warning: Inside (Living Room) DHT11 read failed! Check GPIO 26 wiring");
        }
        if (!isnan(inH)) cacheInsideHumidity = inH;

        bool outFail = isnan(cacheOutsideTemp) || (cacheOutsideTemp == 0.0f);
        bool inFail  = isnan(cacheInsideTemp)  || (cacheInsideTemp  == 0.0f);

        // Read Analog Sensors
        cacheOutsideLDR = analogRead(LDR_OUTSIDE_PIN);
        cacheInsideLDR  = analogRead(LDR_INSIDE_PIN);
        cacheRain       = analogRead(RAIN_SENSOR_PIN);

        cacheIsRaining     = (cacheRain < RAIN_THRESHOLD);
        cacheIsOutsideDark = (cacheOutsideLDR > LDR_DARK_THRESHOLD);
        cacheIsInsideDark  = (cacheInsideLDR > LDR_DARK_THRESHOLD);

        // Night Check: Clock-controlled (past 18:00 or before 6:00 is Night)
        bool isNightTime = (clockHour >= 18 || clockHour < 6);
        cacheIsNight = isNightTime;

        // Automation Evaluation (only if system is enabled)
        if (!isSystemEnabled) {
            timerActive = false;
            pendingReason = "System is OFF";
        } else if (isAutoMode) {
            WindowState targetState = isWindowOpen ? STATE_OPEN : STATE_CLOSED;
            String reason = "";

            if (cacheIsNight) {
                targetState = STATE_CLOSED;
                reason = "Night time (past 18:00) - Auto Open FORBIDDEN (Use Manual)";
            } else if (cacheIsRaining) {
                targetState = STATE_CLOSED;
                reason = "Rain Detected! Protecting interior";
            } else if (!outFail && !inFail && (cacheOutsideTemp - cacheInsideTemp >= TEMP_DIFF_THRESHOLD)) {
                targetState = STATE_CLOSED;
                reason = "Outside is hotter (>= 3.5 C) - Keeping heat out";
            } else if (!outFail && !inFail && (cacheInsideTemp - cacheOutsideTemp >= TEMP_DIFF_THRESHOLD)) {
                targetState = STATE_OPEN;
                reason = "Inside is hotter (>= 3.5 C) - Ventilating room";
            } else if (cacheIsOutsideDark && cacheIsInsideDark) {
                targetState = STATE_CLOSED;
                reason = "Daytime Heavy Cloud Cover / Dark Outside";
            } else {
                targetState = STATE_OPEN;
                reason = "Normal daytime condition";
            }

            // 5-Second Debounce Timer
            WindowState currentState = isWindowOpen ? STATE_OPEN : STATE_CLOSED;
            if (targetState != currentState) {
                if (!timerActive || pendingTargetState != targetState) {
                    timerActive = true;
                    pendingTargetState = targetState;
                    conditionStartTime = currentMillis;
                    pendingReason = reason;
                } else {
                    unsigned long elapsed = currentMillis - conditionStartTime;
                    if (elapsed >= CONFIRM_TIME_MS) {
                        if (pendingTargetState == STATE_OPEN) openWindows();
                        else closeWindows();
                        timerActive = false;
                        publishStatusCloud();
                    }
                }
            } else {
                if (timerActive) timerActive = false;
                pendingReason = reason;
            }
        } else {
            timerActive = false;
            pendingReason = "Manual Control Mode Active";
        }

        // Periodic Cloud Telemetry Publish (every 1 second for fast real-time responsiveness)
        if (currentMillis - lastCloudPublishTime >= 1000) {
            lastCloudPublishTime = currentMillis;
            publishStatusCloud();
        }
    }
}

// ================= WI-FI INITIALIZATION ============
void setupWiFi() {
    WiFi.setSleep(false); // CRITICAL: Keep Wi-Fi radio 100% active to prevent phone disconnects

    IPAddress local_IP(192, 168, 4, 1);
    IPAddress gateway(192, 168, 4, 1);
    IPAddress subnet(255, 255, 255, 0);
    WiFi.softAPConfig(local_IP, gateway, subnet);

    String broadcastApSSID = (apSSID == DEFAULT_AP_SSID) ? ("SmartWindow-" + deviceId.substring(3)) : apSSID;

    // Check if we have home router credentials saved
    if (homeSSID.length() > 0) {
        // Connected to home router: Run in pure STA mode!
        // Pure STA mode avoids all channel conflicts with home router!
        WiFi.mode(WIFI_STA);
        WiFi.setAutoReconnect(true);
        Serial.printf("[WIFI] Pure STA Mode: Connecting to Home Network: %s ...\n", homeSSID.c_str());
        WiFi.begin(homeSSID.c_str(), homePass.c_str());
    } else {
        // No home credentials: Pure AP Provisioning Mode
        // Channel 1, locked, STA disabled completely. Zero channel conflicts!
        WiFi.mode(WIFI_AP);
        WiFi.softAP(broadcastApSSID.c_str(), apPass.c_str(), 1, 0, 4);
        Serial.println("[WIFI] Pure AP Provisioning Mode Started: " + broadcastApSSID);
        Serial.print("[WIFI] AP IP Address: ");
        Serial.println(WiFi.softAPIP());
    }
}

// ================= WEB SERVER ROUTES ===============
void setupWebServer() {
    server.enableCORS(true);

    // 1. Embedded Web Dashboard
    server.on("/", HTTP_GET, []() {
        server.send(200, "text/html", INDEX_HTML);
    });

    // 2. Device Info API
    server.on("/api/device-info", HTTP_GET, []() {
        Serial.printf("[HTTP] GET /api/device-info from %s\n", server.client().remoteIP().toString().c_str());
        server.sendHeader("Access-Control-Allow-Origin", "*");
        server.sendHeader("Connection", "close");
        String json = "{";
        json += "\"deviceId\":\"" + deviceId + "\",";
        json += "\"apSSID\":\"" + apSSID + "\",";
        json += "\"staSSID\":\"" + homeSSID + "\",";
        json += "\"staConnected\":" + String(WiFi.status() == WL_CONNECTED ? "true" : "false") + ",";
        json += "\"staIP\":\"" + WiFi.localIP().toString() + "\",";
        json += "\"cloudConnected\":" + String(mqttClient.connected() ? "true" : "false") + ",";
        json += "\"mqttBroker\":\"" + String(MQTT_BROKER) + "\",";
        json += "\"topicStatus\":\"" + topicStatus + "\",";
        json += "\"topicControl\":\"" + topicControl + "\"";
        json += "}";
        server.send(200, "application/json", json);
    });

    // 3. Status API
    server.on("/api/status", HTTP_GET, []() {
        Serial.printf("[HTTP] GET /api/status from %s\n", server.client().remoteIP().toString().c_str());
        server.sendHeader("Access-Control-Allow-Origin", "*");
        server.sendHeader("Connection", "close");
        int remainSec = 0;
        if (timerActive) {
            long elapsed = millis() - conditionStartTime;
            remainSec = (CONFIRM_TIME_MS > elapsed) ? ((CONFIRM_TIME_MS - elapsed) / 1000) + 1 : 0;
        }

        String json = "{";
        json += "\"deviceId\":\"" + deviceId + "\",";
        json += "\"windowState\":\"" + String(isWindowOpen ? "OPEN" : "CLOSED") + "\",";
        json += "\"currentAngle\":" + String(currentWindowAngle) + ",";
        json += "\"mode\":\"" + String(isAutoMode ? "AUTO" : "MANUAL") + "\",";
        json += "\"outsideTemp\":" + String(cacheOutsideTemp, 2) + ",";
        json += "\"insideTemp\":" + String(cacheInsideTemp, 2) + ",";
        json += "\"outsideHumidity\":" + String(cacheOutsideHumidity, 1) + ",";
        json += "\"insideHumidity\":" + String(cacheInsideHumidity, 1) + ",";
        json += "\"outsideLDR\":" + String(cacheOutsideLDR) + ",";
        json += "\"insideLDR\":" + String(cacheInsideLDR) + ",";
        json += "\"rainRaw\":" + String(cacheRain) + ",";
        json += "\"isRaining\":" + String(cacheIsRaining ? "true" : "false") + ",";
        json += "\"isOutsideDark\":" + String(cacheIsOutsideDark ? "true" : "false") + ",";
        json += "\"isInsideDark\":" + String(cacheIsInsideDark ? "true" : "false") + ",";
        json += "\"isNight\":" + String(cacheIsNight ? "true" : "false") + ",";
        char clkBuf[16];
        snprintf(clkBuf, sizeof(clkBuf), "%02d:%02d:%02d", clockHour, clockMinute, clockSecond);
        json += "\"clockTime\":\"" + String(clkBuf) + "\",";
        json += "\"timerActive\":" + String(timerActive ? "true" : "false") + ",";
        json += "\"timerRemaining\":" + String(remainSec) + ",";
        json += "\"reason\":\"" + pendingReason + "\",";
        json += "\"systemEnabled\":" + String(isSystemEnabled ? "true" : "false") + ",";
        json += "\"cloudConnected\":" + String(mqttClient.connected() ? "true" : "false");
        json += "}";

        server.send(200, "application/json", json);
    });

    // 4. Wi-Fi Scanner (Fast scan with minimal disruption)
    server.on("/api/scan", HTTP_GET, []() {
        Serial.printf("[HTTP] GET /api/scan from %s\n", server.client().remoteIP().toString().c_str());
        server.sendHeader("Access-Control-Allow-Origin", "*");
        server.sendHeader("Connection", "close");
        if (WiFi.getMode() != WIFI_AP_STA && WiFi.getMode() != WIFI_STA) {
            WiFi.mode(WIFI_AP_STA);
        }
        int n = WiFi.scanNetworks(false, false, false, 60);
        String json = "[";
        for (int i = 0; i < n; ++i) {
            if (i > 0) json += ",";
            json += "{";
            json += "\"ssid\":\"" + WiFi.SSID(i) + "\",";
            json += "\"rssi\":" + String(WiFi.RSSI(i)) + ",";
            json += "\"channel\":" + String(WiFi.channel(i)) + ",";
            json += "\"secure\":" + String(WiFi.encryptionType(i) != WIFI_AUTH_OPEN ? "true" : "false");
            json += "}";
        }
        json += "]";
        WiFi.scanDelete();

        // If not connected to home Wi-Fi, restore pure AP mode so beacon stays locked on Channel 1
        if (homeSSID.length() == 0) {
            WiFi.mode(WIFI_AP);
        }

        server.send(200, "application/json", json);
    });

    // 5. Camera-Style Pairing API (Connect to Home Router & Cloud)
    server.on("/api/pair", HTTP_POST, []() {
        Serial.printf("[HTTP] POST /api/pair from %s\n", server.client().remoteIP().toString().c_str());
        server.sendHeader("Access-Control-Allow-Origin", "*");
        server.sendHeader("Connection", "close");
        String reqPin = server.arg("pin");
        if (reqPin != userPIN) {
            server.send(401, "application/json", "{\"success\":false,\"message\":\"Incorrect Security PIN\"}");
            return;
        }

        String ssid = server.arg("ssid");
        String pass = server.arg("pass");
        if (ssid.length() == 0) {
            server.send(400, "application/json", "{\"success\":false,\"message\":\"SSID cannot be empty\"}");
            return;
        }

        homeSSID = ssid;
        homePass = pass;
        saveCredentials();

        // Switch to AP_STA temporarily to connect to router while maintaining web server
        WiFi.mode(WIFI_AP_STA);
        WiFi.setAutoReconnect(true);
        WiFi.begin(homeSSID.c_str(), homePass.c_str());

        // Wait smoothly up to 4 seconds while keeping web server responsive
        int retry = 0;
        while (WiFi.status() != WL_CONNECTED && retry < 20) {
            delay(200);
            server.handleClient();
            retry++;
        }

        bool connected = (WiFi.status() == WL_CONNECTED);
        if (connected) {
            connectMQTT();
        }

        String resp = "{";
        resp += "\"success\":" + String(connected ? "true" : "false") + ",";
        resp += "\"deviceId\":\"" + deviceId + "\",";
        resp += "\"staIP\":\"" + WiFi.localIP().toString() + "\",";
        resp += "\"cloudConnected\":" + String(mqttClient.connected() ? "true" : "false") + ",";
        resp += "\"message\":\"" + String(connected ? "Successfully paired with router and registered to cloud!" : "Connecting in background...") + "\"";
        resp += "}";
        server.send(200, "application/json", resp);

        // Allow HTTP response to flush over the socket to the phone
        delay(400);

        // OPTION 1 FIX: If connected, disable AP completely and switch to pure STA mode!
        // This solves the AP+STA channel collision 100%!
        if (connected) {
            Serial.println("[WIFI] Switched to STA-only mode - channel conflict solved!");
            WiFi.mode(WIFI_STA);
        }
    });

    // 6. Control API
    server.on("/api/control", HTTP_POST, []() {
        Serial.printf("[HTTP] POST /api/control (action: %s) from %s\n", server.arg("action").c_str(), server.client().remoteIP().toString().c_str());
        server.sendHeader("Access-Control-Allow-Origin", "*");
        server.sendHeader("Connection", "close");
        if (millis() - lockoutStartTime < LOCKOUT_DURATION_MS) {
            long remainingLockout = (LOCKOUT_DURATION_MS - (millis() - lockoutStartTime)) / 1000;
            server.send(403, "application/json", "{\"success\":false,\"message\":\"System locked due to 5 failed PIN attempts! Try again in " + String(remainingLockout) + "s\"}");
            return;
        }

        String reqPin = server.arg("pin");
        if (reqPin != userPIN) {
            failedPinAttempts++;
            if (failedPinAttempts >= 5) {
                lockoutStartTime = millis();
                failedPinAttempts = 0;
                server.send(403, "application/json", "{\"success\":false,\"message\":\"Too many failed attempts! System locked for 60 seconds.\"}");
                return;
            }
            server.send(401, "application/json", "{\"success\":false,\"message\":\"Invalid PIN! Attempt " + String(failedPinAttempts) + "/5\"}");
            return;
        }
        failedPinAttempts = 0;

        String action = server.arg("action");
        if (action == "OPEN") {
            isAutoMode = false;
            openWindows();
            publishStatusCloud();
            server.send(200, "application/json", "{\"success\":true,\"message\":\"Windows opened manually.\"}");
        } else if (action == "CLOSE") {
            isAutoMode = false;
            closeWindows();
            publishStatusCloud();
            server.send(200, "application/json", "{\"success\":true,\"message\":\"Windows closed manually.\"}");
        } else if (action == "MODE_TOGGLE") {
            isAutoMode = !isAutoMode;
            publishStatusCloud();
            server.send(200, "application/json", "{\"success\":true,\"message\":\"Mode switched to " + String(isAutoMode ? "AUTO" : "MANUAL") + "\"}");
        } else if (action == "SYSTEM_TOGGLE") {
            setSystemState(!isSystemEnabled);
            publishStatusCloud();
            server.send(200, "application/json", "{\"success\":true,\"message\":\"System " + String(isSystemEnabled ? "ENABLED" : "DISABLED") + "\"}");
        } else if (action == "SET_TIME") {
            String t = server.arg("time");
            t.replace('.', ':');
            int colon = t.indexOf(':');
            if (colon > 0) {
                clockHour = t.substring(0, colon).toInt();
                clockMinute = t.substring(colon + 1).toInt();
                clockSecond = 0;
                cacheIsNight = (clockHour >= 18 || clockHour < 6);
                if (cacheIsNight && isAutoMode && isWindowOpen) {
                    closeWindows();
                }
                publishStatusCloud();
                server.send(200, "application/json", "{\"success\":true,\"message\":\"Clock updated to " + t + "\"}");
                return;
            }
            server.send(400, "application/json", "{\"success\":false,\"message\":\"Invalid time format\"}");
        } else {
            server.send(400, "application/json", "{\"success\":false,\"message\":\"Unknown action\"}");
        }
    });

    // 7. Security API (Change PIN & Change AP Password)
    server.on("/api/security", HTTP_POST, []() {
        String curPin = server.arg("pin");
        if (curPin != userPIN) {
            server.send(401, "application/json", "{\"success\":false,\"message\":\"Current PIN is incorrect!\"}");
            return;
        }

        String newPin = server.arg("new_pin");
        String newApPass = server.arg("new_ap_pass");
        String msg = "";

        if (newPin.length() == 4) {
            userPIN = newPin;
            msg += "PIN changed successfully. ";
        }

        if (newApPass.length() >= 8) {
            apPass = newApPass;
            WiFi.softAP(apSSID.c_str(), apPass.c_str());
            msg += "Hotspot password updated! Reconnect with new password.";
        }

        saveCredentials();
        server.send(200, "application/json", "{\"success\":true,\"message\":\"" + msg + "\"}");
    });

    server.begin();
    Serial.println("[HTTP] Web Server successfully started on port 80");
}

// ================= CREDENTIALS STORAGE (NVS) =======
void loadCredentials() {
    prefs.begin("smartwindow", false);
    apSSID   = prefs.getString("ap_ssid", DEFAULT_AP_SSID);
    apPass   = prefs.getString("ap_pass", DEFAULT_AP_PASS);
    userPIN  = prefs.getString("user_pin", DEFAULT_USER_PIN);
    homeSSID = prefs.getString("home_ssid", "");
    homePass = prefs.getString("home_pass", "");
    isSystemEnabled = prefs.getBool("sys_enabled", false);
    prefs.end();
}

void saveCredentials() {
    prefs.begin("smartwindow", false);
    prefs.putString("ap_ssid", apSSID);
    prefs.putString("ap_pass", apPass);
    prefs.putString("user_pin", userPIN);
    prefs.putString("home_ssid", homeSSID);
    prefs.putString("home_pass", homePass);
    prefs.putBool("sys_enabled", isSystemEnabled);
    prefs.end();
}

void factoryReset() {
    Serial.println("\n[RESET] !!! FACTORY RESET TRIGGERED !!!");
    prefs.begin("smartwindow", false);
    prefs.clear();
    prefs.end();

    apSSID   = DEFAULT_AP_SSID;
    apPass   = DEFAULT_AP_PASS;
    userPIN  = DEFAULT_USER_PIN;
    homeSSID = "";
    homePass = "";

    Serial.println("[RESET] Restored to factory defaults. Restarting ESP32...");
    delay(1000);
    ESP.restart();
}

// ================= SERVO MOVEMENTS =================
void openWindows() {
    Serial.println("[ACTION] Opening Windows smoothly...");
    if (!windowServo1.attached()) windowServo1.attach(SERVO1_PIN, 500, 2400);
    if (!windowServo2.attached()) windowServo2.attach(SERVO2_PIN, 500, 2400);
    moveServosSmoothly(WINDOW_OPEN_POS);
    isWindowOpen = true;
    Serial.println("[ACTION] Windows are now OPEN (180 deg).");
}

void closeWindows() {
    Serial.println("[ACTION] Closing Windows smoothly...");
    if (!windowServo1.attached()) windowServo1.attach(SERVO1_PIN, 500, 2400);
    if (!windowServo2.attached()) windowServo2.attach(SERVO2_PIN, 500, 2400);
    moveServosSmoothly(WINDOW_CLOSED_POS);
    isWindowOpen = false;
    Serial.println("[ACTION] Windows are now CLOSED (0 deg).");
}

void moveServosSmoothly(int targetAngle) {
    if (targetAngle > currentWindowAngle) {
        for (int pos = currentWindowAngle; pos <= targetAngle; pos++) {
            windowServo1.write(pos);
            windowServo2.write(pos);
            delay(15);
        }
    } else if (targetAngle < currentWindowAngle) {
        for (int pos = currentWindowAngle; pos >= targetAngle; pos--) {
            windowServo1.write(pos);
            windowServo2.write(pos);
            delay(15);
        }
    }
    currentWindowAngle = targetAngle;
    windowServo1.write(targetAngle);
    windowServo2.write(targetAngle);
}

// ================= SYSTEM STATE (PERSISTENT) ========
void setSystemState(bool enabled) {
    isSystemEnabled = enabled;
    prefs.begin("smartwindow", false);
    prefs.putBool("sys_enabled", isSystemEnabled);
    prefs.end();
    if (!isSystemEnabled) {
        timerActive = false;
        pendingReason = "System is OFF (Automation Paused)";
    } else {
        pendingReason = "System is ON (Automation Active)";
    }
    Serial.printf("[SYSTEM] State changed: %s (Saved to Flash)\n", isSystemEnabled ? "ON" : "OFF");
}

// ================= PHYSICAL MANUAL WINDOW SWITCH ====
void handleManualSwitch() {
    int reading = digitalRead(MANUAL_SWITCH_PIN);
    static int lastSwitchReading = HIGH;
    static unsigned long lastDebounceTime = 0;
    static int switchState = HIGH;

    if (reading != lastSwitchReading) {
        lastDebounceTime = millis();
    }

    if ((millis() - lastDebounceTime) > 50) {
        if (reading != switchState) {
            switchState = reading;
            if (switchState == LOW) { // Button pressed / switch closed to GND
                Serial.println("\n[SWITCH] Physical Manual Switch Activated!");
                isAutoMode = false; // Always switch to MANUAL mode
                if (isWindowOpen) {
                    closeWindows();
                    Serial.println("[SWITCH] Window CLOSED manually via switch (Switched to MANUAL)");
                } else {
                    openWindows();
                    Serial.println("[SWITCH] Window OPENED manually via switch (Switched to MANUAL)");
                }
                publishStatusCloud();
            }
        }
    }
    lastSwitchReading = reading;
}

// ================= REAL-TIME CLOCK =================
void updateClock() {
    unsigned long currentMillis = millis();
    if (currentMillis - lastClockTick >= 1000) {
        unsigned long secondsPassed = (currentMillis - lastClockTick) / 1000;
        lastClockTick += secondsPassed * 1000;

        clockSecond += secondsPassed;
        while (clockSecond >= 60) {
            clockSecond -= 60;
            clockMinute++;
            if (clockMinute >= 60) {
                clockMinute = 0;
                clockHour++;
                if (clockHour >= 24) clockHour = 0;
            }
        }
    }
}

// ================= SERIAL COMMANDS =================
void handleSerialCommands() {
    if (Serial.available() > 0) {
        String input = Serial.readStringUntil('\n');
        input.trim();

        // Ignore short/garbage input (less than 3 printable chars)
        if (input.length() < 3) return;

        // Check all chars are printable ASCII (ignore binary/garbage data)
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c < 32 || c > 126) return; // Non-printable char = garbage, ignore
        }

        input.toUpperCase();

        if (input.startsWith("SET ") || input.startsWith("TIME ")) {
            int spaceIdx = input.indexOf(' ');
            String timePart = input.substring(spaceIdx + 1);
            int colonIdx = timePart.indexOf(':');
            if (colonIdx > 0) {
                int h = timePart.substring(0, colonIdx).toInt();
                int m = timePart.substring(colonIdx + 1).toInt();
                if (h >= 0 && h < 24 && m >= 0 && m < 60) {
                    clockHour = h;
                    clockMinute = m;
                    clockSecond = 0;
                    Serial.printf("\n>>> [CLOCK UPDATED] Time set to %02d:%02d:00\n\n", clockHour, clockMinute);
                    return;
                }
            }
            Serial.println("\n[!] Use: SET 18:30\n");
        } else if (input == "MODE") {
            isAutoMode = !isAutoMode;
            publishStatusCloud();
            Serial.printf("\n>>> Switched to %s MODE\n\n", isAutoMode ? "AUTO" : "MANUAL");
        } else if (input == "RESET NOW") {
            // Require "RESET NOW" (not just "RESET") to prevent accidental factory reset
            // from Serial Monitor garbage data on connect/disconnect
            factoryReset();
        } else if (input == "RESET") {
            Serial.println("\n[!] To factory reset, type: RESET NOW\n");
        }
    }
}