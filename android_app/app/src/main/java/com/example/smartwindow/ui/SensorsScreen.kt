package com.example.smartwindow.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartwindow.data.SmartWindowStatus
import kotlin.math.abs

@Composable
fun SensorsScreen(status: SmartWindowStatus) {
    val scrollState = rememberScrollState()
    val tempDiff = abs(status.outsideTemp - status.insideTemp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Temperature Overview Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TEMPERATURE SENSORS", color = Color(0xFF64748B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Diff: ${String.format("%.1f", tempDiff)}°C (Req: 3.5°C)", color = Color(0xFF0284C7), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("OUTSIDE (Calibrated -9.5°C)", color = Color(0xFF64748B), fontSize = 10.sp)
                        Text("${String.format("%.1f", status.outsideTemp)}°C", color = Color(0xFF0F172A), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text("➔", color = Color(0xFF94A3B8), fontSize = 20.sp)
                    Column(horizontalAlignment = Alignment.End) {
                        Text("INSIDE LIVING ROOM", color = Color(0xFF64748B), fontSize = 10.sp)
                        Text("${String.format("%.1f", status.insideTemp)}°C", color = Color(0xFF0F172A), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        // Humidity Comparison Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("RELATIVE HUMIDITY", color = Color(0xFF64748B), fontSize = 11.sp, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("OUTSIDE AIR", color = Color(0xFF64748B), fontSize = 10.sp)
                        Text("${String.format("%.0f", status.outsideHumidity)}%", color = Color(0xFF0284C7), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("INSIDE AIR", color = Color(0xFF64748B), fontSize = 10.sp)
                        Text("${String.format("%.0f", status.insideHumidity)}%", color = Color(0xFF0284C7), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }

                LinearProgressIndicator(
                    progress = { (status.outsideHumidity / 100f).coerceIn(0f, 1f) },
                    color = Color(0xFF0284C7),
                    trackColor = Color(0xFFE2E8F0),
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                )
            }
        }

        // Rain Sensor Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("RAIN SENSOR (GPIO 36 / VP)", color = Color(0xFF64748B), fontSize = 11.sp, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (status.isRaining) "🌧️" else "☀️", fontSize = 28.sp)
                        Column {
                            Text(
                                text = if (status.isRaining) "RAIN DETECTED" else "DRY / CLEAR",
                                color = if (status.isRaining) Color(0xFFDC2626) else Color(0xFF16A34A),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Automatic Close Threshold: < 3400 (3.5s confirmation)", color = Color(0xFF64748B), fontSize = 11.sp)
                        }
                    }

                    Text("Raw: ${status.rainRaw}", color = Color(0xFF64748B), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Light & Night Lock Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("LIGHT SENSORS (LDR ADC1)", color = Color(0xFF64748B), fontSize = 11.sp, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("OUTSIDE LIGHT (GPIO 32)", color = Color(0xFF64748B), fontSize = 10.sp)
                        Text(if (status.isOutsideDark) "🌙 DARK (NIGHT)" else "☀️ BRIGHT (DAY)", color = Color(0xFF0F172A), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("INSIDE LIGHT (GPIO 33)", color = Color(0xFF64748B), fontSize = 10.sp)
                        Text(if (status.isInsideDark) "🌙 ROOM DARK" else "☀️ ROOM LIT", color = Color(0xFF0F172A), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                androidx.compose.material3.HorizontalDivider(color = Color(0xFFE2E8F0))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("NIGHT AUTOMATION LOCK", color = Color(0xFF64748B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (status.isNight) "🔒 ACTIVE (Past 18:00)" else "🔓 INACTIVE (Daytime)",
                        color = if (status.isNight) Color(0xFFDC2626) else Color(0xFF16A34A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
