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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import java.text.Normalizer
import kotlin.math.min

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
    private val text = Color.parseColor("#201D2D")
    private val muted = Color.parseColor("#7B768B")
    private val border = Color.parseColor("#E8E5F2")
    private val borderStrong = Color.parseColor("#D7D1F6")

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
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContentView(buildScreen())
    }

    private fun buildScreen(): View {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(bg)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(12), dp(16), dp(18))
        }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(buildHeader())
        root.addView(space(10))
        root.addView(buildAlphabetCard())
        root.addView(space(12))

        root.addView(label("عبارت‌های مورد مقایسه", 14, muted).apply {
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.RIGHT
            setPadding(dp(2), 0, dp(2), dp(7))
        })
        root.addView(buildInputs())
        root.addView(space(12))
        root.addView(buildCompareButton())
        root.addView(space(12))
        root.addView(buildResultCard())

        return scroll
    }

    private fun buildHeader(): View {
        val box = FrameLayout(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val title = label("مقایسه حروف و کلمات فارسی", 23, text).apply {
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
        }
        box.addView(title, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42), Gravity.START or Gravity.TOP))

        val clear = label("پاک کردن همه", 13, primary).apply {
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0)
            background = rounded(primarySoft, 16f)
            isClickable = true
            isFocusable = true
            setOnClickListener { clearAll() }
        }
        box.addView(clear, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34), Gravity.END or Gravity.TOP).apply {
            topMargin = dp(4)
        })

        val subtitle = label("مرتب‌سازی دقیق بر اساس ترتیب واقعی الفبای فارسی", 12, muted).apply {
            gravity = Gravity.RIGHT
        }
        box.addView(subtitle, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(25), Gravity.BOTTOM).apply {
            topMargin = dp(40)
            marginEnd = dp(2)
        })

        return box.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66))
        }
    }

    private fun buildAlphabetCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = rounded(surface, 22f, border, 1)
            elevation = dp(2).toFloat()
        }

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        heading.addView(label("الفبای فارسی", 14, text).apply {
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.RIGHT
        }, LinearLayout.LayoutParams(0, dp(24), 1f))
        heading.addView(label("برای درج، حرف را لمس کنید", 11, muted).apply {
            gravity = Gravity.LEFT
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(24)))
        card.addView(heading)
        card.addView(space(5))

        alphabet.chunked(8).forEach { letters ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                gravity = Gravity.CENTER
            }
            letters.forEach { letter ->
                val tile = label(letter.toString(), 18, text).apply {
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    background = rounded(Color.parseColor("#FAF9FF"), 13f, border, 1)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { view -> insertLetter(letter, view as TextView) }
                }
                row.addView(tile, LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                })
            }
            card.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
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
                row.addView(input, LinearLayout.LayoutParams(0, dp(70), 1f).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                })
            }
            grid.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78)))
        }

        inputCards.firstOrNull()?.setActive(true)
        return grid
    }

    private fun buildCompareButton(): View {
        return TextView(this).apply {
            text = "مقایسه و مرتب‌سازی"
            textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif", Typeface.BOLD))
            gravity = Gravity.CENTER
            background = rounded(primary, 18f)
            elevation = dp(5).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener {
                animate().scaleX(0.985f).scaleY(0.985f).setDuration(70).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(110).start()
                }.start()
                sortAndShow()
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54))
        }
    }

    private fun buildResultCard(): View {
        resultCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(12), dp(11), dp(12), dp(12))
            background = rounded(surface, 22f, border, 1)
            elevation = dp(2).toFloat()
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(label("نتیجه مرتب‌شده", 15, text).apply {
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.RIGHT
        }, LinearLayout.LayoutParams(0, dp(28), 1f))
        header.addView(label("الفبای فارسی", 11, primary).apply {
            gravity = Gravity.CENTER
            setPadding(dp(9), 0, dp(9), 0)
            background = rounded(primarySoft, 12f)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(25)))
        resultCard.addView(header)

        resultHint = label("پس از وارد کردن موارد، نتیجه اینجا نمایش داده می‌شود.", 13, muted).apply {
            gravity = Gravity.CENTER
        }
        resultCard.addView(resultHint, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)))

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

        lastLetterView?.background = rounded(Color.parseColor("#FAF9FF"), 13f, border, 1)
        lastLetterView?.setTextColor(text)
        lastLetterView = tile
        tile.background = rounded(primarySoft, 13f, primary, 1)
        tile.setTextColor(primaryDark)
        tile.animate().scaleX(1.08f).scaleY(1.08f).setDuration(85).withEndAction {
            tile.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
        }.start()
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
                row.addView(resultChip(globalIndex + 1, value), LinearLayout.LayoutParams(0, dp(53), 1f).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                })
            }
            if (pair.size == 1) {
                row.addView(Space(this), LinearLayout.LayoutParams(0, dp(53), 1f).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
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
            setPadding(dp(7), dp(6), dp(7), dp(6))
            background = rounded(Color.parseColor("#F8F7FF"), 15f, borderStrong, 1)
        }

        val badge = label(position.toString(), 13, Color.WHITE).apply {
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = rounded(primary, 14f)
        }
        chip.addView(badge, LinearLayout.LayoutParams(dp(29), dp(29)).apply {
            marginEnd = dp(8)
        })

        val word = label(value, 15, text).apply {
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            maxLines = 2
        }
        chip.addView(word, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        return chip
    }

    private fun animateResult() {
        resultCard.animate().cancel()
        resultCard.alpha = 0.35f
        resultCard.translationY = dp(7).toFloat()
        resultCard.animate().alpha(1f).translationY(0f).setDuration(220).start()
    }

    private fun clearAll() {
        inputCards.forEach { it.editText.setText("") }
        inputCards.forEachIndexed { index, card -> card.setActive(index == 0) }
        activeIndex = 0
        lastLetterView?.background = rounded(Color.parseColor("#FAF9FF"), 13f, border, 1)
        lastLetterView?.setTextColor(text)
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
        for (i in 0 until common) {
            if (ka[i] != kb[i]) return ka[i] - kb[i]
        }
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
                else -> Unit
            }
        }
        return out.toIntArray()
    }

    private fun normalizedDisplay(value: String): String {
        return value.trim()
            .replace('ي', 'ی')
            .replace('ى', 'ی')
            .replace('ك', 'ک')
            .replace('ۀ', 'ه')
            .replace('ة', 'ه')
            .replace('ؤ', 'و')
            .replace('ئ', 'ی')
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
    }

    private fun canonical(c: Char): Char = when (c) {
        'ي', 'ى', 'ئ' -> 'ی'
        'ك' -> 'ک'
        'ۀ', 'ة' -> 'ه'
        'ؤ' -> 'و'
        'أ', 'إ', 'آ' -> 'ا'
        else -> c
    }

    private inner class InputCard(context: Context, private val index: Int) : FrameLayout(context) {
        val editText = EditText(context).apply {
            hint = "مورد ${index + 1}"
            textSize = 17f
            setTextColor(text)
            setHintTextColor(Color.parseColor("#AAA4B8"))
            setTypeface(Typeface.create("sans-serif", Typeface.NORMAL))
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            textDirection = View.TEXT_DIRECTION_RTL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            background = null
            setPadding(dp(13), dp(10), dp(13), dp(3))
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) activate(index)
            }
            setOnClickListener { activate(index) }
        }

        init {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(3), dp(3), dp(3), dp(3))
            setActive(false)
            addView(editText, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

            val badge = label((index + 1).toString(), 11, primary).apply {
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                background = rounded(primarySoft, 11f)
            }
            addView(badge, LayoutParams(dp(23), dp(23), Gravity.START or Gravity.TOP).apply {
                topMargin = dp(6)
                marginStart = dp(6)
            })
        }

        fun setActive(active: Boolean) {
            background = rounded(surface, 17f, if (active) primary else border, if (active) 2 else 1)
            elevation = dp(if (active) 4 else 1).toFloat()
        }
    }

    private fun activate(index: Int) {
        activeIndex = index
        inputCards.forEachIndexed { i, card -> card.setActive(i == index) }
    }

    private fun label(value: String, size: Int, color: Int): TextView = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        includeFontPadding = false
        textDirection = View.TEXT_DIRECTION_RTL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun rounded(fill: Int, radiusDp: Float, stroke: Int? = null, strokeWidthDp: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (stroke != null && strokeWidthDp > 0) setStroke(dp(strokeWidthDp), stroke)
        }
    }

    private fun space(heightDp: Int): View = Space(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(heightDp))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun dp(value: Float): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
