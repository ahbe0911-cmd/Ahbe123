package ir.voxfa.keyboard

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var permissionButton: Button

    private val vazirTypeface: Typeface by lazy {
        try {
            resources.getFont(R.font.vazirmatn_regular)
        } catch (_: Throwable) {
            Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(246, 247, 255)
        window.navigationBarColor = Color.rgb(246, 247, 255)

        // Voice is intentionally manual in v1.2: opening the keyboard never starts the microphone.
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_AUTO_START, false).apply()

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(246, 247, 255))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(20), dp(18), dp(24))
            background = verticalGradient(
                intArrayOf(Color.rgb(248, 249, 255), Color.rgb(240, 246, 251)),
                0f
            )
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(19), dp(18), dp(17))
            background = horizontalGradient(
                intArrayOf(
                    Color.rgb(104, 88, 255),
                    Color.rgb(157, 84, 244),
                    Color.rgb(255, 80, 139)
                ),
                26f
            )
            elevation = dp(3).toFloat()
        }

        hero.addView(TextView(this).apply {
            text = "VoxFa"
            textSize = 33f
            typeface = Typeface.create(vazirTypeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        hero.addView(TextView(this).apply {
            text = "کیبورد هوشمند فارسی + English"
            textSize = 14.5f
            typeface = vazirTypeface
            setTextColor(Color.argb(235, 255, 255, 255))
            gravity = Gravity.CENTER
            setPadding(0, dp(3), 0, dp(10))
        })
        hero.addView(TextView(this).apply {
            text = "🎙  میکروفون فقط با لمس خودت روشن می‌شود"
            textSize = 12.8f
            typeface = Typeface.create(vazirTypeface, Typeface.BOLD)
            setTextColor(Color.rgb(72, 54, 110))
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(Color.argb(235, 255, 255, 255), 18f)
        })
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(15) })

        status = TextView(this).apply {
            textSize = 13.6f
            typeface = vazirTypeface
            setTextColor(Color.rgb(56, 59, 74))
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.WHITE, 18f, Color.rgb(226, 228, 238))
            elevation = dp(1).toFloat()
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        root.addView(sectionTitle("راه‌اندازی سریع"))

        permissionButton = actionButton("۱  اجازه میکروفون", Color.rgb(255, 81, 137)) {
            requestMicPermission()
        }
        root.addView(permissionButton, fullButtonParams())

        root.addView(actionButton("۲  فعال‌سازی VoxFa", Color.rgb(104, 88, 255)) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }, fullButtonParams())

        root.addView(actionButton("۳  انتخاب VoxFa به‌عنوان کیبورد", Color.rgb(38, 176, 139)) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }, fullButtonParams())

        val featureRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        featureRow.addView(featureChip("بدون صدای لمس", Color.rgb(235, 247, 242)), LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        featureRow.addView(featureChip("بدون شروع خودکار", Color.rgb(241, 236, 255)), LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        root.addView(featureRow, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(7) })

        root.addView(sectionTitle("آزمایش سریع"))
        root.addView(EditText(this).apply {
            hint = "اینجا لمس کن؛ فارسی یا English تایپ کن…"
            textSize = 15f
            typeface = vazirTypeface
            gravity = Gravity.TOP or Gravity.RIGHT
            textDirection = View.TEXT_DIRECTION_RTL
            setTextColor(Color.rgb(42, 45, 58))
            setHintTextColor(Color.rgb(142, 145, 160))
            setPadding(dp(14), dp(13), dp(14), dp(13))
            minHeight = dp(92)
            background = rounded(Color.WHITE, 18f, Color.rgb(220, 223, 234))
        }, LinearLayout.LayoutParams(-1, dp(96)).apply { bottomMargin = dp(13) })

        root.addView(TextView(this).apply {
            text = "نسخه ۱.۲: ظاهر زنده‌تر، کلیدهای گرد، پیشنهادهای رنگی، تایپ صوتی دستی و فونت وزیرمتن. VoxFa صدای لمس کلید تولید نمی‌کند و فایل صوتی را ذخیره نمی‌کند."
            textSize = 11.7f
            typeface = vazirTypeface
            setTextColor(Color.rgb(112, 116, 132))
            gravity = Gravity.RIGHT
            setLineSpacing(0f, 1.2f)
            setPadding(dp(4), dp(4), dp(4), 0)
        })

        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val micGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val defaultIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()
        val selected = defaultIme.startsWith(packageName)

        status.text = when {
            micGranted && selected -> "✓ آماده استفاده — VoxFa انتخاب شده و میکروفون مجاز است."
            !micGranted -> "اول اجازه میکروفون را بده؛ بعد VoxFa را فعال و انتخاب کن."
            else -> "میکروفون آماده است؛ حالا VoxFa را از کیبوردهای گوشی انتخاب کن."
        }
        status.background = rounded(
            when {
                micGranted && selected -> Color.rgb(232, 248, 241)
                !micGranted -> Color.rgb(255, 241, 246)
                else -> Color.rgb(242, 239, 255)
            },
            18f
        )
        permissionButton.isEnabled = !micGranted
        permissionButton.alpha = if (micGranted) 0.72f else 1f
        permissionButton.text = if (micGranted) "✓ اجازه میکروفون فعال است" else "۱  اجازه میکروفون"
    }

    private fun requestMicPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC) refreshStatus()
    }

    private fun sectionTitle(label: String): TextView = TextView(this).apply {
        text = label
        textSize = 13.2f
        typeface = Typeface.create(vazirTypeface, Typeface.BOLD)
        setTextColor(Color.rgb(74, 77, 94))
        gravity = Gravity.RIGHT
        setPadding(dp(4), dp(3), dp(4), dp(7))
    }

    private fun actionButton(label: String, fill: Int, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 13.7f
        typeface = Typeface.create(vazirTypeface, Typeface.BOLD)
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(fill, 17f)
        isSoundEffectsEnabled = false
        isHapticFeedbackEnabled = false
        setOnClickListener { action() }
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.98f).scaleY(0.98f).setDuration(70).start()
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
            false
        }
    }

    private fun featureChip(label: String, fill: Int): TextView = TextView(this).apply {
        text = label
        textSize = 11.8f
        typeface = vazirTypeface
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(70, 73, 88))
        background = rounded(fill, 17f)
    }

    private fun fullButtonParams() = LinearLayout.LayoutParams(-1, dp(52)).apply {
        bottomMargin = dp(9)
    }

    private fun rounded(fill: Int, radiusDp: Float, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun horizontalGradient(colors: IntArray, radiusDp: Float): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        colors
    ).apply {
        cornerRadius = dp(radiusDp.toInt()).toFloat()
    }

    private fun verticalGradient(colors: IntArray, radiusDp: Float): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        colors
    ).apply {
        cornerRadius = dp(radiusDp.toInt()).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val PREFS = "voxfa_prefs"
        const val KEY_AUTO_START = "auto_start_voice"
        private const val REQ_MIC = 2001
    }
}
