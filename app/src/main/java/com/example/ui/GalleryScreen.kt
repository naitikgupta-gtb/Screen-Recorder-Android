package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GalleryMediaManager
import com.example.data.RecordingItem
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GalleryScreen(
    recordings: List<RecordingItem>,
    onRefresh: () -> Unit,
    onDeleteRecording: (RecordingItem) -> Unit
) {
    val context = LocalContext.current
    var selectedVideoForPlay by remember { mutableStateOf<RecordingItem?>(null) }
    var itemToDelete by remember { mutableStateOf<RecordingItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Top Album Info Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AccentCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Album",
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Phone Gallery Album",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "DCIM/ScreenRecorder (${recordings.size} videos)",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                IconButton(onClick = onRefresh, modifier = Modifier.testTag("refresh_gallery_button")) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Videos",
                        tint = TextSecondary
                    )
                }
            }
        }

        if (recordings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = "No Recordings",
                            tint = TextTertiary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Text(
                        text = "No recordings found yet",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Recorded videos will automatically appear here and in your phone's Gallery app.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 32.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(recordings, key = { it.id }) { item ->
                    RecordingCard(
                        item = item,
                        onClick = { selectedVideoForPlay = item },
                        onShare = { GalleryMediaManager.shareVideo(context, item.uri) },
                        onOpenGallery = { GalleryMediaManager.openInSystemGallery(context, item.uri) },
                        onDelete = { itemToDelete = item }
                    )
                }
            }
        }
    }

    // Playback dialog
    selectedVideoForPlay?.let { item ->
        VideoPlayerDialog(
            item = item,
            onDismiss = { selectedVideoForPlay = null },
            onShare = { GalleryMediaManager.shareVideo(context, item.uri) },
            onOpenInGallery = { GalleryMediaManager.openInSystemGallery(context, item.uri) },
            onDelete = {
                itemToDelete = item
                selectedVideoForPlay = null
            }
        )
    }

    // Delete confirmation dialog
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Delete Recording?",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove '${item.name}' from your Phone Gallery?",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteRecording(item)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RecordRedPrimary),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun RecordingCard(
    item: RecordingItem,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onOpenGallery: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var thumbnailBitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(item.id) {
        withContext(Dispatchers.IO) {
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(
                        item.uri,
                        Size(320, 240),
                        null
                    )
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Video.Thumbnails.getThumbnail(
                        context.contentResolver,
                        item.id,
                        MediaStore.Video.Thumbnails.MINI_KIND,
                        null
                    )
                }
                thumbnailBitmap = bmp
            } catch (_: Exception) {}
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("recording_card_${item.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Thumbnail or Video Preview Frame
            Box(
                modifier = Modifier
                    .size(90.dp, 64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap!!.asImageBitmap(),
                        contentDescription = "Video Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayCircleFilled,
                        contentDescription = "Play",
                        tint = AccentCyan.copy(alpha = 0.7f),
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Small duration pill badge on bottom
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.formattedDuration,
                        fontSize = 10.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Info column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.formattedSize} · ${item.formattedDate}",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Phone Gallery",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                    Text(
                        text = "•",
                        fontSize = 10.sp,
                        color = TextTertiary
                    )
                    Text(
                        text = "DCIM",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }

            // Actions dropdown / menu buttons
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onShare, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onOpenGallery, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Gallery",
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = RecordRedPrimary.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
