package com.ahmad.persiansort

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import kotlin.math.max

@Suppress("DEPRECATION")
class MainActivity : Activity() {

    private val bg = Color.parseColor("#F7F7FC")
    private val surface = Color.WHITE
    private val primary = Color.parseColor("#5A4CF3")
    private val primary2 = Color.parseColor("#6C5CF7")
    private val primaryDark = Color.parseColor("#4435D8")
    private val primarySoft = Color.parseColor("#F0EEFF")
    private val primarySoft2 = Color.parseColor("#F7F5FF")
    private val textColor = Color.parseColor("#211F2B")
    private val muted = Color.parseColor("#777384")
    private val border = Color.parseColor("#E5E3ED")
    private val borderStrong = Color.parseColor("#D8D2FA")
    private val danger = Color.parseColor("#B54A57")

    private val screenHeightDp: Float by lazy {
        resources.displayMetrics.heightPixels / resources.displayMetrics.density
    }

    private val screenWidthDp: Float by lazy {
        resources.displayMetrics.widthPixels / resources.displayMetrics.density
    }

    private val uiScale: Float by lazy {
        val heightScale = when {
            screenHeightDp < 600f -> 0.74f
            screenHeightDp < 680f -> 0.80f
            screenHeightDp < 760f -> 0.87f
            screenHeightDp < 840f -> 0.92f
            else -> 1f
        }
        val widthScale = if (screenWidthDp < 350f) 0.93f else 1f
        heightScale * widthScale
    }

    private val fontRegular: Typeface by lazy { resources.getFont(R.font.vazirmatn_regular) }
    private val fontBold: Typeface by lazy { resources.getFont(R.font.vazirmatn_semibold) }
    private val mainHandler = Handler(Looper.getMainLooper())

    private val inputCards = mutableListOf<InputCard>()
    private var activeIndex = 0
    private var lastLetterView: TextView? = null
    private var suppressAutoNext = false

    private lateinit var resultCard: LinearLayout
    private lateinit var resultGrid: LinearLayout
    private lateinit var resultHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = surface
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContentView(buildScreen())
    }

    override fun onDestroy() {
        inputCards.forEach { it.cancelPendingAutoNext() }
        super.onDestroy()
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
            setPadding(dps(13), dps(10), dps(13), dps(12))
        }

        scroll.addView(
            root,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        root.addView(buildHeader())
        root.addView(space(6))
        root.addView(buildAlphabetCard())
        root.addView(space(7))
        root.addView(buildInputSectionHeader())
        root.addView(buildInputs())
        root.addView(space(7))
        root.addView(buildCompareButton())
        root.addView(space(7))
        root.addView(buildResultCard())

        return scroll
    }

    private fun buildHeader(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL

            addView(label("مرتب‌سازی حروف و کلمات فارسی", 20, textColor, true).apply {
                gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
                setSingleLine(true)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dps(32)))

            addView(label("مرتب‌سازی دقیق بر اساس ترتیب واقعی الفبای فارسی", 11, muted).apply {
                gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
                setSingleLine(true)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dps(20)))
        }
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

        heading.addView(
            label("الفبای فارسی", 14, textColor, true).apply { gravity = Gravity.RIGHT },
            LinearLayout.LayoutParams(0, dps(22), 1f)
        )

        heading.addView(
            label("برای درج، حرف را لمس کنید", 10, muted).apply { gravity = Gravity.LEFT },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dps(22))
        )

        card.addView(heading)
        card.addView(space(3))

        PersianAlphabet.letters.chunked(8).forEach { letters ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                gravity = Gravity.CENTER
            }

            letters.forEach { letter ->
                val tile = label(letter.toString(), 17, textColor, true).apply {
                    gravity = Gravity.CENTER
                    background = rounded(primarySoft2, 12f, border, 1)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { view -> insertInternalLetter(letter, view as TextView) }
                }
                row.addView(
                    tile,
                    LinearLayout.LayoutParams(0, dps(34), 1f).apply {
                        setMargins(dps(2), dps(2), dps(2), dps(2))
                    }
                )
            }
            card.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dps(38)))
        }

        return card
    }

    private fun buildInputSectionHeader(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL

            addView(label("عبارت‌های مورد مقایسه", 14, muted, true).apply {
                gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, dps(29), 1f))

            val clearAll = label("پاک‌کردن همه", 10, primary, true).apply {
                gravity = Gravity.CENTER
                setPadding(dps(8), 0, dps(8), 0)
                background = rounded(primarySoft, 12f)
                isClickable = true
                isFocusable = true
                setOnClickListener { clearAll() }
            }
            addView(clearAll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dps(25)))
        }
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
                row.addView(
                    input,
                    LinearLayout.LayoutParams(0, dps(57), 1f).apply {
                        setMargins(dps(3), dps(3), dps(3), dps(3))
                    }
                )
            }

            grid.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dps(63)))
        }

        inputCards.firstOrNull()?.setActive(true, animate = false)
        return grid
    }

    private fun buildCompareButton(): View {
        return TextView(this).apply {
            text = "مقایسه و مرتب‌سازی"
            textSize = sp(16)
            setTextColor(Color.WHITE)
            typeface = fontBold
            gravity = Gravity.CENTER
            background = gradientRounded(primary, primary2, 17f)
            elevation = dp(4).toFloat()
            isClickable = true
            isFocusable = true

            setOnClickListener {
                animate().cancel()
                animate().scaleX(0.985f).scaleY(0.985f).setDuration(65).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(105).start()
                }.start()
                sortAndShow()
            }

            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dps(47))
        }
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

        header.addView(
            label("نتیجه مرتب‌شده", 15, textColor, true).apply { gravity = Gravity.RIGHT },
            LinearLayout.LayoutParams(0, dps(25), 1f)
        )

        header.addView(label("الفبای فارسی", 10, primary).apply {
            gravity = Gravity.CENTER
            setPadding(dps(7), 0, dps(7), 0)
            background = rounded(primarySoft, 11f)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dps(22)))

        resultCard.addView(header)

        resultHint = label("پس از وارد کردن موارد، نتیجه اینجا نمایش داده می‌شود.", 12, muted).apply {
            gravity = Gravity.CENTER
        }
        resultCard.addView(
            resultHint,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dps(48))
        )

        resultGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            visibility = View.GONE
        }
        resultCard.addView(
            resultGrid,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        return resultCard
    }

    private fun insertInternalLetter(letter: Char, tile: TextView) {
        val card = inputCards.getOrNull(activeIndex) ?: return
        val target = card.editText
        val start = target.selectionStart.coerceAtLeast(0)
        val current = target.text ?: return

        suppressAutoNext = true
        current.insert(start.coerceAtMost(current.length), letter.toString())
        target.setSelection((start + 1).coerceAtMost(current.length))
        suppressAutoNext = false

        highlightAlphabetTile(tile)

        if (PersianAlphabet.isSinglePersianLetter(target.text.toString()) && activeIndex < inputCards.lastIndex) {
            focusInput(activeIndex + 1)
        } else {
            target.requestFocus()
        }
    }

    private fun highlightAlphabetTile(tile: TextView) {
        lastLetterView?.let { previous ->
            previous.animate().cancel()
            previous.scaleX = 1f
            previous.scaleY = 1f
            previous.background = rounded(primarySoft2, 12f, border, 1)
            previous.setTextColor(textColor)
        }

        lastLetterView = tile
        tile.background = rounded(primarySoft, 12f, primary, 1)
        tile.setTextColor(primaryDark)
        tile.animate().cancel()
        tile.animate().scaleX(0.91f).scaleY(0.91f).setDuration(60).withEndAction {
            tile.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
        }.start()
    }

    private fun scheduleSystemAutoNext(index: Int) {
        if (index >= inputCards.lastIndex || suppressAutoNext) return
        val card = inputCards[index]
        card.cancelPendingAutoNext()

        val snapshot = card.editText.text.toString()
        if (!PersianAlphabet.isSinglePersianLetter(snapshot)) return

        val task = Runnable {
            val stillSingle = PersianAlphabet.isSinglePersianLetter(card.editText.text.toString())
            if (stillSingle && card.editText.hasFocus() && activeIndex == index) {
                focusInput(index + 1)
            }
        }

        card.pendingAutoNext = task
        mainHandler.postDelayed(task, 520L)
    }

    private fun focusInput(index: Int) {
        val target = inputCards.getOrNull(index) ?: return
        activeIndex = index
        inputCards.forEachIndexed { i, card -> card.setActive(i == index) }
        target.editText.requestFocus()
        target.editText.setSelection(target.editText.text?.length ?: 0)
    }

    private fun sortAndShow() {
        inputCards.forEach { it.cancelPendingAutoNext() }

        val values = inputCards
            .map { it.editText.text.toString().trim() }
            .filter { it.isNotEmpty() }

        if (values.isEmpty()) {
            resultGrid.visibility = View.GONE
            resultHint.visibility = View.VISIBLE
            resultHint.text = "حداقل یک حرف، کلمه یا عبارت وارد کنید."
            resultHint.setTextColor(danger)
            animateResult()
            return
        }

        val sorted = PersianAlphabet.sort(values)
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
                row.addView(
                    resultChip(globalIndex + 1, value),
                    LinearLayout.LayoutParams(0, dps(43), 1f).apply {
                        setMargins(dps(2), dps(2), dps(2), dps(2))
                    }
                )
            }

            if (pair.size == 1) {
                row.addView(Space(this), LinearLayout.LayoutParams(0, dps(43), 1f).apply {
                    setMargins(dps(2), dps(2), dps(2), dps(2))
                })
            }
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
            background = rounded(primarySoft2, 14f, borderStrong, 1)
        }

        val badge = label(position.toString(), 12, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(primary, 13f)
        }
        chip.addView(
            badge,
            LinearLayout.LayoutParams(dps(26), dps(26)).apply { marginEnd = dps(6) }
        )

        chip.addView(label(value, 15, textColor, true).apply {
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            maxLines = 1
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        return chip
    }

    private fun animateResult() {
        resultCard.animate().cancel()
        resultCard.alpha = 0.35f
        resultCard.translationY = dps(6).toFloat()
        resultCard.scaleX = 0.995f
        resultCard.scaleY = 0.995f
        resultCard.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(190)
            .start()
    }

    private fun clearAll() {
        suppressAutoNext = true
        inputCards.forEach {
            it.cancelPendingAutoNext()
            it.editText.setText("")
        }
        suppressAutoNext = false

        activeIndex = 0
        inputCards.forEachIndexed { index, card -> card.setActive(index == 0, animate = false) }

        lastLetterView?.let {
            it.background = rounded(primarySoft2, 12f, border, 1)
            it.setTextColor(textColor)
            it.scaleX = 1f
            it.scaleY = 1f
        }
        lastLetterView = null

        resultGrid.removeAllViews()
        resultGrid.visibility = View.GONE
        resultHint.visibility = View.VISIBLE
        resultHint.text = "پس از وارد کردن موارد، نتیجه اینجا نمایش داده می‌شود."
        resultHint.setTextColor(muted)

        inputCards.firstOrNull()?.editText?.requestFocus()
        animateResult()
    }

    private inner class InputCard(context: Context, val index: Int) : LinearLayout(context) {
        var pendingAutoNext: Runnable? = null

        val editText = SmartEditText(context).apply {
            hint = "مورد ${index + 1}"
            textSize = sp(16)
            setTextColor(textColor)
            setHintTextColor(Color.parseColor("#AAA5B7"))
            typeface = fontRegular
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            textDirection = View.TEXT_DIRECTION_RTL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = if (index < 3) EditorInfo.IME_ACTION_NEXT else EditorInfo.IME_ACTION_DONE
            background = null
            setPadding(dps(5), 0, dps(4), 0)

            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) activate(index)
                else cancelPendingAutoNext()
            }

            setOnClickListener { activate(index) }

            onBackspaceWhenEmpty = {
                if (index > 0) focusInput(index - 1)
            }

            setOnEditorActionListener { _, actionId, _ ->
                when {
                    actionId == EditorInfo.IME_ACTION_NEXT && index < 3 -> {
                        focusInput(index + 1)
                        true
                    }
                    actionId == EditorInfo.IME_ACTION_DONE && index == 3 -> {
                        sortAndShow()
                        true
                    }
                    else -> false
                }
            }
        }

        private val clearButton = label("×", 16, muted, true).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
            isClickable = true
            isFocusable = false
            background = rounded(Color.parseColor("#F2F0F7"), 10f)
            setOnClickListener {
                cancelPendingAutoNext()
                suppressAutoNext = true
                editText.setText("")
                suppressAutoNext = false
                editText.requestFocus()
            }
        }

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dps(4), dps(3), dps(4), dps(3))
            setActive(false, animate = false)

            val badge = label((index + 1).toString(), 10, primary, true).apply {
                gravity = Gravity.CENTER
                background = rounded(primarySoft, 10f)
            }

            addView(
                badge,
                LayoutParams(dps(22), dps(22)).apply { marginEnd = dps(4) }
            )
            addView(editText, LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            addView(
                clearButton,
                LayoutParams(dps(22), dps(22)).apply { marginStart = dps(3) }
            )

            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    clearButton.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                    cancelPendingAutoNext()
                }

                override fun afterTextChanged(s: Editable?) {
                    if (!suppressAutoNext && editText.hasFocus()) {
                        scheduleSystemAutoNext(index)
                    }
                }
            })
        }

        fun cancelPendingAutoNext() {
            pendingAutoNext?.let(mainHandler::removeCallbacks)
            pendingAutoNext = null
        }

        fun setActive(active: Boolean, animate: Boolean = true) {
            background = rounded(
                if (active) Color.parseColor("#FEFDFF") else surface,
                16f,
                if (active) primary else border,
                if (active) 2 else 1
            )
            elevation = dp(if (active) 3 else 1).toFloat()

            if (animate) {
                animate().cancel()
                animate()
                    .scaleX(if (active) 1.008f else 1f)
                    .scaleY(if (active) 1.008f else 1f)
                    .setDuration(110)
                    .start()
            }
        }
    }

    private fun activate(index: Int) {
        if (activeIndex == index && inputCards.getOrNull(index)?.editText?.hasFocus() == true) return
        activeIndex = index
        inputCards.forEachIndexed { i, card -> card.setActive(i == index) }
    }

    private fun label(
        value: String,
        size: Int,
        color: Int,
        bold: Boolean = false
    ): TextView = TextView(this).apply {
        text = value
        textSize = sp(size)
        setTextColor(color)
        typeface = if (bold) fontBold else fontRegular
        includeFontPadding = false
        textDirection = View.TEXT_DIRECTION_RTL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun rounded(
        fill: Int,
        radiusDp: Float,
        stroke: Int? = null,
        strokeWidthDp: Int = 0
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dps(radiusDp).toFloat()
        if (stroke != null && strokeWidthDp > 0) {
            setStroke(max(1, dps(strokeWidthDp)), stroke)
        }
    }

    private fun gradientRounded(start: Int, end: Int, radiusDp: Float): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(start, end)
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dps(radiusDp).toFloat()
        }
    }

    private fun space(heightDp: Int): View = Space(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dps(heightDp))
    }

    private fun sp(value: Int): Float = max(9f, value * max(0.84f, uiScale))
    private fun dps(value: Int): Int = dp(value * uiScale)
    private fun dps(value: Float): Int = dp(value * uiScale)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun dp(value: Float): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
