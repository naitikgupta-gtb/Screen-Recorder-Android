package com.example.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.example.overlay.DrawingOverlayView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FloatingDrawService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingPenBubble: View? = null
    private var drawingOverlayView: DrawingOverlayView? = null
    private var isDrawingActive = false

    companion object {
        private const val TAG = "FloatingDrawService"
        const val ACTION_START = "com.example.action.START_FLOATING_PEN"
        const val ACTION_STOP = "com.example.action.STOP_FLOATING_PEN"
        const val ACTION_TOGGLE = "com.example.action.TOGGLE_FLOATING_PEN"

        private val _isFloatingPenActive = MutableStateFlow(false)
        val isFloatingPenActive: StateFlow<Boolean> = _isFloatingPenActive.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, FloatingDrawService::class.java).apply {
                action = ACTION_START
            }
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FloatingDrawService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                showFloatingPenBubble()
            }
            ACTION_STOP -> {
                removeFloatingViews()
                stopSelf()
            }
            ACTION_TOGGLE -> {
                if (floatingPenBubble != null) {
                    removeFloatingViews()
                    stopSelf()
                } else {
                    showFloatingPenBubble()
                }
            }
        }
        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingPenBubble() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot show floating pen without overlay permission")
            stopSelf()
            return
        }

        if (floatingPenBubble != null) return

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 350
        }

        val bubble = FrameLayout(this).apply {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E53935"))
                setStroke(dpToPx(2), Color.WHITE)
            }
            background = bg
            elevation = dpToPx(10).toFloat()
            val size = dpToPx(56)
            layoutParams = FrameLayout.LayoutParams(size, size)
        }

        val iconTv = TextView(this).apply {
            text = "🖊️"
            textSize = 24f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        bubble.addView(iconTv)

        // Drag & Click handler
        bubble.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isClick = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isClick = false
                        }
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager.updateViewLayout(bubble, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            toggleDrawingOverlay()
                        } else {
                            // Snap to edge
                            val screenWidth = resources.displayMetrics.widthPixels
                            params.x = if (params.x < screenWidth / 2) 20 else screenWidth - bubble.width - 20
                            windowManager.updateViewLayout(bubble, params)
                        }
                        return true
                    }
                }
                return false
            }
        })

        floatingPenBubble = bubble
        windowManager.addView(bubble, params)
        _isFloatingPenActive.value = true
    }

    private fun toggleDrawingOverlay() {
        if (isDrawingActive) {
            hideDrawingOverlay()
        } else {
            showDrawingOverlay()
        }
    }

    private fun showDrawingOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        if (drawingOverlayView != null) return

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val drawParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val drawingView = DrawingOverlayView(this) {
            hideDrawingOverlay()
        }

        drawingOverlayView = drawingView
        isDrawingActive = true
        windowManager.addView(drawingView, drawParams)
    }

    private fun hideDrawingOverlay() {
        drawingOverlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing drawing view", e)
            }
        }
        drawingOverlayView = null
        isDrawingActive = false
    }

    private fun removeFloatingViews() {
        hideDrawingOverlay()
        floatingPenBubble?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating pen", e)
            }
        }
        floatingPenBubble = null
        _isFloatingPenActive.value = false
    }

    override fun onDestroy() {
        super.onDestroy()
        removeFloatingViews()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
