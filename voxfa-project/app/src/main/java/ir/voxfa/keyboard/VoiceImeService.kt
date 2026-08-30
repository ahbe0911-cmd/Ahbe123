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

    private enum class LanguageMode { PERSIAN, ENGLISH }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var wantListening = false
    private var actuallyListening = false
    private var keyboardMode = false
    private var numberMode = false
    private var shiftEnabled = false
    private var languageMode = LanguageMode.PERSIAN
    private var utterancePrefix = ""

    private lateinit var container: LinearLayout
    private var statusText: TextView? = null
    private var previewText: TextView? = null
    private var micButton: TextView? = null
    private val suggestionViews = mutableListOf<TextView>()

    override fun onCreate() {
        super.onCreate()
        Locale.setDefault(Locale("fa", "IR"))
        prepareRecognizer()
    }

    override fun onCreateInputView(): View {
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
            setBackgroundColor(Color.rgb(239, 240, 244))
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

        try { speechRecognizer?.destroy() } catch (_: Throwable) {}
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    utterancePrefix = computeUtterancePrefix()
                    actuallyListening = true
                    updateStatus(if (languageMode == LanguageMode.PERSIAN) "در حال شنیدن…" else "Listening…")
                    updateMic(true)
                }

                override fun onBeginningOfSpeech() {
                    updateStatus(if (languageMode == LanguageMode.PERSIAN) "صدای شما دریافت شد…" else "Voice detected…")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    micButton?.alpha = 0.72f + (level * 0.28f)
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    actuallyListening = false
                    updateStatus(if (languageMode == LanguageMode.PERSIAN) "در حال تبدیل گفتار به متن…" else "Converting speech to text…")
                }

                override fun onError(error: Int) {
                    actuallyListening = false
                    currentInputConnection?.finishComposingText()
                    updateMic(false)

                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            updateStatus(if (languageMode == LanguageMode.PERSIAN) "منتظر صدای شما…" else "Waiting for voice…")
                            scheduleRestart(260)
                        }
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                            updateStatus(if (languageMode == LanguageMode.PERSIAN) "موتور صوتی مشغول است…" else "Speech engine busy…")
                            scheduleRestart(900)
                        }
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                        SpeechRecognizer.ERROR_SERVER -> {
                            updateStatus(if (languageMode == LanguageMode.PERSIAN) "خطای اینترنت یا سرویس…" else "Network/service error…")
                            scheduleRestart(1500)
                        }
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            wantListening = false
                            updateStatus("اجازه میکروفون لازم است")
                        }
                        else -> {
                            updateStatus(if (languageMode == LanguageMode.PERSIAN) "تشخیص صوت متوقف شد" else "Speech recognition stopped")
                            if (wantListening) scheduleRestart(1100)
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
                        val normalized = normalizeSpeech(best)
                        if (normalized.isNotBlank()) {
                            currentInputConnection?.setComposingText(utterancePrefix + normalized, 1)
                            currentInputConnection?.finishComposingText()
                            previewText?.text = normalized
                        }
                    }

                    updateStatus(if (languageMode == LanguageMode.PERSIAN) "آماده جمله بعدی…" else "Ready for the next sentence…")
                    updateMic(false)
                    scheduleRestart(300)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull { it.isNotBlank() }
                        .orEmpty()

                    if (partial.isNotBlank()) {
                        val normalized = normalizeSpeech(partial)
                        previewText?.text = normalized
                        currentInputConnection?.setComposingText(utterancePrefix + normalized, 1)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun recognitionIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        val locale = if (languageMode == LanguageMode.PERSIAN) "fa-IR" else "en-US"
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
    }

    private fun normalizeSpeech(text: String): String =
        if (languageMode == LanguageMode.PERSIAN) VoiceTextNormalizer.normalize(text) else text.trim()

    private fun startVoiceLoop() {
        if (!hasMicPermission()) {
            wantListening = false
            updateStatus("برای استفاده، ابتدا اجازه میکروفون را در برنامه VoxFa فعال کنید")
            return
        }
        if (speechRecognizer == null) prepareRecognizer()
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
            speechRecognizer?.startListening(recognitionIntent())
            updateStatus(if (languageMode == LanguageMode.PERSIAN) "در حال آماده‌سازی میکروفون…" else "Preparing microphone…")
            updateMic(true)
        } catch (_: Throwable) {
            updateStatus(if (languageMode == LanguageMode.PERSIAN) "شروع تشخیص صوت ناموفق بود…" else "Could not start speech recognition…")
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
        updateStatus(if (languageMode == LanguageMode.PERSIAN) "میکروفون متوقف است" else "Microphone stopped")
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
        suggestionViews.clear()
        container.removeAllViews()

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(keyButton("⌨", weight = 0f, widthDp = 52, special = true) {
            stopVoiceLoop(clearComposing = true)
            showKeyboardPanel()
        })
        top.addView(TextView(this).apply {
            text = if (languageMode == LanguageMode.PERSIAN) "VoxFa • فارسی" else "VoxFa • English"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(45, 47, 57))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        top.addView(keyButton(if (languageMode == LanguageMode.PERSIAN) "EN" else "فا", 0f, 52, special = true) {
            toggleLanguage(returnToKeyboard = false)
        })
        container.addView(top, LinearLayout.LayoutParams(-1, dp(44)))

        statusText = TextView(this).apply {
            text = if (languageMode == LanguageMode.PERSIAN) "برای صحبت، میکروفون را لمس کنید" else "Tap the microphone to speak"
            textSize = 13f
            setTextColor(Color.rgb(101, 104, 115))
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(3), dp(8), dp(2))
        }
        container.addView(statusText, LinearLayout.LayoutParams(-1, dp(31)))

        micButton = TextView(this).apply {
            text = "●"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(66, 133, 244), 28f)
            elevation = dp(2).toFloat()
            setOnClickListener {
                if (wantListening) stopVoiceLoop(clearComposing = true) else startVoiceLoop()
            }
        }
        container.addView(micButton, LinearLayout.LayoutParams(-1, dp(72)).apply {
            leftMargin = dp(92)
            rightMargin = dp(92)
            topMargin = dp(3)
            bottomMargin = dp(7)
        })

        previewText = TextView(this).apply {
            text = if (languageMode == LanguageMode.PERSIAN) "متن شما مستقیماً در برنامه مقصد نوشته می‌شود" else "Your speech is typed directly into the current app"
            textSize = 13f
            maxLines = 2
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(72, 74, 84))
            background = rounded(Color.WHITE, 13f, Color.rgb(220, 222, 229))
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        container.addView(previewText, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(7) })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        actions.addView(keyButton("⌫", 1f, special = true) { deleteOne() })
        actions.addView(keyButton(if (languageMode == LanguageMode.PERSIAN) "فاصله" else "space", 2.2f) { smartSpace() })
        actions.addView(keyButton("↵", 1f, special = true) { sendEnter() })
        container.addView(actions, LinearLayout.LayoutParams(-1, dp(48)))

        if (startImmediately) mainHandler.postDelayed({ startVoiceLoop() }, 180)
    }

    private fun showKeyboardPanel() {
        if (!::container.isInitialized) return
        keyboardMode = true
        suggestionViews.clear()
        container.removeAllViews()

        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tools.addView(keyButton("●", 0f, 50, special = true) {
            showVoicePanel(startImmediately = true)
        })
        tools.addView(TextView(this).apply {
            text = "VoxFa"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(70, 72, 82))
        }, LinearLayout.LayoutParams(0, dp(38), 1f))
        tools.addView(keyButton(if (numberMode) "ABC" else "123", 0f, 58, special = true) {
            numberMode = !numberMode
            shiftEnabled = false
            showKeyboardPanel()
        })
        container.addView(tools, LinearLayout.LayoutParams(-1, dp(40)))

        addSuggestionBar()

        val rows = when {
            numberMode -> KeyboardLayouts.numberRows
            languageMode == LanguageMode.PERSIAN -> KeyboardLayouts.persianRows
            else -> KeyboardLayouts.englishRows.map { row ->
                if (shiftEnabled) row.map { it.uppercase(Locale.US) } else row
            }
        }

        rows.forEachIndexed { index, row ->
            val rowView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutDirection = if (!numberMode && languageMode == LanguageMode.PERSIAN) {
                    View.LAYOUT_DIRECTION_RTL
                } else View.LAYOUT_DIRECTION_LTR
            }

            if (!numberMode && languageMode == LanguageMode.ENGLISH && index == 2) {
                rowView.addView(keyButton(if (shiftEnabled) "⇧" else "↑", 0f, 48, special = true) {
                    shiftEnabled = !shiftEnabled
                    showKeyboardPanel()
                })
            }

            row.forEach { key ->
                rowView.addView(keyButton(key, 1f) {
                    commitKey(key)
                    if (!numberMode && languageMode == LanguageMode.ENGLISH && shiftEnabled) {
                        shiftEnabled = false
                    }
                })
            }

            if (!numberMode && languageMode == LanguageMode.ENGLISH && index == 2) {
                rowView.addView(repeatingDeleteButton(widthDp = 52))
            }
            container.addView(rowView, LinearLayout.LayoutParams(-1, dp(47)))
        }

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }

        if (numberMode || languageMode == LanguageMode.PERSIAN) {
            bottom.addView(repeatingDeleteButton())
        }
        bottom.addView(keyButton(if (numberMode) "ABC" else "🌐", 0f, 54, special = true) {
            if (numberMode) {
                numberMode = false
                showKeyboardPanel()
            } else toggleLanguage(returnToKeyboard = true)
        })
        bottom.addView(keyButton(if (languageMode == LanguageMode.PERSIAN) "فاصله" else "space", 2.8f) { smartSpace() })
        bottom.addView(keyButton(if (languageMode == LanguageMode.PERSIAN) "،" else ".", 0f, 48) {
            commit(if (languageMode == LanguageMode.PERSIAN) "،" else ".")
            refreshSuggestions()
        })
        bottom.addView(keyButton("↵", 0f, 58, special = true, action = true) { sendEnter() })
        container.addView(bottom, LinearLayout.LayoutParams(-1, dp(51)))
        refreshSuggestions()
    }

    private fun addSuggestionBar() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        repeat(3) {
            val view = TextView(this).apply {
                text = ""
                textSize = 14f
                maxLines = 1
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(65, 67, 76))
                setOnClickListener {
                    val suggestion = text.toString()
                    if (suggestion.isNotBlank()) replaceCurrentWord(suggestion)
                }
            }
            suggestionViews += view
            bar.addView(view, LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                setMargins(dp(1), 0, dp(1), 0)
            })
        }
        container.addView(bar, LinearLayout.LayoutParams(-1, dp(40)))
    }

    private fun refreshSuggestions() {
        if (suggestionViews.isEmpty()) return
        val prefix = currentWordBeforeCursor()
        val suggestions = if (languageMode == LanguageMode.PERSIAN && !numberMode) {
            SmartTextCorrector.suggestions(prefix)
        } else if (languageMode == LanguageMode.ENGLISH && !numberMode) {
            englishSuggestions(prefix)
        } else emptyList()

        suggestionViews.forEachIndexed { index, view ->
            view.text = suggestions.getOrNull(index).orEmpty()
        }
    }

    private fun englishSuggestions(prefix: String): List<String> {
        val p = prefix.lowercase(Locale.US)
        if (p.isBlank()) return listOf("the", "and", "you")
        val words = listOf("the", "this", "that", "thank", "thanks", "hello", "good", "great", "please", "keyboard", "voice", "text", "android", "download")
        return words.filter { it.startsWith(p) && it != p }.take(3)
    }

    private fun smartSpace() {
        if (languageMode == LanguageMode.PERSIAN && !numberMode) {
            val word = currentWordBeforeCursor()
            val corrected = SmartTextCorrector.correctWord(word)
            if (word.isNotBlank() && corrected != word) {
                currentInputConnection?.deleteSurroundingText(word.length, 0)
                currentInputConnection?.commitText(corrected, 1)
            }
        }
        commit(" ")
        refreshSuggestions()
    }

    private fun replaceCurrentWord(replacement: String) {
        val word = currentWordBeforeCursor()
        if (word.isNotEmpty()) currentInputConnection?.deleteSurroundingText(word.length, 0)
        currentInputConnection?.commitText(replacement, 1)
        refreshSuggestions()
    }

    private fun currentWordBeforeCursor(): String {
        val before = currentInputConnection?.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        if (before.isBlank()) return ""
        return before.takeLastWhile { ch -> ch.isLetter() || ch == '‌' || ch == '\'' }
    }

    private fun commitKey(key: String) {
        commit(key)
        refreshSuggestions()
    }

    private fun toggleLanguage(returnToKeyboard: Boolean) {
        val resumeVoice = wantListening
        stopVoiceLoop(clearComposing = true)
        languageMode = if (languageMode == LanguageMode.PERSIAN) LanguageMode.ENGLISH else LanguageMode.PERSIAN
        numberMode = false
        shiftEnabled = false
        prepareRecognizer()
        if (returnToKeyboard) {
            showKeyboardPanel()
        } else {
            showVoicePanel(startImmediately = resumeVoice)
        }
    }

    private fun keyButton(
        label: String,
        weight: Float,
        widthDp: Int? = null,
        special: Boolean = false,
        action: Boolean = false,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = label
            textSize = when {
                label.length == 1 -> 18.5f
                label.length > 5 -> 12.5f
                else -> 14f
            }
            gravity = Gravity.CENTER
            setTextColor(if (action) Color.WHITE else Color.rgb(40, 42, 49))
            background = rounded(
                when {
                    action -> Color.rgb(66, 133, 244)
                    special -> Color.rgb(219, 222, 229)
                    else -> Color.rgb(250, 250, 252)
                },
                7f
            )
            elevation = dp(if (special) 0 else 1).toFloat()
            setOnClickListener { onClick() }
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.alpha = 0.62f
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

    private fun repeatingDeleteButton(widthDp: Int? = null): TextView {
        val repeat = object : Runnable {
            override fun run() {
                deleteOne()
                refreshSuggestions()
                mainHandler.postDelayed(this, 65)
            }
        }

        return keyButton("⌫", if (widthDp == null) 1f else 0f, widthDp, special = true) { }.apply {
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.alpha = 0.62f
                        deleteOne()
                        refreshSuggestions()
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
                if (active) Color.rgb(234, 67, 53) else Color.rgb(66, 133, 244),
                28f
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
