package ir.voxfa.keyboard

import android.content.Context
import android.graphics.*
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

class SuggestionBarView(
    context: Context,
    private val callback: Callback
) : View(context) {

    interface Callback {
        fun onSuggestion(text: String)
        fun onMic()
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(55, 58, 64)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans", Typeface.NORMAL)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(210, 214, 220)
        strokeWidth = dp(1f)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(2f)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    var suggestions: List<String> = emptyList()
        set(value) { field = value.take(3); invalidate() }
    var voiceActive: Boolean = false
        set(value) { field = value; invalidate() }
    var statusText: String = ""
        set(value) { field = value; invalidate() }
    var partialText: String = ""
        set(value) { field = value; invalidate() }

    private var pressedZone = -1

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(dp(48f).toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(238, 241, 245))
        val micW = dp(52f)
        val contentRight = width - micW

        if (voiceActive) {
            val shown = partialText.ifBlank { statusText.ifBlank { "در حال شنیدن…" } }
            textPaint.textSize = dp(14f)
            textPaint.color = Color.rgb(60, 64, 67)
            canvas.drawText(ellipsize(shown, contentRight - dp(24f)), contentRight/2f, centerBaseline(textPaint), textPaint)
        } else {
            val zoneW = contentRight / 3f
            repeat(3) { i ->
                if (i > 0) canvas.drawLine(i*zoneW, dp(12f), i*zoneW, height-dp(12f), linePaint)
                val s = suggestions.getOrNull(i).orEmpty()
                if (s.isNotBlank()) {
                    textPaint.textSize = dp(14.5f)
                    textPaint.color = Color.rgb(45, 48, 54)
                    canvas.drawText(ellipsize(s, zoneW-dp(12f)), i*zoneW+zoneW/2f, centerBaseline(textPaint), textPaint)
                }
            }
        }

        if (pressedZone == 3) {
            fillPaint.color = Color.argb(24, 0, 0, 0)
            canvas.drawCircle(width-micW/2f, height/2f, dp(19f), fillPaint)
        }
        drawMic(canvas, width-micW/2f, height/2f, if (voiceActive) Color.rgb(217,48,37) else Color.rgb(60,64,67))
    }

    private fun drawMic(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        iconPaint.color = color
        iconPaint.strokeWidth = dp(2f)
        val mic = RectF(cx-dp(4.5f), cy-dp(9f), cx+dp(4.5f), cy+dp(3f))
        canvas.drawRoundRect(mic, dp(5f), dp(5f), iconPaint)
        val p = Path().apply {
            moveTo(cx-dp(8f), cy+dp(1f))
            quadTo(cx-dp(8f), cy+dp(9f), cx, cy+dp(9f))
            quadTo(cx+dp(8f), cy+dp(9f), cx+dp(8f), cy+dp(1f))
            moveTo(cx, cy+dp(9f))
            lineTo(cx, cy+dp(14f))
            moveTo(cx-dp(5f), cy+dp(14f))
            lineTo(cx+dp(5f), cy+dp(14f))
        }
        canvas.drawPath(p, iconPaint)
    }

    private fun centerBaseline(p: Paint): Float = height/2f - (p.ascent()+p.descent())/2f

    private fun ellipsize(text: String, maxWidth: Float): String {
        if (textPaint.measureText(text) <= maxWidth) return text
        var s = text
        while (s.length > 1 && textPaint.measureText("$s…") > maxWidth) s = s.dropLast(1)
        return "$s…"
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val micW = dp(52f)
        val contentRight = width - micW
        val zone = if (event.x >= contentRight) 3 else ((event.x / (contentRight/3f)).toInt()).coerceIn(0,2)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedZone = zone
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                invalidate(); return true
            }
            MotionEvent.ACTION_UP -> {
                val down = pressedZone
                pressedZone = -1
                invalidate()
                if (down == zone) {
                    if (zone == 3) callback.onMic()
                    else if (!voiceActive) suggestions.getOrNull(zone)?.takeIf { it.isNotBlank() }?.let(callback::onSuggestion)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> { pressedZone = -1; invalidate(); return true }
        }
        return true
    }
}
