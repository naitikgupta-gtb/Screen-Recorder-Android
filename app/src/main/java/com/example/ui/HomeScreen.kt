package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AudioOption
import com.example.data.RecordingConfig
import com.example.service.RecordingStatus
import com.example.ui.theme.*

@Composable
fun HomeScreen(
    recordingStatus: RecordingStatus,
    config: RecordingConfig,
    isFloatingPenActive: Boolean,
    onStartRecordingClicked: () -> Unit,
    onStopRecordingClicked: () -> Unit,
    onPauseResumeClicked: () -> Unit,
    onToggleAudio: () -> Unit,
    onToggleCountdown: () -> Unit,
    onToggleFloatingOverlay: () -> Unit,
    onToggleFloatingPen: () -> Unit,
    onOpenInAppDrawing: () -> Unit,
    onOpenQuickTileGuide: () -> Unit,
    onOpenGalleryClicked: () -> Unit
) {
    val context = LocalContext.current
    val hasOverlayPermission = remember {
        derivedStateOf { Settings.canDrawOverlays(context) }
    }

    val isRecording = recordingStatus.isRecording
    val isPaused = recordingStatus.isPaused
    val countdown = recordingStatus.countdownRemaining

    // Ambient pulsing animation for the record hero button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.08f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isRecording) 800 else 1800, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top Minimal Status Badge (Clean & Attractive)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkSurfaceCard)
                    .border(
                        width = 1.dp,
                        color = when {
                            isRecording -> RecordRedPrimary.copy(alpha = 0.5f)
                            countdown != null -> AccentYellow.copy(alpha = 0.5f)
                            else -> AccentGreen.copy(alpha = 0.3f)
                        },
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isRecording -> if (isPaused) AccentYellow else RecordRedPrimary
                                    countdown != null -> AccentYellow
                                    else -> AccentGreen
                                }
                            )
                    )
                    Text(
                        text = when {
                            isRecording -> if (isPaused) "PAUSED" else "RECORDING LIVE"
                            countdown != null -> "STARTING IN ${countdown}s"
                            else -> "READY · DCIM AUTO-SAVE"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = when {
                            isRecording -> if (isPaused) AccentYellow else RecordRedPrimary
                            countdown != null -> AccentYellow
                            else -> TextSecondary
                        }
                    )
                }
            }
        }

        // Minimalist Permission Chip (Only if permission needed)
        if (!hasOverlayPermission.value && config.floatingOverlayEnabled) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF2B1D12))
                    .border(1.dp, Color(0xFFFF9800).copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🖊️", fontSize = 16.sp)
                    Text(
                        text = "Enable Screen Pen overlay",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFFCC80)
                    )
                }
                Button(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9800),
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Allow", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 2. HERO CENTERPIECE: Big Pulsing Record Button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(220.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer ambient glow ring
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            if (isRecording) RecordRedGlow else RecordRedSubtle
                        )
                )

                // Inner Main Circular Action Button
                Box(
                    modifier = Modifier
                        .size(175.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = if (isRecording) {
                                    listOf(RecordRedPrimary, RecordRedVariant)
                                } else {
                                    listOf(Color(0xFFE53935), Color(0xFFB71C1C))
                                }
                            )
                        )
                        .border(
                            width = 4.dp,
                            color = if (isRecording) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                        .clickable {
                            if (isRecording) {
                                onStopRecordingClicked()
                            } else {
                                onStartRecordingClicked()
                            }
                        }
                        .testTag("record_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (countdown != null) {
                            Text(
                                text = "$countdown",
                                color = AccentYellow,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 62.sp
                            )
                            Text(
                                text = "STARTING...",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp
                            )
                        } else if (isRecording) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop Recording",
                                tint = Color.White,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "STOP & SAVE",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.FiberManualRecord,
                                contentDescription = "Start Recording",
                                tint = Color.White,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "START",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                letterSpacing = 2.sp
                            )
                        }
                    }
                }
            }

            // Live Timer & Controls when Recording
            if (isRecording) {
                val mins = recordingStatus.durationSeconds / 60
                val secs = recordingStatus.durationSeconds % 60
                val timeFormatted = String.format("%02d:%02d", mins, secs)

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = timeFormatted,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 2.sp
                        )

                        FilledTonalButton(
                            onClick = onPauseResumeClicked,
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = DarkSurfaceVariant),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (isPaused) "Resume" else "Pause",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isPaused) "Resume" else "Pause", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 3. CLEAN QUICK ACTION ROW: 3 Minimalist Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Action 1: Gallery
            CleanActionPill(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.PhotoLibrary,
                iconColor = AccentCyan,
                label = "Recordings",
                onClick = onOpenGalleryClicked,
                testTag = "open_gallery_button"
            )

            // Action 2: Screen Highlighter Pen
            CleanActionPill(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Edit,
                iconColor = if (isFloatingPenActive) RecordRedPrimary else AccentYellow,
                label = if (isFloatingPenActive) "Stop Pen" else "Screen Pen",
                onClick = {
                    if (!hasOverlayPermission.value) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    } else {
                        onToggleFloatingPen()
                    }
                },
                testTag = "toggle_floating_pen_button"
            )

            // Action 3: Quick Settings Tile Guide
            CleanActionPill(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Widgets,
                iconColor = AccentGreen,
                label = "Tile Setup",
                onClick = onOpenQuickTileGuide,
                testTag = "open_tile_guide_button"
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. MINIMAL QUICK TOGGLES (Audio · Countdown · Floating Overlay)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Audio toggle
            CleanSettingToggle(
                modifier = Modifier.weight(1f),
                icon = if (config.audioOption == AudioOption.MIC) Icons.Default.Mic else Icons.Default.MicOff,
                title = "Mic",
                stateText = if (config.audioOption == AudioOption.MIC) "On" else "Mute",
                isActive = config.audioOption == AudioOption.MIC,
                activeColor = AccentGreen,
                onClick = onToggleAudio,
                testTag = "toggle_audio_chip"
            )

            // Countdown timer toggle
            CleanSettingToggle(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Timer,
                title = "Timer",
                stateText = if (config.countdownSeconds > 0) "${config.countdownSeconds}s" else "Off",
                isActive = config.countdownSeconds > 0,
                activeColor = AccentYellow,
                onClick = onToggleCountdown,
                testTag = "toggle_countdown_chip"
            )

            // Floating controls toggle
            CleanSettingToggle(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Layers,
                title = "Tools",
                stateText = if (config.floatingOverlayEnabled) "On" else "Off",
                isActive = config.floatingOverlayEnabled,
                activeColor = AccentCyan,
                onClick = onToggleFloatingOverlay,
                testTag = "toggle_floating_chip"
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun CleanActionPill(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CleanSettingToggle(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    stateText: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        border = if (isActive) {
            androidx.compose.foundation.BorderStroke(1.dp, activeColor.copy(alpha = 0.4f))
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isActive) activeColor else TextTertiary,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = title,
                    fontSize = 10.sp,
                    color = TextSecondary
                )
                Text(
                    text = stateText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) activeColor else TextPrimary
                )
            }
        }
    }
}
