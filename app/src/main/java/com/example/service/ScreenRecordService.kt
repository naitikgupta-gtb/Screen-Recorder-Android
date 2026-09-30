package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File
import com.example.MainActivity
import com.example.R
import com.example.ScreenRecordApplication
import com.example.data.AudioOption
import com.example.data.GalleryMediaManager
import com.example.data.RecordingConfig
import com.example.data.RecordingPreferences
import com.example.overlay.FloatingOverlayManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RecordingStatus(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val durationSeconds: Long = 0,
    val countdownRemaining: Int? = null,
    val currentUri: Uri? = null,
    val lastSavedUri: Uri? = null
)

class ScreenRecordService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null
    private var countdownJob: Job? = null

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var currentTempFile: File? = null
    private var currentPfd: ParcelFileDescriptor? = null
    private var currentVideoUri: Uri? = null

    private var floatingOverlayManager: FloatingOverlayManager? = null
    private lateinit var preferences: RecordingPreferences

    private var durationSeconds: Long = 0
    private var isRecordingStarted = false
    private var isPaused = false
    private var recordingStartTimeMs: Long = 0L

    companion object {
        private const val TAG = "ScreenRecordService"
        const val NOTIFICATION_ID = 101

        const val ACTION_START = "com.example.action.START"
        const val ACTION_STOP = "com.example.action.STOP"
        const val ACTION_PAUSE = "com.example.action.PAUSE"
        const val ACTION_RESUME = "com.example.action.RESUME"
        const val ACTION_TOGGLE_MUTE = "com.example.action.TOGGLE_MUTE"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private val _recordingStatus = MutableStateFlow(RecordingStatus())
        val recordingStatus: StateFlow<RecordingStatus> = _recordingStatus.asStateFlow()

        fun startIntent(context: Context, resultCode: Int, data: Intent): Intent {
            return Intent(context, ScreenRecordService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, ScreenRecordService::class.java).apply {
                action = ACTION_STOP
            }
        }

        fun pauseIntent(context: Context): Intent {
            return Intent(context, ScreenRecordService::class.java).apply {
                action = ACTION_PAUSE
            }
        }

        fun resumeIntent(context: Context): Intent {
            return Intent(context, ScreenRecordService::class.java).apply {
                action = ACTION_RESUME
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferences = RecordingPreferences(this)
        mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultCode != 0 && resultData != null) {
                    startRecordingSequence(resultCode, resultData)
                } else {
                    Log.e(TAG, "Missing MediaProjection data")
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopRecording()
            }
            ACTION_PAUSE -> {
                pauseRecording()
            }
            ACTION_RESUME -> {
                resumeRecording()
            }
            ACTION_TOGGLE_MUTE -> {
                // Toggle mic if supported
            }
        }

        return START_NOT_STICKY
    }

    private fun startRecordingSequence(resultCode: Int, resultData: Intent) {
        val config = preferences.getConfig()
        val countdown = config.countdownSeconds

        // Start foreground notification immediately as required on Android 10+
        val initialNotification = buildNotification("Preparing screen recording…", false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                fgsType
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        // Setup Floating Overlay if enabled
        if (config.floatingOverlayEnabled) {
            floatingOverlayManager = FloatingOverlayManager(
                context = this,
                onPauseResumeClicked = {
                    if (isPaused) resumeRecording() else pauseRecording()
                },
                onStopClicked = {
                    stopRecording()
                },
                onMuteToggleClicked = {
                    // mute state handled in overlay
                }
            )
            floatingOverlayManager?.show()
        }

        // Handle countdown (default 2s as requested)
        if (countdown > 0) {
            countdownJob = serviceScope.launch {
                for (remaining in countdown downTo 1) {
                    _recordingStatus.value = RecordingStatus(
                        isRecording = false,
                        countdownRemaining = remaining
                    )
                    updateNotification("Starting in $remaining s…", false)
                    delay(1000)
                }
                _recordingStatus.value = RecordingStatus(countdownRemaining = null)
                initiateCapture(resultCode, resultData, config)
            }
        } else {
            initiateCapture(resultCode, resultData, config)
        }
    }

    private fun initiateCapture(resultCode: Int, resultData: Intent, config: RecordingConfig) {
        try {
            // Get screen metrics
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)

            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels

            // Keep device's native screen aspect ratio!
            val maxDim = when (config.resolution) {
                com.example.data.VideoResolution.FULL_HD -> 1920
                com.example.data.VideoResolution.HD -> 1280
                com.example.data.VideoResolution.SD -> 854
            }

            var actualWidth: Int
            var actualHeight: Int

            if (screenWidth > screenHeight) {
                // Landscape
                val scale = minOf(1.0, maxDim.toDouble() / screenWidth)
                actualWidth = (screenWidth * scale).toInt()
                actualHeight = (screenHeight * scale).toInt()
            } else {
                // Portrait
                val scale = minOf(1.0, maxDim.toDouble() / screenHeight)
                actualWidth = (screenWidth * scale).toInt()
                actualHeight = (screenHeight * scale).toInt()
            }

            // CRITICAL: Dimensions MUST be multiples of 16 for hardware video encoders
            actualWidth = maxOf(16, (actualWidth / 16) * 16)
            actualHeight = maxOf(16, (actualHeight / 16) * 16)

            val safeFps = minOf(config.fps, 30)
            val safeBitrate = minOf(config.bitrateMbps * 1_000_000, 4_000_000)

            // Direct local file and ParcelFileDescriptor in cacheDir to bypass all SELinux / MediaStore proxy limitations
            val target = GalleryMediaManager.createTempRecordingTarget(this)
            if (target == null) {
                Log.e(TAG, "Failed to create temp recording target")
                stopRecording()
                return
            }
            val (tempFile, pfd) = target
            currentTempFile = tempFile
            currentPfd = pfd

            val canRecordAudio = config.audioOption == AudioOption.MIC &&
                    ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

            // Setup MediaRecorder with resilient fallback
            val recorder = try {
                setupMediaRecorder(
                    pfd = pfd,
                    width = actualWidth,
                    height = actualHeight,
                    fps = safeFps,
                    bitrate = safeBitrate,
                    includeAudio = canRecordAudio
                )
            } catch (e: Exception) {
                Log.w(TAG, "Primary MediaRecorder audio setup failed, retrying video-only", e)
                setupMediaRecorder(
                    pfd = pfd,
                    width = actualWidth,
                    height = actualHeight,
                    fps = minOf(safeFps, 30),
                    bitrate = 2_500_000,
                    includeAudio = false
                )
            }
            mediaRecorder = recorder

            // Obtain MediaProjection
            mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, resultData)
            if (mediaProjection == null) {
                Log.e(TAG, "MediaProjection is null")
                Toast.makeText(this, "Screen capture not supported or cancelled", Toast.LENGTH_SHORT).show()
                stopSelf()
                return
            }

            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection stopped by system")
                    stopRecording()
                }
            }, Handler(Looper.getMainLooper()))

            // 1. Get Surface from prepared MediaRecorder (MUST be before start)
            val surface = mediaRecorder?.surface
            if (surface == null) {
                Log.e(TAG, "MediaRecorder surface is null")
                stopRecording()
                return
            }

            // 2. START MediaRecorder FIRST so the hardware encoder surface is alive
            mediaRecorder?.start()
            isRecordingStarted = true
            recordingStartTimeMs = System.currentTimeMillis()
            isPaused = false
            durationSeconds = 0

            // 3. Create VirtualDisplay attached to the active surface
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenRecordVirtualDisplay",
                actualWidth,
                actualHeight,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null
            )

            // Haptic vibration feedback
            vibrate(100)

            _recordingStatus.value = RecordingStatus(
                isRecording = true,
                isPaused = false,
                durationSeconds = 0,
                currentUri = null
            )

            // Start timer
            startTimer()

        } catch (e: Exception) {
            Log.e(TAG, "Error initiating capture", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, "Recording could not start: ${e.localizedMessage ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            }
            stopRecording()
        }
    }

    private fun setupMediaRecorder(
        pfd: ParcelFileDescriptor,
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int,
        includeAudio: Boolean
    ): MediaRecorder {
        @Suppress("DEPRECATION")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }

        try {
            if (includeAudio) {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            // MANDATORY ORDER: setVideoEncoder MUST follow setOutputFormat before setting dimensions!
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (includeAudio) {
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioSamplingRate(44100)
                recorder.setAudioEncodingBitRate(128000)
            }
            recorder.setVideoSize(width, height)
            recorder.setVideoFrameRate(fps)
            recorder.setVideoEncodingBitRate(bitrate)
            recorder.setOutputFile(pfd.fileDescriptor)
            recorder.prepare()
            return recorder
        } catch (e: Exception) {
            try {
                recorder.reset()
                recorder.release()
            } catch (_: Exception) {}
            throw e
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive && isRecordingStarted) {
                delay(1000)
                if (!isPaused) {
                    durationSeconds++
                    _recordingStatus.value = _recordingStatus.value.copy(
                        durationSeconds = durationSeconds
                    )
                    floatingOverlayManager?.updateTimer(durationSeconds, isPaused)
                    updateNotification(formatDuration(durationSeconds), isPaused)
                }
            }
        }
    }

    private fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isRecordingStarted && !isPaused) {
            try {
                mediaRecorder?.pause()
                isPaused = true
                vibrate(50)
                _recordingStatus.value = _recordingStatus.value.copy(isPaused = true)
                floatingOverlayManager?.updateTimer(durationSeconds, isPaused)
                updateNotification("Recording paused · ${formatDuration(durationSeconds)}", true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pause recording", e)
            }
        }
    }

    private fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isRecordingStarted && isPaused) {
            try {
                mediaRecorder?.resume()
                isPaused = false
                vibrate(50)
                _recordingStatus.value = _recordingStatus.value.copy(isPaused = false)
                floatingOverlayManager?.updateTimer(durationSeconds, isPaused)
                updateNotification(formatDuration(durationSeconds), false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resume recording", e)
            }
        }
    }

    private fun stopRecording() {
        countdownJob?.cancel()
        timerJob?.cancel()

        if (isRecordingStarted) {
            try {
                val elapsed = System.currentTimeMillis() - recordingStartTimeMs
                if (elapsed < 1200) {
                    Thread.sleep(1200 - elapsed)
                }
            } catch (_: Exception) {}

            // 1. Release VirtualDisplay to stop incoming frames
            try {
                virtualDisplay?.release()
            } catch (_: Exception) {}
            virtualDisplay = null

            // 2. Stop MediaRecorder cleanly so MOOV header is written
            try {
                mediaRecorder?.stop()
                Log.d(TAG, "MediaRecorder stopped cleanly and video index written")
            } catch (e: Exception) {
                Log.e(TAG, "MediaRecorder stop failed", e)
            }
        } else {
            try {
                virtualDisplay?.release()
            } catch (_: Exception) {}
            virtualDisplay = null
        }

        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null

        // Close the ParcelFileDescriptor so kernel flushes buffers
        try {
            currentPfd?.close()
        } catch (_: Exception) {}
        currentPfd = null

        floatingOverlayManager?.dismiss()
        floatingOverlayManager = null

        var finalSavedUri: Uri? = null
        val temp = currentTempFile
        currentTempFile = null

        if (temp != null && temp.exists()) {
            val length = temp.length()
            Log.d(TAG, "Recording finished. Temp file size: $length bytes")
            if (length > 1024) {
                finalSavedUri = GalleryMediaManager.saveCompletedVideo(this, temp)
                vibrate(150)
                Toast.makeText(this, "Saved to Movies/ScreenRecorder & Gallery!", Toast.LENGTH_LONG).show()
            } else {
                Log.w(TAG, "Recorded file was 0 bytes, not publishing to gallery")
                Toast.makeText(this, "No video frames captured", Toast.LENGTH_SHORT).show()
            }
            try {
                temp.delete()
            } catch (_: Exception) {}
        }

        _recordingStatus.value = RecordingStatus(
            isRecording = false,
            isPaused = false,
            durationSeconds = durationSeconds,
            lastSavedUri = finalSavedUri
        )

        isRecordingStarted = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(statusText: String, paused: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseResumePending = PendingIntent.getService(
            this,
            2,
            if (paused) resumeIntent(this) else pauseIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseResumeTitle = if (paused) getString(R.string.action_resume) else getString(R.string.action_pause)

        val builder = NotificationCompat.Builder(this, ScreenRecordApplication.CHANNEL_ID)
            .setContentTitle("Screen Recorder")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_tile_record)
            .setContentIntent(openPending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                pauseResumeTitle,
                pauseResumePending
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.action_stop),
                stopPending
            )

        return builder.build()
    }

    private fun updateNotification(statusText: String, paused: Boolean) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(statusText, paused))
    }

    private fun formatDuration(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("Recording: %02d:%02d", mins, secs)
    }

    private fun vibrate(durationMs: Long) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        durationMs,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        floatingOverlayManager?.dismiss()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
