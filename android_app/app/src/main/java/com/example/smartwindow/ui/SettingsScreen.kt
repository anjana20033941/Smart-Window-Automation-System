package com.example.smartwindow.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartwindow.data.SmartWindowStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    status: SmartWindowStatus,
    deviceId: String,
    isAutoMode: Boolean,
    isCloudMode: Boolean,
    savedPin: String = "2580",
    notificationsEnabled: Boolean = true,
    notifyRain: Boolean = true,
    notifyDisconnect: Boolean = true,
    notifyWindowMove: Boolean = true,
    notifyTempDiff: Boolean = false,
    onUpdateNotificationPref: (key: String, value: Boolean) -> Unit = { _, _ -> },
    onToggleAutoMode: (pin: String) -> Unit,
    onManualWindowAction: (action: String, pin: String) -> Unit,
    onChangePin: (curPin: String, newPin: String) -> Unit,
    onChangeApPassword: (curPin: String, newPass: String) -> Unit,
    onSetTime: (time: String) -> Unit = {},
    onReRunSetup: () -> Unit,
    onToggleConnectionMode: () -> Unit
) {
    val scrollState = rememberScrollState()

    // PIN dialog state
    var showPinDialogForMode by remember { mutableStateOf(false) }
    var pinForMode by remember { mutableStateOf("") }

    // Change PIN State
    var curPinForPinChange by remember { mutableStateOf("") }
    var newPinInput by remember { mutableStateOf("") }

    // Change AP Pass State
    var curPinForApPass by remember { mutableStateOf("") }
    var newApPassInput by remember { mutableStateOf("") }

    // Manual Time Input State
    var manualTimeInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Operation Mode (Auto vs Manual)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Operation Mode",
                            color = Color(0xFF0F172A),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isAutoMode) "Automatic Weather Control Active" else "Manual Control Active",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    }

                    Switch(
                        checked = isAutoMode,
                        onCheckedChange = { showPinDialogForMode = true },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0284C7)
                        )
                    )
                }

                // If in Manual Mode, show direct Manual Open/Close buttons!
                if (!isAutoMode) {
                    HorizontalDivider(color = Color(0xFFE2E8F0), modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Manual Window Controls:",
                        color = Color(0xFF0284C7),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onManualWindowAction("OPEN", savedPin) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🔓 Open (180°)", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { onManualWindowAction("CLOSE", savedPin) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🔒 Close (0°)", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Section: Real-Time Clock & Manual Time Setting
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Window Clock & Time Setting",
                    color = Color(0xFF0F172A),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Controls Night Mode (past 18:00 is Night Mode). Current Clock: ${status.time}",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp
                )

                // Real-Time Clock & Night Status Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Current Clock: ${status.time}",
                        color = Color(0xFF0F172A),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (status.isNight) "🌙 NIGHT MODE (Auto Closed)" else "☀️ DAY MODE (Auto Active)",
                        color = if (status.isNight) Color(0xFF6366F1) else Color(0xFF0284C7),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Sync Current Phone Time Button (One-tap!)
                Button(
                    onClick = {
                        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        val phoneTime = sdf.format(java.util.Date())
                        onSetTime(phoneTime)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("⏱️ Sync Phone Time to Window", color = Color.White, fontWeight = FontWeight.Bold)
                }

                // Quick Presets: Day (14:00) vs Night (20:00)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSetTime("14:00") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("☀️ Day (14:00)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = { onSetTime("20:00") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🌙 Night (20:00)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Manual Time Input (accepts both 17:00 and 17.00)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualTimeInput,
                        onValueChange = { manualTimeInput = it },
                        label = { Text("Manual Time (e.g. 14:00 or 17.00)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    val isValidTimeInput = manualTimeInput.contains(":") || manualTimeInput.contains(".")
                    Button(
                        onClick = {
                            if (isValidTimeInput) {
                                onSetTime(manualTimeInput.trim().replace('.', ':'))
                                manualTimeInput = ""
                            }
                        },
                        enabled = isValidTimeInput,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Set", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Section: Notification Preferences
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🔔 Push Notifications",
                            color = Color(0xFF0F172A),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Receive alerts on your phone screen",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { onUpdateNotificationPref("notif_enabled", it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0284C7)
                        )
                    )
                }

                if (notificationsEnabled) {
                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    // 1. Rain Alert Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🌧️ Rain Detection Alert", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                            Text("Notify when rain is detected and window closes", fontSize = 11.sp, color = Color(0xFF64748B))
                        }
                        Switch(
                            checked = notifyRain,
                            onCheckedChange = { onUpdateNotificationPref("notify_rain", it) }
                        )
                    }

                    // 2. Disconnect Alert Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("⚠️ Device Disconnect Alert", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                            Text("Notify if ESP32 loses Wi-Fi or electricity", fontSize = 11.sp, color = Color(0xFF64748B))
                        }
                        Switch(
                            checked = notifyDisconnect,
                            onCheckedChange = { onUpdateNotificationPref("notify_disconnect", it) }
                        )
                    }

                    // 3. Window Movement Alert Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🪟 Window Open/Close Alert", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                            Text("Notify whenever the window opens or closes", fontSize = 11.sp, color = Color(0xFF64748B))
                        }
                        Switch(
                            checked = notifyWindowMove,
                            onCheckedChange = { onUpdateNotificationPref("notify_window_move", it) }
                        )
                    }

                    // 4. Temp Diff Alert Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🌡️ Temperature Difference Alert", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                            Text("Notify when temperature difference exceeds 3.5°C", fontSize = 11.sp, color = Color(0xFF64748B))
                        }
                        Switch(
                            checked = notifyTempDiff,
                            onCheckedChange = { onUpdateNotificationPref("notify_temp_diff", it) }
                        )
                    }
                }
            }
        }

        // Section 2: Change 4-Digit Security Master PIN
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Change Security Master PIN",
                    color = Color(0xFF0F172A),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Used to authorize window open/close and mode changes.",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp
                )

                OutlinedTextField(
                    value = curPinForPinChange,
                    onValueChange = { if (it.length <= 4) curPinForPinChange = it },
                    label = { Text("Current 4-Digit PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = newPinInput,
                    onValueChange = { if (it.length <= 4) newPinInput = it },
                    label = { Text("New 4-Digit PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        onChangePin(curPinForPinChange, newPinInput)
                        curPinForPinChange = ""
                        newPinInput = ""
                    },
                    enabled = curPinForPinChange.length == 4 && newPinInput.length == 4,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Update Master PIN", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Section 3: Change ESP32 Hotspot Password
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Change Wi-Fi Hotspot Password",
                    color = Color(0xFF0F172A),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Changes the password of the ESP32 Wi-Fi (Default: 12345678).",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp
                )

                OutlinedTextField(
                    value = curPinForApPass,
                    onValueChange = { if (it.length <= 4) curPinForApPass = it },
                    label = { Text("Security Master PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = newApPassInput,
                    onValueChange = { newApPassInput = it },
                    label = { Text("New Hotspot Password (min 8 chars)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        onChangeApPassword(curPinForApPass, newApPassInput)
                        curPinForApPass = ""
                        newApPassInput = ""
                    },
                    enabled = curPinForApPass.length == 4 && newApPassInput.length >= 8,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Update Hotspot Password", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Section 4: Network & Setup Re-run
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Network & Cloud Setup",
                    color = Color(0xFF0F172A),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Connected Device: $deviceId",
                    color = Color(0xFF0284C7),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                OutlinedButton(
                    onClick = onReRunSetup,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔄 Re-run Wi-Fi Setup Wizard", color = Color(0xFF0F172A), fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick = onToggleConnectionMode,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCloudMode) Color(0xFF16A34A) else Color(0xFF0284C7)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isCloudMode) "🌐 Current: Cloud (4G/5G) Mode" else "📡 Current: Direct Local Wi-Fi Mode",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    // Modal to verify PIN when toggling Auto Mode
    if (showPinDialogForMode) {
        AlertDialog(
            onDismissRequest = { showPinDialogForMode = false },
            containerColor = Color(0xFFFFFFFF),
            title = { Text("Authorize Mode Change", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter your 4-digit PIN to toggle mode:", color = Color(0xFF64748B), fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pinForMode,
                        onValueChange = { if (it.length <= 4) pinForMode = it },
                        label = { Text("Security Master PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onToggleAutoMode(pinForMode)
                        showPinDialogForMode = false
                        pinForMode = ""
                    },
                    enabled = pinForMode.length == 4,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Text("Authorize")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialogForMode = false }) { Text("Cancel", color = Color(0xFF64748B)) }
            }
        )
    }
}
