package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AudioOption
import com.example.data.RecordingConfig
import com.example.data.VideoResolution
import com.example.ui.theme.*

@Composable
fun SettingsScreen(
    config: RecordingConfig,
    onResolutionChanged: (VideoResolution) -> Unit,
    onFpsChanged: (Int) -> Unit,
    onBitrateChanged: (Int) -> Unit,
    onAudioOptionChanged: (AudioOption) -> Unit,
    onCountdownChanged: (Int) -> Unit,
    onFloatingOverlayToggle: (Boolean) -> Unit,
    onOpenTileGuide: () -> Unit
) {
    val context = LocalContext.current
    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Section: Video Quality
        SettingsSectionHeader(title = "VIDEO RECORDING QUALITY", icon = Icons.Default.HighQuality)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Resolution Selector
                Text(
                    text = "Resolution",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VideoResolution.values().forEach { res ->
                        val isSelected = config.resolution == res
                        OutlinedButton(
                            onClick = { onResolutionChanged(res) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("res_option_${res.name}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) RecordRedSubtle else Color.Transparent,
                                contentColor = if (isSelected) RecordRedPrimary else TextSecondary
                            ),
                            border = ButtonDefaults.outlinedButtonBorder(
                                enabled = true
                            ).copy(
                                brush = androidx.compose.ui.graphics.SolidColor(
                                    if (isSelected) RecordRedPrimary else DarkSurfaceVariant
                                )
                            )
                        ) {
                            Text(
                                text = res.label.split(" ").first(),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                HorizontalDivider(color = DarkSurfaceVariant)

                // Frame Rate (FPS)
                Text(
                    text = "Frame Rate (FPS)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(60, 30).forEach { fps ->
                        val isSelected = config.fps == fps
                        OutlinedButton(
                            onClick = { onFpsChanged(fps) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("fps_option_$fps"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) RecordRedSubtle else Color.Transparent,
                                contentColor = if (isSelected) RecordRedPrimary else TextSecondary
                            ),
                            border = ButtonDefaults.outlinedButtonBorder(
                                enabled = true
                            ).copy(
                                brush = androidx.compose.ui.graphics.SolidColor(
                                    if (isSelected) RecordRedPrimary else DarkSurfaceVariant
                                )
                            )
                        ) {
                            Text(
                                text = "$fps FPS",
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                HorizontalDivider(color = DarkSurfaceVariant)

                // Bitrate
                Text(
                    text = "Video Bitrate",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        12 to "12 Mbps",
                        8 to "8 Mbps",
                        4 to "4 Mbps"
                    ).forEach { (mbps, label) ->
                        val isSelected = config.bitrateMbps == mbps
                        OutlinedButton(
                            onClick = { onBitrateChanged(mbps) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("bitrate_option_$mbps"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) RecordRedSubtle else Color.Transparent,
                                contentColor = if (isSelected) RecordRedPrimary else TextSecondary
                            ),
                            border = ButtonDefaults.outlinedButtonBorder(
                                enabled = true
                            ).copy(
                                brush = androidx.compose.ui.graphics.SolidColor(
                                    if (isSelected) RecordRedPrimary else DarkSurfaceVariant
                                )
                            )
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // Section: Audio & Recording Behavior
        SettingsSectionHeader(title = "AUDIO & CONTROLS", icon = Icons.Default.SettingsVoice)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Audio Source
                Text(
                    text = "Audio Source",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AudioOption.values().forEach { opt ->
                        val isSelected = config.audioOption == opt
                        OutlinedButton(
                            onClick = { onAudioOptionChanged(opt) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("audio_option_${opt.name}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) RecordRedSubtle else Color.Transparent,
                                contentColor = if (isSelected) RecordRedPrimary else TextSecondary
                            ),
                            border = ButtonDefaults.outlinedButtonBorder(
                                enabled = true
                            ).copy(
                                brush = androidx.compose.ui.graphics.SolidColor(
                                    if (isSelected) RecordRedPrimary else DarkSurfaceVariant
                                )
                            )
                        ) {
                            Text(
                                text = opt.label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                HorizontalDivider(color = DarkSurfaceVariant)

                // Countdown Timer (2s default as user requested!)
                Text(
                    text = "Countdown Before Start",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        2 to "2 Sec (Def)",
                        3 to "3 Sec",
                        5 to "5 Sec",
                        0 to "None"
                    ).forEach { (sec, label) ->
                        val isSelected = config.countdownSeconds == sec
                        OutlinedButton(
                            onClick = { onCountdownChanged(sec) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("countdown_option_$sec"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) RecordRedSubtle else Color.Transparent,
                                contentColor = if (isSelected) RecordRedPrimary else TextSecondary
                            ),
                            border = ButtonDefaults.outlinedButtonBorder(
                                enabled = true
                            ).copy(
                                brush = androidx.compose.ui.graphics.SolidColor(
                                    if (isSelected) RecordRedPrimary else DarkSurfaceVariant
                                )
                            )
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // Section: Floating Overlay & Drawing Tools
        SettingsSectionHeader(title = "FLOATING OVERLAY & DRAWING", icon = Icons.Default.Layers)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show Floating Bubble & Markup",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Small on-screen pill with timer, pause/resume, and drawing/highlighter pen tools.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                    Switch(
                        checked = config.floatingOverlayEnabled,
                        onCheckedChange = { onFloatingOverlayToggle(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = RecordRedPrimary,
                            checkedTrackColor = RecordRedSubtle
                        ),
                        modifier = Modifier.testTag("floating_overlay_switch")
                    )
                }

                if (!hasOverlayPermission) {
                    FilledTonalButton(
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFFFF9800).copy(alpha = 0.2f),
                            contentColor = Color(0xFFFFB74D)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "Grant Permission",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Grant 'Display over other apps' Permission")
                    }
                }
            }
        }

        // Section: Quick Settings Tile
        SettingsSectionHeader(title = "QUICK SETTINGS WIDGET", icon = Icons.Default.Widgets)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenTileGuide() },
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(AccentYellow.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = "Quick Tile",
                        tint = AccentYellow,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quick Settings Widget Guide",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Learn how to put the Record Screen widget in your notification pull-down menu next to Wi-Fi.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowForwardIos,
                    contentDescription = "Open Guide",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = RecordRedPrimary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = RecordRedPrimary,
            letterSpacing = 1.sp
        )
    }
}
