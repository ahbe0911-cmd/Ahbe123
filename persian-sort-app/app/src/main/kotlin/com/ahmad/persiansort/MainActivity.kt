package com.ahmad.persiansort

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Suppress("DEPRECATION")
class MainActivity : Activity() {

    private val alphabet = listOf(
        'ا','ب','پ','ت','ث','ج','چ','ح','خ','د','ذ','ر','ز','ژ','س','ش',
        'ص','ض','ط','ظ','ع','غ','ف','ق','ک','گ','ل','م','ن','و','ه','ی'
    )
    private val rank = alphabet.withIndex().associate { it.value to it.index }

    private val bg = Color.parseColor("#F7F8FC")
    private val surface = Color.WHITE
    private val primary = Color.parseColor("#5B4CF0")
    private val primaryDark = Color.parseColor("#4738D8")
    private val primarySoft = Color.parseColor("#EFEDFF")
    private val textColor = Color.parseColor("#201D2D")
    private val muted = Color.parseColor("#7B768B")
    private val border = Color.parseColor("#E8E5F2")
    private val borderStrong = Color.parseColor("#D7D1F6")

    private val screenHeightDp: Float by lazy {
        resources.displayMetrics.heightPixels / resources.displayMetrics.density
    }
    private val uiScale: Float by lazy {
        when {
            screenHeightDp < 620f -> 0.72f
            screenHeightDp < 720f -> 0.80f
            screenHeightDp < 820f -> 0.88f
            else -> 1.0f
        }
    }
    private val fontRegular: Typeface by lazy { resources.getFont(R.font.vazirmatn_regular) }
    private val fontBold: Typeface by lazy { resources.getFont(R.font.vazirmatn_semibold) }

    private val inputCards = mutableListOf<InputCard>()
    private var activeIndex = 0
    private var lastLetterView: TextView? = null
    private lateinit var resultCard: LinearLayout
    private lateinit var resultGrid: LinearLayout
    private lateinit var resultHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = surface
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContentView(buildScreen())
    }

    private fun buildScreen(): View {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(bg)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dps(13), dps(7), dps(13), dps(10))
        }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(buildHeader())
        root.addView(space(5))
        root.addView(buildAlphabetCard())
        root.addView(space(7))
        root.addView(label("عبارت‌های مورد مقایسه", 14, muted, true).apply {
            gravity = Gravity.RIGHT
            setPadding(dps(2), 0, dps(2), dps(3))
        })
        root.addView(buildInputs())
        root.addView(space(7))
        root.addView(buildCompareButton())
        root.addView(space(7))
        root.addView(buildResultCard())
        return scroll
    }

    private fun buildHeader(): View {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val title = label("مقایسه حروف و کلمات فارسی", 21, textColor, true).apply {
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            setSingleLine(true)
        }
        top.addView(title, LinearLayout.LayoutParams(0, dps(34), 1f))
        val clear = label("پاک کردن", 12, primary, true).apply {
            gravity = Gravity.CENTER
            setPadding(dps(9), 0, dps(9), 0)
            background = rounded(primarySoft, 14f)
            isClickable = true
            setOnClickListener { clearAll() }
        }
        top.addView(clear, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dps(29)).apply {
            marginStart = dps(7)
        })
        wrap.addView(top)
        wrap.addView(label("مرتب‌سازی دقیق بر اساس ترتیب واقعی الفبای فارسی", 11, muted).apply {
            gravity = Gravity.RIGHT
            setSingleLine(true)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dps(19)))
        return wrap
    }

    private fun buildAlphabetCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dps(8), dps(7), dps(8), dps(7))
            background = rounded(surface, 19f, border, 1)
            elevation = dp(2).toFloat()
        }
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        heading.addView(label("الفبای فارسی", 14, textColor, true).apply { gravity = Gravity.RIGHT }, LinearLayout.LayoutParams(0, dps(21), 1f))
        heading.addView(label("برای درج، حرف را لمس کنید", 10, muted).apply { gravity = Gravity.LEFT }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dps(21)))
        card.addView(heading)
        card.addView(space(3))

        alphabet.chunked(8).forEach { letters ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                gravity = Gravity.CENTER
            }
            letters.forEach { letter ->
                val tile = label(letter.toString(), 18, textColor, true).apply {
                    gravity = Gravity.CENTER
                    background = rounded(Color.parseColor("#FAF9FF"), 12f, border, 1)
                    isClickable = true
                    setOnClickListener { view -> insertLetter(letter, view as TextView) }
                }
                row.addView(tile, LinearLayout.LayoutParams(0, dps(34), 1f).apply { setMargins(dps(2), dps(2), dps(2), dps(2)) })
            }
            card.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dps(38)))
        }
        return card
    }

    private fun buildInputs(): View {
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        for (rowIndex in 0..1) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            }
            for (col in 0..1) {
                val index = rowIndex * 2 + col
                val input = InputCard(this, index)
                inputCards.add(input)
                row.addView(input, LinearLayout.LayoutParams(0, dps(57), 1f).apply { setMargins(dps(3), dps(3), dps(3), dps(3)) })
            }
            grid.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dps(63)))
        }
        inputCards.firstOrNull()?.setActive(true)
        return grid
    }

    private fun buildCompareButton(): View = TextView(this).apply {
        text = "مقایسه و مرتب‌سازی"
        textSize = sp(17)
        setTextColor(Color.WHITE)
        typeface = fontBold
        gravity = Gravity.CENTER
        background = rounded(primary, 17f)
        elevation = dp(4).toFloat()
        isClickable = true
        setOnClickListener {
            animate().scaleX(0.985f).scaleY(0.985f).setDuration(70).withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }.start()
            sortAndShow()
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dps(47))
    }

    private fun buildResultCard(): View {
        resultCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dps(10), dps(8), dps(10), dps(8))
            background = rounded(surface, 19f, border, 1)
            elevation = dp(2).toFloat()
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(label("نتیجه مرتب‌شده", 15, textColor, true).apply { gravity = Gravity.RIGHT }, LinearLayout.LayoutParams(0, dps(25), 1f))
        header.addView(label("الفبای فارسی", 10, primary).apply {
            gravity = Gravity.CENTER
            setPadding(dps(7), 0, dps(7), 0)
            background = rounded(primarySoft, 11f)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dps(22)))
        resultCard.addView(header)

        resultHint = label("پس از وارد کردن موارد، نتیجه اینجا نمایش داده می‌شود.", 12, muted).apply { gravity = Gravity.CENTER }
        resultCard.addView(resultHint, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dps(48)))

        resultGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            visibility = View.GONE
        }
        resultCard.addView(resultGrid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return resultCard
    }

    private fun insertLetter(letter: Char, tile: TextView) {
        val target = inputCards.getOrNull(activeIndex)?.editText ?: return
        val start = target.selectionStart.coerceAtLeast(0)
        val current = target.text ?: return
        current.insert(start.coerceAtMost(current.length), letter.toString())
        target.setSelection((start + 1).coerceAtMost(current.length))
        target.requestFocus()
        lastLetterView?.background = rounded(Color.parseColor("#FAF9FF"), 12f, border, 1)
        lastLetterView?.setTextColor(textColor)
        lastLetterView = tile
        tile.background = rounded(primarySoft, 12f, primary, 1)
        tile.setTextColor(primaryDark)
    }

    private fun sortAndShow() {
        val values = inputCards.map { it.editText.text.toString().trim() }.filter { it.isNotEmpty() }
        if (values.isEmpty()) {
            resultGrid.visibility = View.GONE
            resultHint.visibility = View.VISIBLE
            resultHint.text = "حداقل یک حرف یا کلمه وارد کنید."
            resultHint.setTextColor(Color.parseColor("#B04A56"))
            animateResult()
            return
        }
        val sorted = values.sortedWith(Comparator { a, b -> comparePersian(a, b) })
        resultHint.visibility = View.GONE
        resultGrid.visibility = View.VISIBLE
        resultGrid.removeAllViews()
        sorted.chunked(2).forEachIndexed { rowIndex, pair ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            }
            pair.forEachIndexed { colIndex, value ->
                val globalIndex = rowIndex * 2 + colIndex
                row.addView(resultChip(globalIndex + 1, value), LinearLayout.LayoutParams(0, dps(43), 1f).apply { setMargins(dps(2), dps(2), dps(2), dps(2)) })
            }
            if (pair.size == 1) row.addView(Space(this), LinearLayout.LayoutParams(0, dps(43), 1f))
            resultGrid.addView(row)
        }
        animateResult()
    }

    private fun resultChip(position: Int, value: String): View {
        val chip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dps(6), dps(4), dps(6), dps(4))
            background = rounded(Color.parseColor("#F8F7FF"), 14f, borderStrong, 1)
        }
        val badge = label(position.toString(), 12, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(primary, 13f)
        }
        chip.addView(badge, LinearLayout.LayoutParams(dps(26), dps(26)).apply { marginEnd = dps(6) })
        chip.addView(label(value, 15, textColor, true).apply {
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            maxLines = 1
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        return chip
    }

    private fun animateResult() {
        resultCard.animate().cancel()
        resultCard.alpha = 0.45f
        resultCard.translationY = dps(5).toFloat()
        resultCard.animate().alpha(1f).translationY(0f).setDuration(180).start()
    }

    private fun clearAll() {
        inputCards.forEach { it.editText.setText("") }
        inputCards.forEachIndexed { index, card -> card.setActive(index == 0) }
        activeIndex = 0
        lastLetterView?.background = rounded(Color.parseColor("#FAF9FF"), 12f, border, 1)
        lastLetterView?.setTextColor(textColor)
        lastLetterView = null
        resultGrid.removeAllViews()
        resultGrid.visibility = View.GONE
        resultHint.visibility = View.VISIBLE
        resultHint.text = "پس از وارد کردن موارد، نتیجه اینجا نمایش داده می‌شود."
        resultHint.setTextColor(muted)
        inputCards.firstOrNull()?.editText?.clearFocus()
        animateResult()
    }

    private fun comparePersian(a: String, b: String): Int {
        val ka = sortKey(a)
        val kb = sortKey(b)
        val common = min(ka.size, kb.size)
        for (i in 0 until common) if (ka[i] != kb[i]) return ka[i] - kb[i]
        if (ka.size != kb.size) return ka.size - kb.size
        return normalizedDisplay(a).compareTo(normalizedDisplay(b))
    }

    private fun sortKey(input: String): IntArray {
        val normalized = Normalizer.normalize(normalizedDisplay(input), Normalizer.Form.NFKC)
        val out = ArrayList<Int>(normalized.length)
        normalized.forEach { raw ->
            if (Character.getType(raw) == Character.NON_SPACING_MARK.toInt()) return@forEach
            val c = canonical(raw)
            val letterRank = rank[c]
            when {
                letterRank != null -> out.add(letterRank)
                c.isWhitespace() || c == '\u200C' || c == '\u200D' -> Unit
                Character.isLetterOrDigit(c) -> out.add(1000 + c.code)
            }
        }
        return out.toIntArray()
    }

    private fun normalizedDisplay(value: String): String = value.trim()
        .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
        .replace('ۀ', 'ه').replace('ة', 'ه').replace('ؤ', 'و')
        .replace('ئ', 'ی').replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')

    private fun canonical(c: Char): Char = when (c) {
        'ي', 'ى', 'ئ' -> 'ی'
        'ك' -> 'ک'
        'ۀ', 'ة' -> 'ه'
        'ؤ' -> 'و'
        'أ', 'إ', 'آ' -> 'ا'
        else -> c
    }

    private inner class InputCard(context: Context, private val index: Int) : LinearLayout(context) {
        val editText = EditText(context).apply {
            hint = "مورد ${index + 1}"
            textSize = sp(17)
            setTextColor(textColor)
            setHintTextColor(Color.parseColor("#AAA4B8"))
            typeface = fontRegular
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            textDirection = View.TEXT_DIRECTION_RTL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            background = null
            setPadding(dps(11), dps(6), dps(11), dps(2))
            setOnFocusChangeListener { _, hasFocus -> if (hasFocus) activate(index) }
            setOnClickListener { activate(index) }
        }
        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dps(3), dps(3), dps(3), dps(3))
            setActive(false)
            val badge = label((index + 1).toString(), 10, primary, true).apply {
                gravity = Gravity.CENTER
                background = rounded(primarySoft, 10f)
            }
            addView(badge, LayoutParams(dps(22), dps(22)).apply { marginEnd = dps(5) })
            addView(editText, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        fun setActive(active: Boolean) {
            background = rounded(surface, 16f, if (active) primary else border, if (active) 2 else 1)
            elevation = dp(if (active) 3 else 1).toFloat()
        }
    }

    private fun activate(index: Int) {
        activeIndex = index
        inputCards.forEachIndexed { i, card -> card.setActive(i == index) }
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = sp(size)
        setTextColor(color)
        typeface = if (bold) fontBold else fontRegular
        includeFontPadding = false
        textDirection = View.TEXT_DIRECTION_RTL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun rounded(fill: Int, radiusDp: Float, stroke: Int? = null, strokeWidthDp: Int = 0): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dps(radiusDp).toFloat()
        if (stroke != null && strokeWidthDp > 0) setStroke(max(1, dps(strokeWidthDp)), stroke)
    }

    private fun space(heightDp: Int): View = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dps(heightDp)) }
    private fun sp(value: Int): Float = max(9f, value * max(0.84f, uiScale))
    private fun dps(value: Int): Int = dp(value * uiScale)
    private fun dps(value: Float): Int = dp(value * uiScale)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun dp(value: Float): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
