package ir.voxfa.keyboard

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import java.util.Locale
import kotlin.math.min

class KeyboardCanvasView(
    context: Context,
    private val callback: Callback
) : View(context) {

    enum class Language { PERSIAN, ENGLISH }
    enum class Mode { LETTERS, SYMBOLS }
    enum class Action { TEXT, BACKSPACE, SHIFT, LANGUAGE, SYMBOLS, LETTERS, SPACE, ENTER }

    data class Key(
        val label: String,
        val action: Action = Action.TEXT,
        val value: String = label,
        val weight: Float = 1f,
        val kind: Kind = Kind.NORMAL
    )

    enum class Kind { NORMAL, SPECIAL, ACTION }

    interface Callback {
        fun onKey(key: Key)
        fun onBackspaceRepeat()
    }

    var language: Language = Language.PERSIAN
        set(value) { field = value; shift = false; rebuild() }
    var mode: Mode = Mode.LETTERS
        set(value) { field = value; shift = false; rebuild() }
    var shift: Boolean = false
        set(value) { field = value; rebuild() }

    private data class PlacedKey(val key: Key, val rect: RectF)

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val placed = mutableListOf<PlacedKey>()
    private var activeIndex = -1
    private var previewIndex = -1
    private val handler = Handler(Looper.getMainLooper())
    private var backspaceRepeating = false

    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (backspaceRepeating) {
                callback.onBackspaceRepeat()
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                handler.postDelayed(this, 58)
            }
        }
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = dp(218f).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(desired, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = rebuild()

    private fun rebuild() {
        if (width <= 0 || height <= 0) { invalidate(); return }
        placed.clear()
        val rows = rows()
        val rowGap = dp(5f)
        val horizontalPadding = dp(4f)
        val rowHeight = (height - rowGap * (rows.size - 1)) / rows.size.toFloat()
        var y = 0f
        rows.forEachIndexed { index, row ->
            val inset = when {
                mode == Mode.LETTERS && language == Language.ENGLISH && index == 1 -> dp(14f)
                else -> 0f
            }
            layoutRow(row, horizontalPadding + inset, y, width - horizontalPadding - inset, y + rowHeight)
            y += rowHeight + rowGap
        }
        invalidate()
    }

    private fun layoutRow(row: List<Key>, left: Float, top: Float, right: Float, bottom: Float) {
        val gap = dp(4f)
        val totalWeight = row.sumOf { it.weight.toDouble() }.toFloat()
        val usable = (right - left) - gap * (row.size - 1)
        var x = left
        row.forEach { key ->
            val keyWidth = usable * (key.weight / totalWeight)
            placed += PlacedKey(key, RectF(x, top, x + keyWidth, bottom))
            x += keyWidth + gap
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(COLOR_SURFACE)
        placed.forEachIndexed { index, pk -> drawKey(canvas, pk, index == activeIndex) }
        if (previewIndex >= 0 && previewIndex < placed.size) drawPreview(canvas, placed[previewIndex])
    }

    private fun drawKey(canvas: Canvas, pk: PlacedKey, pressed: Boolean) {
        val rect = RectF(pk.rect)
        val radius = dp(7f)
        val baseColor = when (pk.key.kind) {
            Kind.NORMAL -> COLOR_KEY
            Kind.SPECIAL -> COLOR_SPECIAL
            Kind.ACTION -> COLOR_ACTION
        }
        bgPaint.color = if (pressed) blend(baseColor, Color.BLACK, 0.10f) else baseColor
        bgPaint.style = Paint.Style.FILL
        bgPaint.setShadowLayer(if (pk.key.kind == Kind.NORMAL && !pressed) dp(0.8f) else 0f, 0f, dp(0.6f), 0x26000000)
        canvas.drawRoundRect(rect, radius, radius, bgPaint)
        bgPaint.clearShadowLayer()

        val centerX = rect.centerX()
        val centerY = rect.centerY()
        val color = if (pk.key.kind == Kind.ACTION) Color.WHITE else COLOR_TEXT

        when (pk.key.action) {
            Action.BACKSPACE -> drawBackspace(canvas, centerX, centerY, color)
            Action.SHIFT -> drawShift(canvas, centerX, centerY, color, shift)
            Action.LANGUAGE -> drawGlobe(canvas, centerX, centerY, color)
            Action.ENTER -> drawEnter(canvas, centerX, centerY, color)
            Action.SPACE -> drawSpace(canvas, rect, color)
            else -> {
                textPaint.color = color
                textPaint.textSize = when {
                    pk.key.label.length >= 5 -> dp(12.5f)
                    pk.key.label.length >= 3 -> dp(13.5f)
                    else -> dp(20f)
                }
                val baseline = centerY - (textPaint.ascent() + textPaint.descent()) / 2f
                canvas.drawText(pk.key.label, centerX, baseline, textPaint)
            }
        }
    }

    private fun drawSpace(canvas: Canvas, rect: RectF, color: Int) {
        textPaint.color = color
        textPaint.textSize = dp(11.5f)
        val label = if (language == Language.PERSIAN) "فارسی" else "English"
        val baseline = rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(label, rect.centerX(), baseline, textPaint)
    }

    private fun drawBackspace(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        iconPaint.color = color
        iconPaint.strokeWidth = dp(1.8f)
        val w = dp(21f); val h = dp(14f)
        val path = Path().apply {
            moveTo(cx - w/2, cy)
            lineTo(cx - w/2 + dp(6f), cy - h/2)
            lineTo(cx + w/2, cy - h/2)
            lineTo(cx + w/2, cy + h/2)
            lineTo(cx - w/2 + dp(6f), cy + h/2)
            close()
        }
        canvas.drawPath(path, iconPaint)
        canvas.drawLine(cx + dp(1f), cy - dp(3.5f), cx + dp(7f), cy + dp(3.5f), iconPaint)
        canvas.drawLine(cx + dp(7f), cy - dp(3.5f), cx + dp(1f), cy + dp(3.5f), iconPaint)
    }

    private fun drawShift(canvas: Canvas, cx: Float, cy: Float, color: Int, active: Boolean) {
        iconPaint.color = color
        iconPaint.strokeWidth = dp(2f)
        iconPaint.style = if (active) Paint.Style.FILL else Paint.Style.STROKE
        val p = Path().apply {
            moveTo(cx, cy - dp(9f))
            lineTo(cx - dp(8f), cy - dp(1f))
            lineTo(cx - dp(4f), cy - dp(1f))
            lineTo(cx - dp(4f), cy + dp(8f))
            lineTo(cx + dp(4f), cy + dp(8f))
            lineTo(cx + dp(4f), cy - dp(1f))
            lineTo(cx + dp(8f), cy - dp(1f))
            close()
        }
        canvas.drawPath(p, iconPaint)
        iconPaint.style = Paint.Style.STROKE
    }

    private fun drawGlobe(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        iconPaint.color = color
        iconPaint.strokeWidth = dp(1.5f)
        val r = dp(8.5f)
        canvas.drawCircle(cx, cy, r, iconPaint)
        canvas.drawOval(RectF(cx-r/2.3f, cy-r, cx+r/2.3f, cy+r), iconPaint)
        canvas.drawLine(cx-r, cy, cx+r, cy, iconPaint)
    }

    private fun drawEnter(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        iconPaint.color = color
        iconPaint.strokeWidth = dp(2.1f)
        val p = Path().apply {
            moveTo(cx + dp(9f), cy - dp(7f))
            lineTo(cx + dp(9f), cy - dp(1f))
            quadTo(cx + dp(9f), cy + dp(5f), cx + dp(3f), cy + dp(5f))
            lineTo(cx - dp(8f), cy + dp(5f))
            moveTo(cx - dp(8f), cy + dp(5f))
            lineTo(cx - dp(3f), cy)
            moveTo(cx - dp(8f), cy + dp(5f))
            lineTo(cx - dp(3f), cy + dp(10f))
        }
        canvas.drawPath(p, iconPaint)
    }

    private fun drawPreview(canvas: Canvas, pk: PlacedKey) {
        if (pk.key.action != Action.TEXT || pk.key.label.length > 2) return
        val w = min(dp(56f), width * 0.16f)
        val h = dp(56f)
        val cx = pk.rect.centerX().coerceIn(w/2 + dp(2f), width - w/2 - dp(2f))
        val bottom = (pk.rect.top - dp(4f)).coerceAtLeast(h + dp(2f))
        val r = RectF(cx-w/2, bottom-h, cx+w/2, bottom)
        bgPaint.color = Color.WHITE
        bgPaint.style = Paint.Style.FILL
        bgPaint.setShadowLayer(dp(3f), 0f, dp(1f), 0x45000000)
        canvas.drawRoundRect(r, dp(10f), dp(10f), bgPaint)
        bgPaint.clearShadowLayer()
        textPaint.color = COLOR_TEXT
        textPaint.textSize = dp(26f)
        val baseline = r.centerY() - (textPaint.ascent() + textPaint.descent())/2f
        canvas.drawText(pk.key.label, r.centerX(), baseline, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val idx = placed.indexOfFirst { it.rect.contains(event.x, event.y) }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeIndex = idx
                previewIndex = idx
                if (idx >= 0) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (placed[idx].key.action == Action.BACKSPACE) {
                        backspaceRepeating = true
                        callback.onKey(placed[idx].key)
                        handler.postDelayed(repeatRunnable, 360)
                    }
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (idx != activeIndex) {
                    activeIndex = idx
                    previewIndex = idx
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(repeatRunnable)
                val upIndex = idx
                val downIndex = activeIndex
                backspaceRepeating = false
                activeIndex = -1
                previewIndex = -1
                invalidate()
                if (upIndex >= 0 && upIndex == downIndex) {
                    val key = placed[upIndex].key
                    if (key.action != Action.BACKSPACE) callback.onKey(key)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(repeatRunnable)
                backspaceRepeating = false
                activeIndex = -1
                previewIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    private fun rows(): List<List<Key>> {
        if (mode == Mode.SYMBOLS) return symbolRows()
        return if (language == Language.ENGLISH) englishRows() else persianRows()
    }

    private fun englishRows(): List<List<Key>> {
        fun letter(s: String) = Key(if (shift) s.uppercase(Locale.US) else s)
        return listOf(
            "qwertyuiop".map { letter(it.toString()) },
            "asdfghjkl".map { letter(it.toString()) },
            listOf(Key("", Action.SHIFT, kind = Kind.SPECIAL, weight = 1.25f)) +
                "zxcvbnm".map { letter(it.toString()) } +
                Key("", Action.BACKSPACE, kind = Kind.SPECIAL, weight = 1.25f),
            listOf(
                Key("?123", Action.SYMBOLS, kind = Kind.SPECIAL, weight = 1.25f),
                Key(",", value = ",", weight = 0.85f),
                Key("", Action.LANGUAGE, kind = Kind.SPECIAL, weight = 0.95f),
                Key("space", Action.SPACE, kind = Kind.NORMAL, weight = 3.9f),
                Key(".", value = ".", weight = 0.85f),
                Key("", Action.ENTER, kind = Kind.ACTION, weight = 1.25f)
            )
        )
    }

    private fun persianRows(): List<List<Key>> {
        fun keys(list: List<String>) = list.map { Key(it) }
        return listOf(
            keys(listOf("چ","ج","ح","خ","ه","ع","غ","ف","ق","ث","ص","ض")),
            keys(listOf("گ","ک","م","ن","ت","ا","ل","ب","ی","س","ش")),
            listOf(Key("؟", value = "؟", kind = Kind.SPECIAL, weight = 1.05f)) +
                keys(listOf("ژ","و","پ","د","ذ","ر","ز","ط","ظ")) +
                Key("", Action.BACKSPACE, kind = Kind.SPECIAL, weight = 1.25f),
            listOf(
                Key("۱۲۳", Action.SYMBOLS, kind = Kind.SPECIAL, weight = 1.25f),
                Key("،", value = "،", weight = 0.85f),
                Key("", Action.LANGUAGE, kind = Kind.SPECIAL, weight = 0.95f),
                Key("فاصله", Action.SPACE, kind = Kind.NORMAL, weight = 3.9f),
                Key(".", value = ".", weight = 0.85f),
                Key("", Action.ENTER, kind = Kind.ACTION, weight = 1.25f)
            )
        )
    }

    private fun symbolRows(): List<List<Key>> {
        val digits = if (language == Language.PERSIAN)
            listOf("۱","۲","۳","۴","۵","۶","۷","۸","۹","۰")
        else listOf("1","2","3","4","5","6","7","8","9","0")
        return listOf(
            digits.map { Key(it) },
            listOf("@","#","%","&","-","+","(", ")","/",":").map { Key(it) },
            listOf(
                Key("=", weight = 1f), Key("_", weight = 1f), Key("\"", value = "\"", weight = 1f),
                Key("'", weight = 1f), Key(";", weight = 1f), Key("!", weight = 1f),
                Key("?", weight = 1f), Key("", Action.BACKSPACE, kind = Kind.SPECIAL, weight = 1.35f)
            ),
            listOf(
                Key(if (language == Language.PERSIAN) "اب‌پ" else "ABC", Action.LETTERS, kind = Kind.SPECIAL, weight = 1.35f),
                Key(",", value = if (language == Language.PERSIAN) "،" else ",", weight = 0.85f),
                Key("", Action.LANGUAGE, kind = Kind.SPECIAL, weight = 0.95f),
                Key("space", Action.SPACE, kind = Kind.NORMAL, weight = 3.9f),
                Key(".", value = ".", weight = 0.85f),
                Key("", Action.ENTER, kind = Kind.ACTION, weight = 1.25f)
            )
        )
    }

    private fun blend(c1: Int, c2: Int, ratio: Float): Int {
        val inv = 1f - ratio
        return Color.rgb(
            (Color.red(c1)*inv + Color.red(c2)*ratio).toInt(),
            (Color.green(c1)*inv + Color.green(c2)*ratio).toInt(),
            (Color.blue(c1)*inv + Color.blue(c2)*ratio).toInt()
        )
    }

    companion object {
        private val COLOR_SURFACE = Color.rgb(238, 241, 245)
        private val COLOR_KEY = Color.rgb(253, 253, 254)
        private val COLOR_SPECIAL = Color.rgb(218, 223, 230)
        private val COLOR_ACTION = Color.rgb(26, 115, 232)
        private val COLOR_TEXT = Color.rgb(32, 33, 36)
    }
}
