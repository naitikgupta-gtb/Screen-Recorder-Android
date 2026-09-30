package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AudioOption
import com.example.data.RecordingPreferences
import com.example.data.VideoResolution
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Screen Recorder", appName)
    }

    @Test
    fun `preferences default countdown is 2 seconds`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = RecordingPreferences(context)
        val config = prefs.getConfig()
        assertEquals(2, config.countdownSeconds)
        assertEquals(VideoResolution.FULL_HD, config.resolution)
        assertEquals(60, config.fps)
        assertEquals(AudioOption.MIC, config.audioOption)
    }
}
