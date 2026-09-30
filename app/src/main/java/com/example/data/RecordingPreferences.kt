package com.example.data

import android.content.Context
import android.content.SharedPreferences

enum class VideoResolution(val label: String, val width: Int, val height: Int) {
    FULL_HD("1080p Full HD", 1080, 1920),
    HD("720p HD", 720, 1280),
    SD("480p SD", 480, 854)
}

enum class AudioOption(val label: String, val description: String) {
    MIC("Microphone", "Record voice & surroundings"),
    MUTE("Muted", "Silent video recording")
}

data class RecordingConfig(
    val resolution: VideoResolution = VideoResolution.FULL_HD,
    val fps: Int = 60,
    val bitrateMbps: Int = 8,
    val audioOption: AudioOption = AudioOption.MIC,
    val countdownSeconds: Int = 2,
    val floatingOverlayEnabled: Boolean = true,
    val hideOverlayInRecording: Boolean = false
)

class RecordingPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("screen_recorder_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_RESOLUTION = "resolution"
        private const val KEY_FPS = "fps"
        private const val KEY_BITRATE = "bitrate"
        private const val KEY_AUDIO = "audio_option"
        private const val KEY_COUNTDOWN = "countdown_seconds"
        private const val KEY_FLOATING_OVERLAY = "floating_overlay_enabled"
        private const val KEY_HIDE_OVERLAY = "hide_overlay_in_recording"
    }

    fun getConfig(): RecordingConfig {
        val resName = prefs.getString(KEY_RESOLUTION, VideoResolution.FULL_HD.name)
        val resolution = try {
            VideoResolution.valueOf(resName ?: VideoResolution.FULL_HD.name)
        } catch (_: Exception) {
            VideoResolution.FULL_HD
        }

        val fps = prefs.getInt(KEY_FPS, 60)
        val bitrate = prefs.getInt(KEY_BITRATE, 8)
        val audioName = prefs.getString(KEY_AUDIO, AudioOption.MIC.name)
        val audioOption = try {
            AudioOption.valueOf(audioName ?: AudioOption.MIC.name)
        } catch (_: Exception) {
            AudioOption.MIC
        }
        val countdown = prefs.getInt(KEY_COUNTDOWN, 2)
        val floatingOverlay = prefs.getBoolean(KEY_FLOATING_OVERLAY, true)
        val hideOverlay = prefs.getBoolean(KEY_HIDE_OVERLAY, false)

        return RecordingConfig(
            resolution = resolution,
            fps = fps,
            bitrateMbps = bitrate,
            audioOption = audioOption,
            countdownSeconds = countdown,
            floatingOverlayEnabled = floatingOverlay,
            hideOverlayInRecording = hideOverlay
        )
    }

    fun setResolution(resolution: VideoResolution) {
        prefs.edit().putString(KEY_RESOLUTION, resolution.name).apply()
    }

    fun setFps(fps: Int) {
        prefs.edit().putInt(KEY_FPS, fps).apply()
    }

    fun setBitrate(mbps: Int) {
        prefs.edit().putInt(KEY_BITRATE, mbps).apply()
    }

    fun setAudioOption(option: AudioOption) {
        prefs.edit().putString(KEY_AUDIO, option.name).apply()
    }

    fun setCountdownSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_COUNTDOWN, seconds).apply()
    }

    fun setFloatingOverlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FLOATING_OVERLAY, enabled).apply()
    }

    fun setHideOverlayInRecording(hide: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_OVERLAY, hide).apply()
    }
}
