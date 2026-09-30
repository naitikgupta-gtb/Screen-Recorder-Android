package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickTileGuideSheet(
    onDismiss: () -> Unit,
    onTestTileAction: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextTertiary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AccentYellow.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Widgets,
                        contentDescription = "Quick Settings",
                        tint = AccentYellow,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = "Quick Settings Widget",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Record from any app in 2 seconds",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            Text(
                text = "Add our Quick Settings tile to your notification pull-down shade right next to Wi-Fi and Bluetooth toggles:",
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )

            // Step 1
            StepItem(
                stepNumber = "1",
                title = "Swipe down twice",
                description = "Pull down from the top edge of your screen to open the full Quick Settings panel."
            )

            // Step 2
            StepItem(
                stepNumber = "2",
                title = "Tap the Edit / Pencil icon",
                description = "Locate the ✏️ pencil or 'Edit' button in your Quick Settings shade."
            )

            // Step 3
            StepItem(
                stepNumber = "3",
                title = "Drag 'Record Screen' tile",
                description = "Scroll to the inactive tiles list, find 'Record Screen', and drag it up into your active widget grid."
            )

            // Step 4
            StepItem(
                stepNumber = "4",
                title = "Tap anytime to record!",
                description = "Whenever you want to record, tap the tile. It starts with a 2s countdown, shows the floating markup tool, and saves directly to your Phone Gallery!"
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    onDismiss()
                    onTestTileAction()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("test_tile_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RecordRedPrimary)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Test Quick Tile",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test Quick Recording Now", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StepItem(stepNumber: String, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(AccentCyan.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                color = AccentCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )
        }
    }
}
