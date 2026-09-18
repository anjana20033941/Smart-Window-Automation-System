package com.example.smartwindow.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnimatedWindowView(
    targetAngle: Int,          // 0 to 180
    isOpen: Boolean,
    isRaining: Boolean,
    isNight: Boolean,
    modifier: Modifier = Modifier
) {
    // Smooth angle animation
    val animatedAngle by animateFloatAsState(
        targetValue = targetAngle.toFloat(),
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "windowAngle"
    )

    // Continuous breeze wave animation when open
    val infiniteTransition = rememberInfiniteTransition(label = "breeze")
    val breezePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "breezeWave"
    )

    // Rain drop continuous animation
    val rainDropY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rainDrops"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(230.dp)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)), // Modern Light Background
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                val canvasW = size.width
                val canvasH = size.height

                // 1. Sky / Outdoor Background tint inside window opening
                val skyColorTop = if (isNight) Color(0xFF1E293B) else Color(0xFFBAE6FD)
                val skyColorBottom = if (isNight) Color(0xFF0F172A) else Color(0xFFE0F2FE)

                val frameMarginX = canvasW * 0.12f
                val frameMarginY = canvasH * 0.08f
                val frameW = canvasW - (frameMarginX * 2)
                val frameH = canvasH - (frameMarginY * 2)

                // Draw sky gradient
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(skyColorTop, skyColorBottom)),
                    topLeft = Offset(frameMarginX, frameMarginY),
                    size = Size(frameW, frameH),
                    cornerRadius = CornerRadius(16f, 16f)
                )

                // 2. Animated Rain Drops (if raining)
                if (isRaining) {
                    val dropColor = Color(0xFF2563EB).copy(alpha = 0.8f)
                    for (i in 0..12) {
                        val startX = frameMarginX + (frameW * (i / 12f))
                        val currentY = frameMarginY + ((rainDropY + (i * 0.15f)) % 1f) * frameH
                        drawLine(
                            color = dropColor,
                            start = Offset(startX, currentY),
                            end = Offset(startX - 5f, currentY + 14f),
                            strokeWidth = 2.5f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // 3. Animated Airflow / Breeze curves (if open)
                if (isOpen && animatedAngle > 20f) {
                    val breezeAlpha = (animatedAngle / 180f) * 0.9f
                    val breezeColor = Color(0xFF0284C7).copy(alpha = breezeAlpha)
                    for (lineIdx in 0..2) {
                        val path = Path()
                        val baseY = frameMarginY + frameH * (0.35f + (lineIdx * 0.2f))
                        val waveOffset = (breezePhase + lineIdx * 50f) * (Math.PI.toFloat() / 180f)

                        path.moveTo(frameMarginX - 10f, baseY)
                        for (xStep in 0..20) {
                            val curX = frameMarginX + (frameW * (xStep / 20f))
                            val waveY = baseY + sin(waveOffset + (xStep * 0.4f)) * 10f
                            path.lineTo(curX, waveY)
                        }

                        drawPath(
                            path = path,
                            color = breezeColor,
                            style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                        )
                    }
                }

                // 4. Outer Window Wall Frame (Solid Dark Slate Frame for high contrast)
                val frameBorderColor = Color(0xFF1E293B)
                drawRoundRect(
                    color = frameBorderColor,
                    topLeft = Offset(frameMarginX, frameMarginY),
                    size = Size(frameW, frameH),
                    cornerRadius = CornerRadius(16f, 16f),
                    style = Stroke(width = 8f)
                )

                // Center sill / divider
                val centerX = canvasW / 2f
                drawLine(
                    color = frameBorderColor,
                    start = Offset(centerX, frameMarginY),
                    end = Offset(centerX, frameMarginY + frameH),
                    strokeWidth = 6f
                )

                // 5. Dual 180° Rotating Window Panes
                val paneMaxW = (frameW / 2f) - 6f
                val paneH = frameH - 12f
                val paneTopY = frameMarginY + 6f

                val rad = Math.toRadians(animatedAngle.toDouble())
                val cosVal = cos(rad).toFloat()
                val sinVal = sin(rad).toFloat()

                // LEFT PANE (hinged at frameMarginX + 4f)
                val leftHingeX = frameMarginX + 4f
                val leftPaneProjW = paneMaxW * cosVal
                val leftPerspectiveY = paneH * (1f - (sinVal * 0.15f))
                val leftYOffset = (paneH - leftPerspectiveY) / 2f

                val paneGlassColor = if (isNight) Color(0xFF334155).copy(alpha = 0.5f) else Color(0xFF38BDF8).copy(alpha = 0.35f)
                val paneBorderColor = if (isOpen) Color(0xFF0284C7) else Color(0xFF475569)

                val leftPanePath = Path().apply {
                    moveTo(leftHingeX, paneTopY + leftYOffset)
                    lineTo(leftHingeX + leftPaneProjW, paneTopY)
                    lineTo(leftHingeX + leftPaneProjW, paneTopY + paneH)
                    lineTo(leftHingeX, paneTopY + paneH - leftYOffset)
                    close()
                }
                drawPath(leftPanePath, paneGlassColor)
                drawPath(leftPanePath, paneBorderColor, style = Stroke(width = 4f))

                // RIGHT PANE (hinged at frameMarginX + frameW - 4f)
                val rightHingeX = frameMarginX + frameW - 4f
                val rightPaneProjW = paneMaxW * cosVal
                val rightPerspectiveY = paneH * (1f - (sinVal * 0.15f))
                val rightYOffset = (paneH - rightPerspectiveY) / 2f

                val rightPanePath = Path().apply {
                    moveTo(rightHingeX, paneTopY + rightYOffset)
                    lineTo(rightHingeX - rightPaneProjW, paneTopY)
                    lineTo(rightHingeX - rightPaneProjW, paneTopY + paneH)
                    lineTo(rightHingeX, paneTopY + paneH - rightYOffset)
                    close()
                }
                drawPath(rightPanePath, paneGlassColor)
                drawPath(rightPanePath, paneBorderColor, style = Stroke(width = 4f))

                // Center Lock Indicator when 0 degrees (Closed)
                if (animatedAngle < 8f) {
                    drawCircle(
                        color = Color(0xFFD97706),
                        radius = 12f,
                        center = Offset(centerX, frameMarginY + (frameH / 2f))
                    )
                }
            }

            // Overlay Badges & Status Indicators
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            if (isOpen) Color(0xFF16A34A) else Color(0xFF0284C7),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isOpen) "OPEN (${animatedAngle.toInt()}°)" else "CLOSED (0°)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isRaining) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFDC2626), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text("🌧️ RAINING", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (isNight) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF7C3AED), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text("🌙 NIGHT LOCK", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (isOpen) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF0EA5E9), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text("🍃 VENTILATING", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
