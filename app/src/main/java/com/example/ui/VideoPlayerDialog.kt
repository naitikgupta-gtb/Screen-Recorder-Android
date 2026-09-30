package com.example.ui

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.RecordingItem
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun VideoPlayerDialog(
    item: RecordingItem,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onOpenInGallery: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableStateOf(0) }
    var totalDurationMs by remember { mutableStateOf(item.durationMs.toInt()) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    // Progress polling loop
    LaunchedEffect(videoViewRef, isPlaying) {
        while (isActive) {
            val vv = videoViewRef
            if (vv != null && vv.isPlaying) {
                currentPositionMs = vv.currentPosition
                if (vv.duration > 0) totalDurationMs = vv.duration
            }
            delay(250)
        }
    }

    Dialog(
        onDismissRequest = {
            videoViewRef?.stopPlayback()
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(DarkSurface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = "${item.formattedSize} · ${item.formattedDate}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = {
                            videoViewRef?.stopPlayback()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("close_player_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Player",
                            tint = TextSecondary
                        )
                    }
                }

                // Video Surface
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setVideoURI(item.uri)
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    totalDurationMs = duration
                                    start()
                                    isPlaying = true
                                }
                                setOnErrorListener { _, _, _ ->
                                    true
                                }
                            }
                        },
                        update = { view ->
                            videoViewRef = view
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Big center play/pause overlay on tap
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = {
                                val vv = videoViewRef ?: return@IconButton
                                if (vv.isPlaying) {
                                    vv.pause()
                                    isPlaying = false
                                } else {
                                    vv.start()
                                    isPlaying = true
                                }
                            },
                            modifier = Modifier.testTag("center_play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                // Scrubber / Seek Bar
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Slider(
                        value = if (totalDurationMs > 0) {
                            (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f,
                        onValueChange = { fraction ->
                            val targetMs = (fraction * totalDurationMs).toInt()
                            currentPositionMs = targetMs
                            videoViewRef?.seekTo(targetMs)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = RecordRedPrimary,
                            activeTrackColor = RecordRedPrimary,
                            inactiveTrackColor = DarkSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatMs(currentPositionMs),
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = formatMs(totalDurationMs),
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                // Bottom Action Bar: Share, Open in Gallery, Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onOpenInGallery,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = DarkSurfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Open in Gallery",
                            modifier = Modifier.size(16.dp),
                            tint = AccentCyan
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Phone Gallery", fontSize = 12.sp, color = TextPrimary)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = onShare) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Video",
                                tint = TextPrimary
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Video",
                                tint = RecordRedPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Int): String {
    val totalSecs = (ms / 1000).coerceAtLeast(0)
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%02d:%02d", mins, secs)
}
