package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.data.*
import com.example.service.FloatingDrawService
import com.example.service.ScreenCapturePermissionActivity
import com.example.service.ScreenRecordService
import com.example.ui.GalleryScreen
import com.example.ui.HomeScreen
import com.example.ui.InAppDrawingDialog
import com.example.ui.QuickTileGuideSheet
import com.example.ui.SettingsScreen
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ScreenTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    RECORD("Record", Icons.Default.FiberManualRecord),
    GALLERY("Gallery", Icons.Default.PhotoLibrary),
    SETTINGS("Settings", Icons.Default.Tune)
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private lateinit var preferences: RecordingPreferences
    private var mediaProjectionManager: MediaProjectionManager? = null

    private var recordingsList by mutableStateOf<List<RecordingItem>>(emptyList())
    private var configState by mutableStateOf(RecordingConfig())
    private var selectedTab by mutableStateOf(ScreenTab.RECORD)
    private var showTileGuide by mutableStateOf(false)

    // Permission launcher for MediaProjection
    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = ScreenRecordService.startIntent(
                this,
                result.resultCode,
                result.data!!
            )
            ContextCompat.startForegroundService(this, serviceIntent)
            Toast.makeText(this, "Recording starting...", Toast.LENGTH_SHORT).show()
            
            // Automatically minimize app to Home Screen so the user can record their game or app
            minimizeToHomeScreen()
        } else {
            Toast.makeText(this, "Screen capture permission canceled", Toast.LENGTH_SHORT).show()
        }
    }

    // Permission launcher for Storage on Android 7-9 (API 24-28)
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            checkNotificationAndProceed()
        } else {
            Toast.makeText(
                this,
                "Storage permission is required on Android 7-9 to save recordings to Gallery",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Permission launcher for Audio
    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(
                this,
                "Microphone permission not granted. Recording will be muted.",
                Toast.LENGTH_SHORT
            ).show()
            preferences.setAudioOption(AudioOption.MUTE)
            configState = preferences.getConfig()
        }
        startCaptureFlow()
    }

    // Permission launcher for Notification
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        checkAudioAndStartCapture()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        preferences = RecordingPreferences(this)
        configState = preferences.getConfig()
        mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager

        loadRecordings()

        setContent {
            MyApplicationTheme {
                val recordingStatus by ScreenRecordService.recordingStatus.collectAsStateWithLifecycle()
                val isFloatingPenActive by FloatingDrawService.isFloatingPenActive.collectAsStateWithLifecycle()
                var showInAppDrawingDialog by remember { mutableStateOf(false) }

                // Refresh recordings when a new video is saved or recording stops
                LaunchedEffect(recordingStatus.lastSavedUri, recordingStatus.isRecording) {
                    if (recordingStatus.lastSavedUri != null || !recordingStatus.isRecording) {
                        kotlinx.coroutines.delay(600)
                        loadRecordings()
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = DarkBackground,
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Row(
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = "Screen Recorder Pro",
                                        tint = RecordRedPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "Screen Recorder",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = TextPrimary
                                    )
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = { showTileGuide = true },
                                    modifier = Modifier.testTag("top_bar_tile_guide_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Widgets,
                                        contentDescription = "Quick Settings Tile Guide",
                                        tint = AccentYellow
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = DarkBackground
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = DarkSurface,
                            tonalElevation = 8.dp
                        ) {
                            ScreenTab.values().forEach { tab ->
                                val isSelected = selectedTab == tab
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = {
                                        selectedTab = tab
                                        if (tab == ScreenTab.GALLERY) {
                                            loadRecordings()
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.title,
                                            tint = if (isSelected) RecordRedPrimary else TextSecondary
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.title,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) RecordRedPrimary else TextSecondary
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = RecordRedSubtle
                                    ),
                                    modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AnimatedContent(
                            targetState = selectedTab,
                            label = "tabTransition"
                        ) { tab ->
                            when (tab) {
                                ScreenTab.RECORD -> {
                                    HomeScreen(
                                        recordingStatus = recordingStatus,
                                        config = configState,
                                        isFloatingPenActive = isFloatingPenActive,
                                        onStartRecordingClicked = {
                                            initiateRecording()
                                        },
                                        onStopRecordingClicked = {
                                            stopRecordingService()
                                        },
                                        onPauseResumeClicked = {
                                            pauseOrResumeRecordingService(recordingStatus.isPaused)
                                        },
                                        onToggleAudio = {
                                            val next = if (configState.audioOption == AudioOption.MIC) {
                                                AudioOption.MUTE
                                            } else {
                                                AudioOption.MIC
                                            }
                                            preferences.setAudioOption(next)
                                            configState = preferences.getConfig()
                                        },
                                        onToggleCountdown = {
                                            val next = when (configState.countdownSeconds) {
                                                2 -> 3
                                                3 -> 0
                                                else -> 2
                                            }
                                            preferences.setCountdownSeconds(next)
                                            configState = preferences.getConfig()
                                        },
                                        onToggleFloatingOverlay = {
                                            val next = !configState.floatingOverlayEnabled
                                            preferences.setFloatingOverlayEnabled(next)
                                            configState = preferences.getConfig()
                                            if (next && !Settings.canDrawOverlays(this@MainActivity)) {
                                                promptOverlayPermission()
                                            }
                                        },
                                        onToggleFloatingPen = {
                                            if (isFloatingPenActive) {
                                                FloatingDrawService.stopService(this@MainActivity)
                                            } else {
                                                FloatingDrawService.startService(this@MainActivity)
                                            }
                                        },
                                        onOpenInAppDrawing = {
                                            showInAppDrawingDialog = true
                                        },
                                        onOpenQuickTileGuide = {
                                            showTileGuide = true
                                        },
                                        onOpenGalleryClicked = {
                                            selectedTab = ScreenTab.GALLERY
                                            loadRecordings()
                                        }
                                    )
                                }
                                ScreenTab.GALLERY -> {
                                    GalleryScreen(
                                        recordings = recordingsList,
                                        onRefresh = { loadRecordings() },
                                        onDeleteRecording = { item ->
                                            lifecycleScope.launch(Dispatchers.IO) {
                                                GalleryMediaManager.deleteRecording(this@MainActivity, item.uri)
                                                loadRecordings()
                                            }
                                        }
                                    )
                                }
                                ScreenTab.SETTINGS -> {
                                    SettingsScreen(
                                        config = configState,
                                        onResolutionChanged = {
                                            preferences.setResolution(it)
                                            configState = preferences.getConfig()
                                        },
                                        onFpsChanged = {
                                            preferences.setFps(it)
                                            configState = preferences.getConfig()
                                        },
                                        onBitrateChanged = {
                                            preferences.setBitrate(it)
                                            configState = preferences.getConfig()
                                        },
                                        onAudioOptionChanged = {
                                            preferences.setAudioOption(it)
                                            configState = preferences.getConfig()
                                        },
                                        onCountdownChanged = {
                                            preferences.setCountdownSeconds(it)
                                            configState = preferences.getConfig()
                                        },
                                        onFloatingOverlayToggle = {
                                            preferences.setFloatingOverlayEnabled(it)
                                            configState = preferences.getConfig()
                                            if (it && !Settings.canDrawOverlays(this@MainActivity)) {
                                                promptOverlayPermission()
                                            }
                                        },
                                        onOpenTileGuide = {
                                            showTileGuide = true
                                        }
                                    )
                                }
                            }
                        }

                        if (showTileGuide) {
                            QuickTileGuideSheet(
                                onDismiss = { showTileGuide = false },
                                onTestTileAction = {
                                    initiateRecording()
                                }
                            )
                        }

                        if (showInAppDrawingDialog) {
                            InAppDrawingDialog(
                                onDismiss = { showInAppDrawingDialog = false }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadRecordings()
    }

    private fun loadRecordings() {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                GalleryMediaManager.queryGalleryRecordings(this@MainActivity)
            }
            recordingsList = list
        }
    }

    private fun initiateRecording() {
        // On Android 7-9 (API 24-28), WRITE_EXTERNAL_STORAGE is required at runtime to write to DCIM
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        checkNotificationAndProceed()
    }

    private fun checkNotificationAndProceed() {
        // Check Notification Permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        checkAudioAndStartCapture()
    }

    private fun checkAudioAndStartCapture() {
        if (configState.audioOption == AudioOption.MIC) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                return
            }
        }
        startCaptureFlow()
    }

    private fun startCaptureFlow() {
        val mgr = mediaProjectionManager
        if (mgr != null) {
            captureLauncher.launch(mgr.createScreenCaptureIntent())
        } else {
            Toast.makeText(this, "Screen capture not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun promptOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun stopRecordingService() {
        val intent = ScreenRecordService.stopIntent(this)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun pauseOrResumeRecordingService(isPaused: Boolean) {
        val intent = if (isPaused) {
            ScreenRecordService.resumeIntent(this)
        } else {
            ScreenRecordService.pauseIntent(this)
        }
        ContextCompat.startForegroundService(this, intent)
    }
}
