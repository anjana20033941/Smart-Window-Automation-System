# 🪟 Project Iris — Smart Window Automation System

[![Platform](https://img.shields.io/badge/Platform-ESP32-blue.svg)](https://espressif.com)
[![Framework](https://img.shields.io/badge/Framework-Arduino%20%2F%20PlatformIO-orange.svg)](https://platformio.org)
[![Android](https://img.shields.io/badge/Android-Jetpack%20Compose-green.svg)](https://developer.android.com/jetpack/compose)
[![MQTT](https://img.shields.io/badge/MQTT-HiveMQ%20Cloud-yellow.svg)](https://www.hivemq.com)
[![License](https://img.shields.io/badge/License-MIT-purple.svg)](LICENSE)

An intelligent, cloud-connected, dual-mode Smart Window Automation System engineered with **ESP32**, **HiveMQ MQTT**, and a modern **Android (Jetpack Compose)** mobile companion application (**Iris**).

---

## 📲 Download Iris Mobile App

You can download and install the latest **Iris.apk** directly to your Android device by scanning the QR code below or clicking the direct download button:

<div align="center">
  <img src="assets/Iris_App_Download_QR.png" alt="Iris App Download QR Code" width="260"/>
  <br/>
  <br/>
  <a href="https://github.com/anjana20033941/Smart-Window-Automation-System/raw/main/apk/Iris.apk">
    <img src="https://img.shields.io/badge/Direct%20Download-Iris.apk%20(15.8%20MB)-0284C7?style=for-the-badge&logo=android&logoColor=white" alt="Download APK" />
  </a>
</div>

> **Quick Install Instructions:**
> 1. Scan the QR code above with your mobile phone camera or click the **Direct Download** button.
> 2. Open the downloaded `Iris.apk` file on your Android phone and tap **Install**.
> 3. Grant Notification permissions when prompted to receive real-time alerts.

---

## ✨ Key Features

- **🌧️ High-Sensitivity Rain Protection:** Rain sensor (< 3400 threshold) with a 3.5-second verification debounce automatically closes windows to protect interior spaces.
- **🌡️ Automated Temperature Differential Ventilation:** Monitors inside and outside temperatures. If inside is hotter by ≥ 3.5°C during daytime, windows open automatically for natural breeze and ventilation.
- **🌙 Real-Time Clock & Night Lock:** Automatically prevents auto-opening during night hours (18:00 – 06:00).
- **🛡️ Wi-Fi Disconnect & Power Cut Safety Lock:** If Wi-Fi connection is lost for > 15s, or when power is restored after a blackout, windows safely close and lock into **System OFF** mode until the user explicitly turns them back ON.
- **🔘 Physical Manual Window Switch (GPIO 25):** Hardware push button / toggle switch directly opens or closes the window, instantly switching mode to `MANUAL` and updating the Cloud app.
- **🔔 Android Push Notifications:** Native system notifications for rain detection, Wi-Fi/power disconnect, window state transitions, and temperature alerts, fully customizable with toggles in Settings.
- **🔐 Master PIN Security:** 4-digit master security PIN required for window actuation and mode switching, easily configurable from the app.
- **⚡ Dual Connectivity:** Works via direct **Local Wi-Fi (192.168.4.1)** and over global internet via **HiveMQ Cloud MQTT**.

---

## 🔌 Hardware Pinout & Wiring

| Component | Pin / Signal | ESP32 GPIO | Description |
|---|---|---|---|
| **Outside DHT11** | DATA | `GPIO 27` | Outside Environment Temp & Humidity |
| **Inside DHT11** | DATA | `GPIO 26` | Living Room Temp & Humidity |
| **Outside LDR** | Analog A0 | `GPIO 32` (ADC1) | Ambient Daylight Detection |
| **Inside LDR** | Analog A0 | `GPIO 33` (ADC1) | Indoor Lighting Detection |
| **Rain Sensor** | Analog A0 | `GPIO 36` / VP (ADC1) | Rain Drops Moisture Detection |
| **Window Servo 1** | PWM Signal | `GPIO 18` | Window Axis 1 (0° Closed, 180° Open) |
| **Window Servo 2** | PWM Signal | `GPIO 19` | Window Axis 2 (0° Closed, 180° Open) |
| **Manual Switch** | Signal | `GPIO 25` | Connect between GPIO 25 and GND |

---

## 📁 Repository Structure

```
Smart-Window-Automation-System/
├── firmware/                  # ESP32 PlatformIO Source Code
│   ├── src/main.cpp           # Complete firmware logic, sensors, MQTT, WebServer
│   └── platformio.ini         # PlatformIO build configuration
├── android_app/               # Android Native App (Jetpack Compose)
│   ├── app/src/main/          # Kotlin UI screens, ViewModel, MQTT, Notifications
│   └── build.gradle.kts       # Android Gradle build scripts
├── apk/                       # Pre-compiled Android Application Package
│   └── Iris.apk               # Ready-to-install Android APK
├── assets/                    # Project Media, Logo & QR Code
│   ├── Iris_App_Download_QR.png
│   └── app_logo.png
└── README.md                  # Project documentation & guides
```

---

## 👨‍💻 Author

Developed for **Smart Window Automation System**  
GitHub: [@anjana20033941](https://github.com/anjana20033941)
