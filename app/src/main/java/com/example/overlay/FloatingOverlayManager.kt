package com.example.overlay

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class FloatingOverlayManager(
    private val context: Context,
    private val onPauseResumeClicked: () -> Unit,
    private val onStopClicked: () -> Unit,
    private val onMuteToggleClicked: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var floatingPillView: View? = null
    private var drawingOverlayView: DrawingOverlayView? = null

    private var pillParams: WindowManager.LayoutParams? = null
    private var isExpanded = false
    private var isDrawingActive = false
    private var isMuted = false

    private var timerTextView: TextView? = null
    private var redDotView: View? = null
    private var pauseIconView: TextView? = null
    private var expandBarView: LinearLayout? = null

    companion object {
        private const val TAG = "FloatingOverlayManager"
    }

    fun show() {
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Overlay permission not granted; cannot show floating controls")
            return
        }

        if (floatingPillView != null) return

        try {
            buildFloatingPill()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing floating pill", e)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildFloatingPill() {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        pillParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 200
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6))
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#EE181A20"))
                cornerRadius = dpToPx(24).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#33FFFFFF"))
            }
            background = bg
            elevation = dpToPx(8).toFloat()
        }

        // Flashing Red Dot
        val redDot = View(context).apply {
            val dot = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF3B30"))
            }
            background = dot
            val lp = LinearLayout.LayoutParams(dpToPx(10), dpToPx(10)).apply {
                rightMargin = dpToPx(6)
            }
            layoutParams = lp
        }
        redDotView = redDot
        root.addView(redDot)

        // Timer text
        val timerTv = TextView(context).apply {
            text = "00:00"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
        }
        timerTextView = timerTv
        root.addView(timerTv)

        // Controls bar (visible by default so users immediately see Draw & Save buttons)
        val expandBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.VISIBLE
        }
        expandBarView = expandBar

        // Pause/Resume button
        val pauseBtn = createControlButton("⏸") {
            onPauseResumeClicked()
        }
        pauseIconView = pauseBtn

        // Stop & Save button
        val stopBtn = createControlButton("⏹") {
            onStopClicked()
        }

        // Markup / Highlighter / Draw tool button (distinctive cyan/yellow highlight)
        val drawBtn = TextView(context).apply {
            text = "🖊️ Draw"
            textSize = 12f
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#3300E5FF"))
                cornerRadius = dpToPx(14).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#8800E5FF"))
            }
            background = bg
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            setOnClickListener {
                toggleDrawingOverlay()
            }
        }

        // Mic mute toggle
        lateinit var micBtn: TextView
        micBtn = createControlButton("🎙") {
            isMuted = !isMuted
            micBtn.text = if (isMuted) "🔇" else "🎙"
            onMuteToggleClicked()
        }

        expandBar.addView(createVerticalDivider())
        expandBar.addView(pauseBtn)
        expandBar.addView(stopBtn)
        expandBar.addView(drawBtn)
        expandBar.addView(micBtn)

        root.addView(expandBar)

        // Touch listener for dragging and tapping
        root.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isClick = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val params = pillParams ?: return false
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
                        windowManager.updateViewLayout(root, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            toggleExpand()
                        } else {
                            // Snap to nearest edge
                            val screenWidth = context.resources.displayMetrics.widthPixels
                            if (params.x < screenWidth / 2) {
                                params.x = 24
                            } else {
                                params.x = screenWidth - root.width - 24
                            }
                            windowManager.updateViewLayout(root, params)
                        }
                        return true
                    }
                }
                return false
            }
        })

        floatingPillView = root
        windowManager.addView(root, pillParams)
    }

    private fun toggleExpand() {
        isExpanded = !isExpanded
        expandBarView?.visibility = if (isExpanded) View.VISIBLE else View.GONE
    }

    private fun toggleDrawingOverlay() {
        if (isDrawingActive) {
            hideDrawingOverlay()
        } else {
            showDrawingOverlay()
        }
    }

    private fun showDrawingOverlay() {
        if (!Settings.canDrawOverlays(context)) return
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

        val drawingView = DrawingOverlayView(context) {
            hideDrawingOverlay()
        }

        drawingOverlayView = drawingView
        isDrawingActive = true
        windowManager.addView(drawingView, drawParams)
    }

    private fun hideDrawingOverlay() {
        drawingOverlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing drawing view", e)
            }
        }
        drawingOverlayView = null
        isDrawingActive = false
    }

    fun updateTimer(seconds: Long, isPaused: Boolean) {
        val mins = seconds / 60
        val secs = seconds % 60
        val timeStr = String.format("%02d:%02d", mins, secs)
        timerTextView?.text = timeStr

        pauseIconView?.text = if (isPaused) "▶" else "⏸"
        redDotView?.alpha = if (isPaused) 0.3f else if (seconds % 2 == 0L) 1.0f else 0.4f
    }

    fun dismiss() {
        hideDrawingOverlay()
        floatingPillView?.let { pill ->
            try {
                windowManager.removeView(pill)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating pill", e)
            }
        }
        floatingPillView = null
    }

    private fun createControlButton(symbol: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = symbol
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            setOnClickListener { onClick() }
        }
    }

    private fun createVerticalDivider(): View {
        return View(context).apply {
            setBackgroundColor(Color.parseColor("#33FFFFFF"))
            val lp = LinearLayout.LayoutParams(dpToPx(1), dpToPx(18)).apply {
                leftMargin = dpToPx(6)
                rightMargin = dpToPx(6)
            }
            layoutParams = lp
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
