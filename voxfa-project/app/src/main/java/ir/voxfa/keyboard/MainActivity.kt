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
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var permissionButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(247, 248, 252)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(24), dp(22), dp(18))
            setBackgroundColor(Color.rgb(247, 248, 252))
        }

        root.addView(TextView(this).apply {
            text = "VoxFa"
            textSize = 31f
            setTextColor(Color.rgb(35, 38, 58))
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = "کیبورد فارسی با اولویت تایپ صوتی"
            textSize = 15f
            setTextColor(Color.rgb(100, 103, 122))
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(18))
        })

        status = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(50, 53, 72))
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.WHITE, 16f)
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        permissionButton = actionButton("۱) اجازه میکروفون") {
            requestMicPermission()
        }
        root.addView(permissionButton, fullButtonParams())

        root.addView(actionButton("۲) فعال‌سازی VoxFa در تنظیمات") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }, fullButtonParams())

        root.addView(actionButton("۳) انتخاب VoxFa به‌عنوان کیبورد") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }, fullButtonParams())

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        root.addView(CheckBox(this).apply {
            text = "شروع خودکار تایپ صوتی هنگام باز شدن کیبورد"
            textSize = 14f
            isChecked = prefs.getBoolean(KEY_AUTO_START, true)
            setTextColor(Color.rgb(55, 58, 78))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(KEY_AUTO_START, checked).apply()
            }
            setPadding(dp(4), dp(8), dp(4), dp(8))
        })

        root.addView(TextView(this).apply {
            text = "آزمایش سریع"
            textSize = 13f
            setTextColor(Color.rgb(105, 108, 126))
            setPadding(0, dp(8), 0, dp(4))
        })

        root.addView(EditText(this).apply {
            hint = "بعد از انتخاب VoxFa اینجا لمس کنید و صحبت کنید…"
            textSize = 15f
            gravity = Gravity.TOP or Gravity.RIGHT
            textDirection = View.TEXT_DIRECTION_RTL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            minHeight = dp(82)
            background = rounded(Color.WHITE, 16f, Color.rgb(224, 226, 236))
        }, LinearLayout.LayoutParams(-1, dp(90)))

        root.addView(TextView(this).apply {
            text = "VoxFa فایل صوتی را ذخیره نمی‌کند. تشخیص گفتار توسط سرویس Speech Recognition فعال روی گوشی انجام می‌شود و برای کیفیت بهتر به اینترنت نیاز دارد."
            textSize = 11.5f
            setTextColor(Color.rgb(120, 123, 140))
            gravity = Gravity.RIGHT
            setPadding(dp(2), dp(14), dp(2), 0)
        })

        setContentView(root)
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
            micGranted && selected -> "✅ آماده استفاده — VoxFa کیبورد پیش‌فرض است و میکروفون مجاز است."
            !micGranted -> "برای شروع، اجازه میکروفون را فعال کنید."
            else -> "میکروفون آماده است؛ حالا VoxFa را فعال و انتخاب کنید."
        }
        permissionButton.isEnabled = !micGranted
        permissionButton.text = if (micGranted) "✓ اجازه میکروفون فعال است" else "۱) اجازه میکروفون"
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

    private fun actionButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(Color.rgb(91, 95, 239), 15f)
        setOnClickListener { action() }
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val PREFS = "voxfa_prefs"
        const val KEY_AUTO_START = "auto_start_voice"
        private const val REQ_MIC = 2001
    }
}
