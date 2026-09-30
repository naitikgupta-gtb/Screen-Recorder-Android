package com.example.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class ScreenCapturePermissionActivity : ComponentActivity() {

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
        } else {
            Toast.makeText(this, "Screen record permission canceled", Toast.LENGTH_SHORT).show()
        }
        finish()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (mgr != null) {
            captureLauncher.launch(mgr.createScreenCaptureIntent())
        } else {
            Toast.makeText(this, "Media projection not supported", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
