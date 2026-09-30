package com.example.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.example.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class ScreenRecordTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var statusJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        updateTileState(ScreenRecordService.recordingStatus.value.isRecording)

        statusJob?.cancel()
        statusJob = serviceScope.launch {
            ScreenRecordService.recordingStatus.collectLatest { status ->
                updateTileState(status.isRecording)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        statusJob?.cancel()
    }

    override fun onClick() {
        super.onClick()
        val isRecording = ScreenRecordService.recordingStatus.value.isRecording

        if (isRecording) {
            val stopIntent = ScreenRecordService.stopIntent(this)
            ContextCompat.startForegroundService(this, stopIntent)
            updateTileState(false)
        } else {
            val intent = Intent(this, ScreenCapturePermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }

    private fun updateTileState(isRecording: Boolean) {
        val tile = qsTile ?: return
        if (isRecording) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = getString(R.string.tile_screen_recording)
            tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_stop)
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.tile_screen_record)
            tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_record)
        }
        tile.updateTile()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
