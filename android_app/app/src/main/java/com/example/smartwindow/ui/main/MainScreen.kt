package com.example.smartwindow.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.smartwindow.data.SmartWindowStatus
import com.example.smartwindow.ui.DeviceSetupWizard
import com.example.smartwindow.ui.SensorsScreen
import com.example.smartwindow.ui.SettingsScreen
import com.example.smartwindow.ui.SplashScreen
import com.example.smartwindow.ui.components.AnimatedWindowView
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    var isSplashDone by remember { mutableStateOf(false) }

    if (!isSplashDone) {
        SplashScreen(onLoaded = { isSplashDone = true })
        return
    }

    val status by viewModel.status.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val isSetupCompleted by viewModel.isSetupCompleted.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isCloudMode by viewModel.isCloudMode.collectAsStateWithLifecycle()
    val isCloudConnected by viewModel.isCloudConnected.collectAsStateWithLifecycle()
    val activeDeviceId by viewModel.deviceId.collectAsStateWithLifecycle()
    val wifiNetworks by viewModel.wifiNetworks.collectAsStateWithLifecycle()
    val isScanningWifi by viewModel.isScanningWifi.collectAsStateWithLifecycle()
    val savedPin by viewModel.savedPin.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val notifyRain by viewModel.notifyRain.collectAsStateWithLifecycle()
    val notifyDisconnect by viewModel.notifyDisconnect.collectAsStateWithLifecycle()
    val notifyWindowMove by viewModel.notifyWindowMove.collectAsStateWithLifecycle()
    val notifyTempDiff by viewModel.notifyTempDiff.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Notification Permission Request (Android 13+ / API 33+)
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Notification permission callback */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Dialog Visibility States
    var showPinDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<String?>(null) }
    var showSetupWizardManually by remember { mutableStateOf(false) }

    // Toast feedback
    LaunchedEffect(userMessage) {
        userMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    // One-Time Setup Wizard: Shown automatically on 1st launch if not completed
    val showWizard = (!isSetupCompleted || showSetupWizardManually)

    if (showWizard) {
        DeviceSetupWizard(
            isScanning = isScanningWifi,
            wifiNetworks = wifiNetworks,
            onScanWifi = { viewModel.scanWifi() },
            onPairDevice = { ssid, pass, pin, onResult ->
                viewModel.pairDevice(ssid, pass, pin, onResult)
            },
            onPairSuccess = { devId ->
                viewModel.completeSetup(devId)
                showSetupWizardManually = false
            },
            onDismiss = {
                viewModel.completeSetup(activeDeviceId)
                showSetupWizardManually = false
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFFF8FAFC), // Solar App Clean Light Background
        bottomBar = {
            IrisBottomNavigationBar(
                selectedTab = selectedTab,
                onSelectTab = { viewModel.selectTab(it) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Solar-App Inspired Clean Header Bar
            IrisHeaderBar(
                deviceId = activeDeviceId,
                isCloudMode = isCloudMode,
                isCloudConnected = isCloudConnected,
                isLocalOnline = status.isOnline,
                isAutoMode = (status.mode == "AUTO"),
                isNight = status.isNight,
                onToggleCloud = { viewModel.toggleConnectionMode() }
            )

            // Screen Content by Tab
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> DashboardTab(
                        status = status,
                        isCloudMode = isCloudMode,
                        onActionClick = { action ->
                            viewModel.sendWindowAction(action, savedPin)
                        },
                        onRetryLocal = { viewModel.refreshLocal() },
                        onSystemToggle = {
                            viewModel.toggleSystem(savedPin)
                        }
                    )
                    1 -> SensorsScreen(status = status)
                    2 -> SettingsScreen(
                        status = status,
                        deviceId = activeDeviceId,
                        isAutoMode = (status.mode == "AUTO"),
                        isCloudMode = isCloudMode,
                        savedPin = savedPin,
                        notificationsEnabled = notificationsEnabled,
                        notifyRain = notifyRain,
                        notifyDisconnect = notifyDisconnect,
                        notifyWindowMove = notifyWindowMove,
                        notifyTempDiff = notifyTempDiff,
                        onUpdateNotificationPref = { k, v -> viewModel.updateNotificationPref(k, v) },
                        onToggleAutoMode = { pin -> viewModel.sendWindowAction("MODE_TOGGLE", pin) },
                        onManualWindowAction = { action, pin -> viewModel.sendWindowAction(action, pin) },
                        onChangePin = { cur, newPin ->
                            viewModel.updateSecurity(cur, newPin, "")
                            viewModel.updateSavedPin(newPin)
                        },
                        onChangeApPassword = { cur, newPass -> viewModel.updateSecurity(cur, "", newPass) },
                        onSetTime = { time -> viewModel.setClockTime(time, savedPin) },
                        onReRunSetup = {
                            viewModel.scanWifi()
                            showSetupWizardManually = true
                        },
                        onToggleConnectionMode = { viewModel.toggleConnectionMode() }
                    )
                }
            }
        }
    }

    // PIN Verification Modal Dialog
    if (showPinDialog && pendingAction != null) {
        PinVerificationDialog(
            actionName = pendingAction!!,
            onConfirm = { pin ->
                when (pendingAction) {
                    "SYSTEM_TOGGLE" -> viewModel.toggleSystem(pin)
                    else -> viewModel.sendWindowAction(pendingAction!!, pin)
                }
                showPinDialog = false
                pendingAction = null
            },
            onDismiss = {
                showPinDialog = false
                pendingAction = null
            }
        )
    }
}

// ================= SOLAR APP INSPIRED HEADER =================

@Composable
fun IrisHeaderBar(
    deviceId: String,
    isCloudMode: Boolean,
    isCloudConnected: Boolean,
    isLocalOnline: Boolean,
    isAutoMode: Boolean,
    isNight: Boolean,
    onToggleCloud: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFFFFF))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .border(width = 0.5.dp, color = Color(0xFFE2E8F0)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🌸", fontSize = 20.sp)
                Text(
                    text = "Iris",
                    color = Color(0xFF0F172A),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (isCloudMode) {
                                if (isCloudConnected) Color(0xFF16A34A) else Color(0xFFDC2626)
                            } else {
                                if (isLocalOnline) Color(0xFF16A34A) else Color(0xFFDC2626)
                            },
                            CircleShape
                        )
                )
                Text(
                    text = "Living Room • $deviceId",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Connection Mode Badge (Clickable to switch Cloud / Local)
            Box(
                modifier = Modifier
                    .clickable { onToggleCloud() }
                    .background(
                        if (isCloudMode) {
                            if (isCloudConnected) Color(0xFFDCFCE7) else Color(0xFFFFEDD5)
                        } else {
                            if (isLocalOnline) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                        },
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isCloudMode) {
                        if (isCloudConnected) "🟢 CLOUD" else "🟠 CONNECTING"
                    } else {
                        if (isLocalOnline) "🟢 LOCAL ⚡" else "🔴 OFFLINE"
                    },
                    color = if (isCloudMode) {
                        if (isCloudConnected) Color(0xFF16A34A) else Color(0xFFC2410C)
                    } else {
                        if (isLocalOnline) Color(0xFF16A34A) else Color(0xFFDC2626)
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Mode Badge
            Box(
                modifier = Modifier
                    .background(
                        if (isAutoMode) Color(0xFFE0F2FE) else Color(0xFFFEF3C7),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isAutoMode) "AUTO" else "MANUAL",
                    color = if (isAutoMode) Color(0xFF0284C7) else Color(0xFFD97706),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ================= DASHBOARD TAB (HERO ANIMATION) =================

@Composable
fun DashboardTab(
    status: SmartWindowStatus,
    isCloudMode: Boolean,
    onActionClick: (String) -> Unit,
    onRetryLocal: () -> Unit,
    onSystemToggle: () -> Unit = {}
) {
    val isOpen = status.window == "OPEN"
    val diff = abs(status.outsideTemp - status.insideTemp)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Disconnected Offline Warning Banner when in Local Mode
        if (!isCloudMode && !status.isOnline) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("⚠️", fontSize = 16.sp)
                            Text(
                                "Local Device Connecting...",
                                color = Color(0xFF991B1B),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Connect phone Wi-Fi to 'SmartWindow-686E40' (Pass: 12345678). Once connected, real-time data will appear.",
                            color = Color(0xFF7F1D1D),
                            fontSize = 11.sp
                        )
                        Button(
                            onClick = onRetryLocal,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("⚡ Reconnect / Retry", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        // 5-Second Confirmation Debounce Banner
        if (status.timerActive) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("⏳", fontSize = 16.sp)
                            Text(status.reason, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Text("${status.timerRemaining}s", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Hero Weather & Window Canvas Card (Solar App style - Image 3)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(22.dp)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Top Weather & Status Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = if (status.isRaining) "🌧️ Rain" else if (status.isNight) "🌙 Night" else "☀️ Clear Day",
                                color = Color(0xFF0F172A),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text("•", color = Color(0xFF94A3B8))
                            Text("${String.format("%.1f", status.outsideTemp)}°C Outside", color = Color(0xFF64748B), fontSize = 13.sp)
                        }

                        Text(
                            text = "Diff: ${String.format("%.1f", diff)}°C",
                            color = Color(0xFF0284C7),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // System Clock & Day/Night Status Banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (status.isNight) Color(0xFF1E293B) else Color(0xFFF0FDF4),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (status.isNight) "🌙" else "☀️", fontSize = 16.sp)
                            Text(
                                text = "Clock: ${status.time}",
                                color = if (status.isNight) Color(0xFFF8FAFC) else Color(0xFF166534),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = if (status.isNight) "NIGHT LOCK (Manual Only)" else "DAYTIME (Auto Active)",
                            color = if (status.isNight) Color(0xFFA5B4FC) else Color(0xFF15803D),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // LIVE ANIMATED WINDOW CANVAS
                    AnimatedWindowView(
                        targetAngle = status.angle,
                        isOpen = isOpen,
                        isRaining = status.isRaining,
                        isNight = status.isNight
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Status Summary Text
                    Text(
                        text = status.reason,
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Environment Flow Indicator (Outside -> Window -> Inside) - Inspired by Image 3
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(18.dp)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Node 1: Outside
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (status.isRaining) "🌧️" else "🌳", fontSize = 24.sp)
                        Text("Outside", color = Color(0xFF64748B), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        Text("${String.format("%.1f", status.outsideTemp)}°C", color = Color(0xFF0F172A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    Text("•••", color = Color(0xFF0284C7), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)

                    // Node 2: Window Center
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(
                                if (isOpen) Color(0xFFDCFCE7) else Color(0xFFE0F2FE),
                                CircleShape
                            )
                            .border(
                                2.dp,
                                if (isOpen) Color(0xFF16A34A) else Color(0xFF0284C7),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (isOpen) "🪟" else "🔒", fontSize = 22.sp)
                    }

                    Text("•••", color = Color(0xFF0284C7), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)

                    // Node 3: Inside Room
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🏠", fontSize = 24.sp)
                        Text("Living Room", color = Color(0xFF64748B), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        Text("${String.format("%.1f", status.insideTemp)}°C", color = Color(0xFF0F172A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Quick Actions Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onActionClick("OPEN") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🔓 Open (180°)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = { onActionClick("CLOSE") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🔒 Close (0°)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = { onActionClick("MODE_TOGGLE") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🔄 Mode", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // System ON/OFF Master Switch Button
            Button(
                onClick = { onSystemToggle() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (status.systemEnabled) Color(0xFF0F172A) else Color(0xFF64748B)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = if (status.systemEnabled) "⚙️ System: ON" else "⏻ System: OFF",
                        color = if (status.systemEnabled) Color(0xFF4ADE80) else Color(0xFFFFFFFF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (status.systemEnabled) "Tap to Disable" else "Tap to Enable",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

// ================= SOLAR APP INSPIRED LIGHT BOTTOM NAVIGATION BAR =================

@Composable
fun IrisBottomNavigationBar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    NavigationBar(
        containerColor = Color(0xFFFFFFFF),
        tonalElevation = 8.dp,
        modifier = Modifier.border(width = 0.5.dp, color = Color(0xFFE2E8F0))
    ) {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onSelectTab(0) },
            icon = { Text("🪟", fontSize = 20.sp) },
            label = { Text("Dashboard", fontSize = 11.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF0284C7),
                selectedTextColor = Color(0xFF0284C7),
                unselectedIconColor = Color(0xFF64748B),
                unselectedTextColor = Color(0xFF64748B),
                indicatorColor = Color(0xFFE0F2FE)
            )
        )

        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onSelectTab(1) },
            icon = { Text("📊", fontSize = 20.sp) },
            label = { Text("Sensors", fontSize = 11.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF0284C7),
                selectedTextColor = Color(0xFF0284C7),
                unselectedIconColor = Color(0xFF64748B),
                unselectedTextColor = Color(0xFF64748B),
                indicatorColor = Color(0xFFE0F2FE)
            )
        )

        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onSelectTab(2) },
            icon = { Text("⚙️", fontSize = 20.sp) },
            label = { Text("Settings", fontSize = 11.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF0284C7),
                selectedTextColor = Color(0xFF0284C7),
                unselectedIconColor = Color(0xFF64748B),
                unselectedTextColor = Color(0xFF64748B),
                indicatorColor = Color(0xFFE0F2FE)
            )
        )
    }
}

// ================= PIN VERIFICATION MODAL =================

@Composable
fun PinVerificationDialog(actionName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var pinText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFFFFFFF),
        title = { Text("Authorize $actionName", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Enter your 4-digit Master PIN to continue:", color = Color(0xFF64748B), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = pinText,
                    onValueChange = { if (it.length <= 4) pinText = it },
                    label = { Text("Security Master PIN") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (pinText.length == 4) onConfirm(pinText) },
                enabled = pinText.length == 4,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
            ) {
                Text("Authorize")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF64748B)) }
        }
    )
}
