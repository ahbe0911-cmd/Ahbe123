package ir.voxfa.keyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class VoiceImeService : InputMethodService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerIntent: Intent? = null
    private var wantListening = false
    private var actuallyListening = false
    private var keyboardMode = false
    private var numberMode = false
    private var utterancePrefix = ""

    private lateinit var container: LinearLayout
    private var statusText: TextView? = null
    private var previewText: TextView? = null
    private var micButton: TextView? = null

    override fun onCreate() {
        super.onCreate()
        Locale.setDefault(Locale("fa", "IR"))
        prepareRecognizer()
    }

    override fun onCreateInputView(): View {
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(Color.rgb(247, 248, 252))
        }
        showVoicePanel(startImmediately = false)
        return container
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val autoStart = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
            .getBoolean(MainActivity.KEY_AUTO_START, true)

        if (!keyboardMode) showVoicePanel(startImmediately = false)

        if (autoStart && hasMicPermission()) {
            mainHandler.postDelayed({ startVoiceLoop() }, 250)
        } else if (!hasMicPermission()) {
            updateStatus("اجازه میکروفون از برنامه VoxFa فعال نشده است")
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        stopVoiceLoop(clearComposing = true)
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        try { speechRecognizer?.destroy() } catch (_: Throwable) {}
        speechRecognizer = null
        super.onDestroy()
    }

    private fun prepareRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    utterancePrefix = computeUtterancePrefix()
                    actuallyListening = true
                    updateStatus("در حال شنیدن…")
                    updateMic(true)
                }

                override fun onBeginningOfSpeech() {
                    updateStatus("صدای شما دریافت شد…")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    micButton?.alpha = 0.72f + (level * 0.28f)
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    actuallyListening = false
                    updateStatus("در حال تبدیل گفتار به متن…")
                }

                override fun onError(error: Int) {
                    actuallyListening = false
                    currentInputConnection?.finishComposingText()
                    updateMic(false)

                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            updateStatus("منتظر صدای شما…")
                            scheduleRestart(220)
                        }
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                            updateStatus("موتور صوتی مشغول است؛ تلاش مجدد…")
                            scheduleRestart(850)
                        }
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                        SpeechRecognizer.ERROR_SERVER -> {
                            updateStatus("خطای اینترنت یا سرویس؛ تلاش مجدد…")
                            scheduleRestart(1400)
                        }
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            wantListening = false
                            updateStatus("اجازه میکروفون لازم است؛ برنامه VoxFa را باز کنید")
                        }
                        else -> {
                            updateStatus("تشخیص صوت متوقف شد؛ لمس میکروفون برای ادامه")
                            if (wantListening) scheduleRestart(1000)
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    actuallyListening = false
                    val best = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull { it.isNotBlank() }
                        .orEmpty()

                    if (best.isNotBlank()) {
                        val normalized = VoiceTextNormalizer.normalize(best)
                        if (normalized.isNotBlank()) {
                            currentInputConnection?.setComposingText(utterancePrefix + normalized, 1)
                            currentInputConnection?.finishComposingText()
                            previewText?.text = normalized
                        }
                    }

                    updateStatus("آماده جمله بعدی…")
                    updateMic(false)
                    scheduleRestart(260)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull { it.isNotBlank() }
                        .orEmpty()

                    if (partial.isNotBlank()) {
                        val normalized = VoiceTextNormalizer.normalize(partial)
                        previewText?.text = normalized
                        currentInputConnection?.setComposingText(utterancePrefix + normalized, 1)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }

        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        }
    }

    private fun startVoiceLoop() {
        if (!hasMicPermission()) {
            wantListening = false
            updateStatus("برای استفاده، ابتدا اجازه میکروفون را در برنامه VoxFa فعال کنید")
            return
        }
        if (speechRecognizer == null || recognizerIntent == null) {
            prepareRecognizer()
        }
        if (speechRecognizer == null) {
            wantListening = false
            updateStatus("سرویس تشخیص گفتار روی این گوشی در دسترس نیست")
            return
        }

        wantListening = true
        startOneRecognition()
    }

    private fun startOneRecognition() {
        if (!wantListening || actuallyListening || !isInputViewShown) return
        mainHandler.removeCallbacks(restartRunnable)
        try {
            speechRecognizer?.startListening(recognizerIntent)
            updateStatus("در حال آماده‌سازی میکروفون…")
            updateMic(true)
        } catch (_: Throwable) {
            updateStatus("شروع تشخیص صوت ناموفق بود؛ تلاش مجدد…")
            scheduleRestart(1000)
        }
    }

    private fun stopVoiceLoop(clearComposing: Boolean) {
        wantListening = false
        actuallyListening = false
        mainHandler.removeCallbacks(restartRunnable)
        try { speechRecognizer?.cancel() } catch (_: Throwable) {}
        if (clearComposing) currentInputConnection?.finishComposingText()
        updateMic(false)
        updateStatus("میکروفون متوقف است")
    }

    private val restartRunnable = Runnable { startOneRecognition() }

    private fun scheduleRestart(delayMs: Long) {
        if (!wantListening) return
        mainHandler.removeCallbacks(restartRunnable)
        mainHandler.postDelayed(restartRunnable, delayMs)
    }

    private fun showVoicePanel(startImmediately: Boolean) {
        if (!::container.isInitialized) return
        keyboardMode = false
        container.removeAllViews()

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(keyButton("⌨", weight = 0f, widthDp = 52) {
            stopVoiceLoop(clearComposing = true)
            showKeyboardPanel()
        })
        top.addView(TextView(this).apply {
            text = "VoxFa • فارسی"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(50, 53, 72))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        top.addView(keyButton("⚙", weight = 0f, widthDp = 52) {
            val intent = Intent(this@VoiceImeService, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        })
        container.addView(top, LinearLayout.LayoutParams(-1, dp(46)))

        statusText = TextView(this).apply {
            text = "برای صحبت، میکروفون را لمس کنید"
            textSize = 13f
            setTextColor(Color.rgb(100, 103, 122))
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(2))
        }
        container.addView(statusText, LinearLayout.LayoutParams(-1, dp(32)))

        micButton = TextView(this).apply {
            text = "🎙"
            textSize = 35f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(91, 95, 239), 38f)
            setOnClickListener {
                if (wantListening) stopVoiceLoop(clearComposing = true) else startVoiceLoop()
            }
        }
        container.addView(micButton, LinearLayout.LayoutParams(-1, dp(82)).apply {
            leftMargin = dp(72)
            rightMargin = dp(72)
            topMargin = dp(2)
            bottomMargin = dp(7)
        })

        previewText = TextView(this).apply {
            text = "متن شما مستقیماً در برنامه مقصد نوشته می‌شود"
            textSize = 13f
            maxLines = 2
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(74, 77, 96))
            background = rounded(Color.WHITE, 14f, Color.rgb(228, 230, 238))
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        container.addView(previewText, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(7) })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        actions.addView(keyButton("⌫", 1f) { deleteOne() })
        actions.addView(keyButton("فاصله", 2.2f) { commit(" ") })
        actions.addView(keyButton("↵", 1f) { sendEnter() })
        container.addView(actions, LinearLayout.LayoutParams(-1, dp(48)))

        if (startImmediately) mainHandler.postDelayed({ startVoiceLoop() }, 180)
    }

    private fun showKeyboardPanel() {
        if (!::container.isInitialized) return
        keyboardMode = true
        container.removeAllViews()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(keyButton("🎙", 0f, 58) {
            showVoicePanel(startImmediately = true)
        })
        header.addView(TextView(this).apply {
            text = if (numberMode) "اعداد و نشانه‌ها" else "فارسی"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(75, 78, 96))
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        header.addView(keyButton(if (numberMode) "اب‌پ" else "۱۲۳", 0f, 62) {
            numberMode = !numberMode
            showKeyboardPanel()
        })
        container.addView(header, LinearLayout.LayoutParams(-1, dp(44)))

        val rows = if (numberMode) KeyboardLayouts.numberRows else KeyboardLayouts.persianRows
        rows.forEach { row ->
            val rowView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            row.forEach { key -> rowView.addView(keyButton(key, 1f) { commit(key) }) }
            container.addView(rowView, LinearLayout.LayoutParams(-1, dp(47)))
        }

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        bottom.addView(repeatingDeleteButton())
        bottom.addView(keyButton("فاصله", 2.7f) { commit(" ") })
        bottom.addView(keyButton("↵", 1f) { sendEnter() })
        container.addView(bottom, LinearLayout.LayoutParams(-1, dp(49)))
    }

    private fun keyButton(label: String, weight: Float, widthDp: Int? = null, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = when {
                label.length == 1 -> 18f
                label.length > 5 -> 12.5f
                else -> 14f
            }
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(43, 46, 63))
            background = rounded(Color.WHITE, 10f, Color.rgb(224, 226, 236))
            setOnClickListener { onClick() }
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.alpha = 0.72f
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.alpha = 1f
                }
                false
            }
            layoutParams = if (widthDp != null) {
                LinearLayout.LayoutParams(dp(widthDp), -1).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
            } else {
                LinearLayout.LayoutParams(0, -1, weight).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
            }
        }
    }

    private fun repeatingDeleteButton(): TextView {
        val repeat = object : Runnable {
            override fun run() {
                deleteOne()
                mainHandler.postDelayed(this, 65)
            }
        }

        return keyButton("⌫", 1f) { }.apply {
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.alpha = 0.72f
                        deleteOne()
                        mainHandler.postDelayed(repeat, 360)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.alpha = 1f
                        mainHandler.removeCallbacks(repeat)
                        true
                    }
                    else -> true
                }
            }
        }
    }

    private fun commit(text: String) {
        currentInputConnection?.finishComposingText()
        currentInputConnection?.commitText(text, 1)
    }

    private fun deleteOne() {
        currentInputConnection?.finishComposingText()
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun sendEnter() {
        currentInputConnection?.finishComposingText()
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            currentInputConnection?.performEditorAction(action)
        } else {
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    private fun computeUtterancePrefix(): String {
        val before = currentInputConnection?.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        return if (before.isNotEmpty() && !before.last().isWhitespace() && before.last() !in listOf('(', '[', '{', '«')) " " else ""
    }

    private fun updateStatus(text: String) {
        statusText?.text = text
    }

    private fun updateMic(active: Boolean) {
        micButton?.apply {
            background = rounded(
                if (active) Color.rgb(239, 83, 80) else Color.rgb(91, 95, 239),
                38f
            )
            alpha = 1f
        }
    }

    private fun hasMicPermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun rounded(fill: Int, radiusDp: Float, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
