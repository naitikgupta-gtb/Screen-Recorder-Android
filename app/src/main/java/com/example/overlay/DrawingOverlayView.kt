package com.example.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat

class DrawingOverlayView(
    context: Context,
    private val onDismiss: () -> Unit
) : FrameLayout(context) {

    enum class ToolMode {
        PEN, HIGHLIGHTER, ERASER
    }

    private data class Stroke(
        val path: Path,
        val color: Int,
        val strokeWidth: Float,
        val isHighlighter: Boolean
    )

    private val strokes = mutableListOf<Stroke>()
    private val undoStack = mutableListOf<Stroke>()
    private var currentPath = Path()
    private var currentMode = ToolMode.PEN
    private var currentColor = Color.parseColor("#FF5252") // Default vivid red pen
    private var highlighterColor = Color.parseColor("#66FFEB3B") // Translucent highlighter yellow
    private var currentStrokeWidth = 10f
    private val highlighterWidth = 36f

    private val canvasPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val drawingCanvasView: View

    init {
        // Transparent background so the underlying screen is visible
        setBackgroundColor(Color.TRANSPARENT)

        // Custom canvas view
        drawingCanvasView = object : View(context) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                // Draw completed strokes
                for (stroke in strokes) {
                    canvasPaint.color = stroke.color
                    canvasPaint.strokeWidth = stroke.strokeWidth
                    if (stroke.isHighlighter) {
                        canvasPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
                    } else {
                        canvasPaint.xfermode = null
                    }
                    canvas.drawPath(stroke.path, canvasPaint)
                }

                // Draw active stroke
                if (!currentPath.isEmpty) {
                    val isHighlight = currentMode == ToolMode.HIGHLIGHTER
                    val color = when (currentMode) {
                        ToolMode.HIGHLIGHTER -> highlighterColor
                        ToolMode.ERASER -> Color.parseColor("#44FFFFFF")
                        ToolMode.PEN -> currentColor
                    }
                    val width = if (isHighlight) highlighterWidth else currentStrokeWidth
                    canvasPaint.color = color
                    canvasPaint.strokeWidth = width
                    canvasPaint.xfermode = null
                    canvas.drawPath(currentPath, canvasPaint)
                }
            }

            private var lastX = 0f
            private var lastY = 0f

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouchEvent(event: MotionEvent): Boolean {
                val x = event.x
                val y = event.y

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = x
                        lastY = y
                        currentPath.reset()
                        currentPath.moveTo(x, y)
                        invalidate()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = Math.abs(x - lastX)
                        val dy = Math.abs(y - lastY)
                        if (dx >= 4 || dy >= 4) {
                            currentPath.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2)
                            lastX = x
                            lastY = y
                            invalidate()
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        currentPath.lineTo(x, y)
                        if (currentMode == ToolMode.ERASER) {
                            // Find and remove strokes close to this path or clear last
                            if (strokes.isNotEmpty()) {
                                strokes.removeAt(strokes.lastIndex)
                            }
                        } else {
                            val isHighlight = currentMode == ToolMode.HIGHLIGHTER
                            val color = if (isHighlight) highlighterColor else currentColor
                            val width = if (isHighlight) highlighterWidth else currentStrokeWidth
                            strokes.add(
                                Stroke(
                                    path = Path(currentPath),
                                    color = color,
                                    strokeWidth = width,
                                    isHighlighter = isHighlight
                                )
                            )
                        }
                        currentPath.reset()
                        invalidate()
                        return true
                    }
                }
                return false
            }
        }

        addView(
            drawingCanvasView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        // Build the sleek floating toolbar at top
        setupToolbar()
    }

    private fun setupToolbar() {
        val toolbarContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#EE1E1F2A"))
                cornerRadius = dpToPx(28).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#44FFFFFF"))
            }
            background = bg
            elevation = dpToPx(8).toFloat()
        }

        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dpToPx(48)
        }

        // Pen Button
        val penBtn = createIconButton("✏️", "Pen") {
            currentMode = ToolMode.PEN
            updateToolSelection()
        }

        // Highlighter Button
        val highlighterBtn = createIconButton("🖊️", "Highlighter") {
            currentMode = ToolMode.HIGHLIGHTER
            updateToolSelection()
        }

        // Color buttons (Red, Yellow, Cyan, Green, White)
        val colorRed = createColorDot(Color.parseColor("#FF5252")) {
            currentColor = Color.parseColor("#FF5252")
            highlighterColor = Color.parseColor("#66FF5252")
            if (currentMode == ToolMode.ERASER) currentMode = ToolMode.PEN
        }
        val colorYellow = createColorDot(Color.parseColor("#FFEB3B")) {
            currentColor = Color.parseColor("#FFD600")
            highlighterColor = Color.parseColor("#66FFEB3B")
            if (currentMode == ToolMode.ERASER) currentMode = ToolMode.PEN
        }
        val colorCyan = createColorDot(Color.parseColor("#00E5FF")) {
            currentColor = Color.parseColor("#00E5FF")
            highlighterColor = Color.parseColor("#6600E5FF")
            if (currentMode == ToolMode.ERASER) currentMode = ToolMode.PEN
        }
        val colorGreen = createColorDot(Color.parseColor("#00E676")) {
            currentColor = Color.parseColor("#00E676")
            highlighterColor = Color.parseColor("#6600E676")
            if (currentMode == ToolMode.ERASER) currentMode = ToolMode.PEN
        }

        // Eraser Button
        val eraserBtn = createIconButton("🧹", "Eraser") {
            currentMode = ToolMode.ERASER
            updateToolSelection()
        }

        // Undo Button
        val undoBtn = createIconButton("↩️", "Undo") {
            if (strokes.isNotEmpty()) {
                undoStack.add(strokes.removeAt(strokes.lastIndex))
                drawingCanvasView.invalidate()
            }
        }

        // Clear All Button
        val clearBtn = createIconButton("🗑️", "Clear") {
            strokes.clear()
            drawingCanvasView.invalidate()
        }

        // Close / Done Button
        val closeBtn = createIconButton("✕", "Close") {
            onDismiss()
        }

        toolbarContainer.addView(penBtn)
        toolbarContainer.addView(highlighterBtn)
        addDivider(toolbarContainer)
        toolbarContainer.addView(colorRed)
        toolbarContainer.addView(colorYellow)
        toolbarContainer.addView(colorCyan)
        toolbarContainer.addView(colorGreen)
        addDivider(toolbarContainer)
        toolbarContainer.addView(eraserBtn)
        toolbarContainer.addView(undoBtn)
        toolbarContainer.addView(clearBtn)
        addDivider(toolbarContainer)
        toolbarContainer.addView(closeBtn)

        addView(toolbarContainer, lp)
    }

    private fun updateToolSelection() {
        // visual update if needed
    }

    private fun addDivider(container: LinearLayout) {
        val div = View(context).apply {
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#33FFFFFF"))
            }
            background = bg
        }
        val lp = LinearLayout.LayoutParams(dpToPx(1), dpToPx(24)).apply {
            leftMargin = dpToPx(6)
            rightMargin = dpToPx(6)
        }
        container.addView(div, lp)
    }

    private fun createIconButton(emoji: String, tooltip: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = emoji
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
            contentDescription = tooltip
            setOnClickListener { onClick() }
        }
    }

    private fun createColorDot(color: Int, onClick: () -> Unit): View {
        return View(context).apply {
            val dot = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(dpToPx(1), Color.WHITE)
            }
            background = dot
            val lp = LinearLayout.LayoutParams(dpToPx(20), dpToPx(20)).apply {
                leftMargin = dpToPx(4)
                rightMargin = dpToPx(4)
            }
            layoutParams = lp
            setOnClickListener { onClick() }
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
