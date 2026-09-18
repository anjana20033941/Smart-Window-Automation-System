# 🪟 Smart Window Automation System

An ESP32-based IoT system that automatically opens and closes windows based on outside/inside temperature, light levels, and rain detection — with a built-in Wi-Fi web dashboard for manual override.

## 📋 Overview

This project uses an ESP32 microcontroller to monitor environmental conditions and control two servo-driven windows automatically. It switches between **AUTO** mode (sensor-driven decisions) and **MANUAL** mode (control via web dashboard), toggled by a physical push button. A password-protected local web dashboard displays live sensor readings and lets you manually open/close the windows.

## ⚙️ Features

- 🌡️ Dual temperature sensing (outside + inside) using DHT11 sensors
- ☀️ Dual light sensing (outside + inside) using LDR modules
- 🌧️ Rain detection with automatic window closing
- 🔁 Auto / Manual mode toggle via physical switch
- 📶 Built-in Wi-Fi Access Point with live web dashboard
- 🔒 PIN-protected manual controls (OPEN / CLOSE / MODE TOGGLE)
- 🔧 Dual servo motor control (synchronized, 0°–180°)

## 🧠 Automation Logic (Priority Order)

| Priority | Condition | Action |
|---|---|---|
| 1 | Night (outside dark) | Close |
| 2 | Rain detected | Close |
| 3 | Outside temp > Inside temp | Close |
| 4 | Inside temp > Outside temp | Open |
| 5 | Daytime & inside dark | Open |
| 6 | Default (normal day) | Open |

## 🔌 Wiring / Pin Configuration

| Component | Pin | ESP32 GPIO |
|---|---|---|
| Outside DHT11 (Temperature) | Data | GPIO 27 |
| Inside DHT11 (Temperature) | Data | GPIO 26 |
| Outside LDR Module | AO | GPIO 32 |
| Inside LDR Module | AO | GPIO 33 |
| Rain Sensor Module | AO | GPIO 36 (labeled "SP"/"VP" on some boards) |
| Servo Motor 1 | Signal | GPIO 18 |
| Servo Motor 2 | Signal | GPIO 19 |
| Mode Switch | Signal | GPIO 25 (INPUT_PULLUP) |

All sensor VCC pins → 3V3, all GND pins → GND. Servos recommended on a separate 5V supply for stable power.

## 🛠️ Hardware Required

- ESP32 Dev Board
- 2× DHT11 Temperature Sensors
- 2× LDR Light Sensor Modules
- 1× Rain Sensor Module
- 2× Servo Motors (SG90 or similar)
- 1× Push Button / Switch
- Jumper wires, breadboard

## 💻 Software / Libraries

Built with **PlatformIO** (Arduino framework):

```ini
[env:esp32dev]
platform = espressif32
board = esp32dev
framework = arduino
monitor_speed = 115200

lib_deps =
    madhephaestus/ESP32Servo @ ^3.0.5
    adafruit/DHT sensor library @ ^1.4.7
    adafruit/Adafruit Unified Sensor @ ^1.1.14
```

## 🚀 Setup & Usage

1. Wire the components as per the table above.
2. Open the project in PlatformIO (VS Code extension).
3. Upload the code to the ESP32.
4. On boot, the ESP32 creates a Wi-Fi Access Point named **`SMART_WINDOW`** (password: `password123`).
5. Connect to it and visit `192.168.4.1` in a browser to view the dashboard.
6. Use the physical switch to toggle between AUTO and MANUAL mode.
7. In MANUAL mode, use the dashboard (PIN: `2580`) to control the windows directly.

## 📷 Demo / Screenshots

*(Add photos or a short video of your working setup here)*

## 🎓 Project Info

Built as an IoT / embedded systems mini-project demonstrating sensor fusion, automated decision logic, and an embedded web server on ESP32.

