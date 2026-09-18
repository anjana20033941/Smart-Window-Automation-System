package com.example.smartwindow.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.smartwindow.data.PairingResponse
import com.example.smartwindow.data.WifiNetwork
import com.example.smartwindow.network.WifiAutoConnector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSetupWizard(
    isScanning: Boolean,
    wifiNetworks: List<WifiNetwork>,
    onScanWifi: () -> Unit,
    onPairDevice: (ssid: String, pass: String, pin: String, onResult: (PairingResponse) -> Unit) -> Unit,
    onPairSuccess: (deviceId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val wifiAutoConnector = remember { WifiAutoConnector(context) }

    var currentStep by remember { mutableStateOf(1) }
    var selectedSsid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var securityPin by remember { mutableStateOf("2580") }
    var isPairingInProgress by remember { mutableStateOf(false) }
    var isAutoConnecting by remember { mutableStateOf(false) }
    var pairingResult by remember { mutableStateOf<PairingResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Runtime Permission Launcher for Modern Android (13, 14, 15, 16+)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val nearbyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.NEARBY_WIFI_DEVICES] ?: false
        } else true

        if (fineLocationGranted || nearbyGranted) {
            onScanWifi()
        }
    }

    LaunchedEffect(Unit) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
        permissionLauncher.launch(permissions)
    }

    DisposableEffect(Unit) {
        onDispose {
            wifiAutoConnector.release()
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isPairingInProgress && !isAutoConnecting) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)), // Clean Pure White
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header with Step Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Iris Setup Wizard",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Smart Window Cloud Provisioning",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFFE0F2FE))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Step $currentStep of 4",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0284C7)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color(0xFFE2E8F0))

                // Content by Step
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (currentStep) {
                        1 -> Step1ConnectHotspotLight(
                            isAutoConnecting = isAutoConnecting,
                            onAutoConnect = {
                                isAutoConnecting = true
                                errorMessage = null
                                wifiAutoConnector.connectToSmartWindow(
                                    onConnected = {
                                        isAutoConnecting = false
                                        currentStep = 2
                                        onScanWifi()
                                    },
                                    onError = { err ->
                                        isAutoConnecting = false
                                        errorMessage = err
                                    }
                                )
                            },
                            onManualNext = {
                                currentStep = 2
                                onScanWifi()
                            },
                            error = errorMessage
                        )
                        2 -> Step2SelectWifiLight(
                            isScanning = isScanning,
                            networks = wifiNetworks,
                            onRefresh = onScanWifi,
                            onSelectSsid = { ssid ->
                                selectedSsid = ssid
                                currentStep = 3
                            }
                        )
                        3 -> Step3EnterPasswordLight(
                            ssid = selectedSsid,
                            password = wifiPassword,
                            onPasswordChange = { wifiPassword = it },
                            passwordVisible = passwordVisible,
                            onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
                            pin = securityPin,
                            onPinChange = { if (it.length <= 4) securityPin = it },
                            isPairing = isPairingInProgress,
                            error = errorMessage,
                            onBack = { currentStep = 2 },
                            onStartPair = {
                                isPairingInProgress = true
                                errorMessage = null
                                onPairDevice(selectedSsid, wifiPassword, securityPin) { resp ->
                                    isPairingInProgress = false
                                    pairingResult = resp
                                    if (resp.success) {
                                        currentStep = 4
                                    } else {
                                        errorMessage = resp.message
                                    }
                                }
                            }
                        )
                        4 -> Step4PairingCompleteLight(
                            response = pairingResult,
                            onFinish = {
                                val devId = pairingResult?.deviceId ?: "SW-686E40"
                                wifiAutoConnector.release()
                                onPairSuccess(devId)
                                onDismiss()
                            }
                        )
                    }
                }

                // Cancel Button at bottom
                if (currentStep < 4 && !isPairingInProgress && !isAutoConnecting) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Cancel Setup", color = Color(0xFF64748B))
                    }
                }
            }
        }
    }
}

@Composable
fun Step1ConnectHotspotLight(
    isAutoConnecting: Boolean,
    onAutoConnect: () -> Unit,
    onManualNext: () -> Unit,
    error: String?
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "1. Connect Phone to Window Wi-Fi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )

            Text(
                text = "To send your home Wi-Fi details, connect to the ESP32 setup network. Tap below to automatically connect without disconnecting:",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF475569)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Device Hotspot:", color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                        Text("SmartWindow-686E40", fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Default Password:", color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                        Text("12345678", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    }
                }
            }

            if (error != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = error,
                        color = Color(0xFFDC2626),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (isAutoConnecting) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = Color(0xFF0284C7), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Searching & Binding SmartWindow Wi-Fi...", color = Color(0xFF0284C7), fontWeight = FontWeight.SemiBold)
                }
            } else {
                // 1-Click Auto Connect Button
                Button(
                    onClick = onAutoConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("⚡ Auto-Connect to SmartWindow", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                // Manual Next Option
                OutlinedButton(
                    onClick = onManualNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Already Connected Manually ➔", color = Color(0xFF475569))
                }
            }
        }
    }
}

@Composable
fun Step2SelectWifiLight(
    isScanning: Boolean,
    networks: List<WifiNetwork>,
    onRefresh: () -> Unit,
    onSelectSsid: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "2. Select Home 2.4GHz Wi-Fi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            IconButton(onClick = onRefresh, enabled = !isScanning) {
                Text(if (isScanning) "⏳" else "🔄", fontSize = 18.sp)
            }
        }

        if (isScanning) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF0284C7))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Scanning nearby 2.4GHz networks...", color = Color(0xFF64748B))
                }
            }
        } else if (networks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No networks found or not connected to ESP32 hotspot.", color = Color(0xFF64748B), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(onClick = onRefresh, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))) {
                        Text("Scan Again")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(networks) { net ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSsid(net.ssid) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("📶", fontSize = 20.sp)
                                Text(net.ssid, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                            }
                            Text(
                                text = "${net.rssi} dBm",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Step3EnterPasswordLight(
    ssid: String,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    pin: String,
    onPinChange: (String) -> Unit,
    isPairing: Boolean,
    error: String?,
    onBack: () -> Unit,
    onStartPair: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "3. Enter Router Password & PIN",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2FE)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("📡", fontSize = 22.sp)
                    Column {
                        Text("Selected Network", style = MaterialTheme.typography.labelSmall, color = Color(0xFF0369A1))
                        Text(ssid, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                    }
                }
            }

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Wi-Fi Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onTogglePasswordVisibility) {
                        Text(if (passwordVisible) "👁️" else "🙈")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = pin,
                onValueChange = onPinChange,
                label = { Text("Device Security Master PIN") },
                supportingText = { Text("4-digit security PIN") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            if (error != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = error,
                        color = Color(0xFFDC2626),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (isPairing) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(color = Color(0xFF0284C7))
                Text(
                    "Connecting ESP32 to Router & Cloud...",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF0F172A)
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Back", color = Color(0xFF475569))
                }
                Button(
                    onClick = onStartPair,
                    enabled = ssid.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    modifier = Modifier
                        .weight(2f)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Connect to Cloud 🚀", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun Step4PairingCompleteLight(
    response: PairingResponse?,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(top = 20.dp)
        ) {
            Text("🎉", fontSize = 54.sp)
            Text(
                text = "Pairing Successful!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0284C7)
            )

            Text(
                text = "Iris is now connected to your home router. You can control your window from anywhere in the world over 4G / 5G / Wi-Fi!",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF475569)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Device ID:", color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                        Text(response?.deviceId ?: "SW-686E40", fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Cloud Status:", color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                        Text("🟢 ONLINE", fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                    }
                }
            }
        }

        Button(
            onClick = onFinish,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Go to Iris Dashboard 🚀", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
