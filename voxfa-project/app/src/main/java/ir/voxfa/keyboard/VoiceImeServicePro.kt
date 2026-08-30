package ir.voxfa.keyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
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
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class VoiceImeServicePro : InputMethodService() {

    private enum class LanguageMode { PERSIAN, ENGLISH }
    private enum class KeyStyle { NORMAL, SPECIAL, ACTION, SPACE }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var wantListening = false
    private var actuallyListening = false
    private var segmentedRequested = false
    private var numberMode = false
    private var shiftEnabled = false
    private var languageMode = LanguageMode.PERSIAN
    private var utterancePrefix = ""

    private lateinit var container: LinearLayout
    private var micButton: TextView? = null
    private var statusText: TextView? = null
    private val suggestionViews = mutableListOf<TextView>()

    override fun onCreate() {
        super.onCreate()
        prepareRecognizer()
    }

    override fun onCreateInputView(): View {
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(7))
            setBackgroundColor(Color.rgb(238, 240, 244))
            isSoundEffectsEnabled = false
        }
        showKeyboard()
        return container
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        showKeyboard()
        val autoStart = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
            .getBoolean(MainActivity.KEY_AUTO_START, true)
        if (autoStart && hasMicPermission()) mainHandler.postDelayed({ startSpeechLoop() }, 450)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        stopSpeechLoop(clearComposing = true, cancel = true)
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
                    setVoiceUi(true, if (isPersian()) "دارم می‌شنوم… ادامه بده" else "Listening… keep speaking")
                }

                override fun onBeginningOfSpeech() {
                    setVoiceUi(true, if (isPersian()) "صدات رو گرفتم…" else "Voice detected…")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    val pulse = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    micButton?.alpha = 0.78f + pulse * 0.22f
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    if (!segmentedRequested) {
                        actuallyListening = false
                        setVoiceUi(true, if (isPersian()) "در حال نوشتن…" else "Writing…")
                    }
                }

                override fun onError(error: Int) {
                    actuallyListening = false
                    currentInputConnection?.finishComposingText()
                    val label = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            if (isPersian()) "منتظر صدای شما…" else "Waiting for voice…"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                            if (isPersian()) "موتور صوتی مشغول است" else "Speech engine busy"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                            if (isPersian()) "خطای اینترنت یا سرویس" else "Network/service error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            wantListening = false
                            if (isPersian()) "اجازه میکروفون لازم است" else "Microphone permission required"
                        }
                        else -> if (isPersian()) "تشخیص صوت موقتاً متوقف شد" else "Speech recognition paused"
                    }
                    setVoiceUi(false, label)
                    if (wantListening) scheduleRestart(restartDelayFor(error))
                }

                override fun onResults(results: Bundle?) {
                    actuallyListening = false
                    commitSpeechBundle(results)
                    setVoiceUi(false, if (isPersian()) "آماده ادامه صحبت…" else "Ready for more…")
                    if (wantListening && !segmentedRequested) scheduleRestart(1200)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = bestText(partialResults)
                    if (partial.isBlank()) return
                    val normalized = normalizeSpeech(partial)
                    currentInputConnection?.setComposingText(utterancePrefix + normalized, 1)
                    showVoiceMessage(normalized)
                }

                override fun onSegmentResults(segmentResults: Bundle) {
                    commitSpeechBundle(segmentResults)
                    utterancePrefix = computeUtterancePrefix()
                    actuallyListening = true
                    setVoiceUi(true, if (isPersian()) "دارم می‌شنوم… ادامه بده" else "Still listening…")
                }

                override fun onEndOfSegmentedSession() {
                    actuallyListening = false
                    currentInputConnection?.finishComposingText()
                    setVoiceUi(false, if (isPersian()) "مکث طولانی؛ آماده ادامه…" else "Long pause; ready to continue…")
                    if (wantListening) scheduleRestart(1200)
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun recognitionIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        val locale = if (isPersian()) "fa-IR" else "en-US"
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)

        segmentedRequested = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
            putExtra(RecognizerIntent.EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION, true)
            putExtra(RecognizerIntent.EXTRA_ENABLE_BIASING_DEVICE_CONTEXT, true)
            putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(buildBiasingStrings()))
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 12_000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1_500)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3_000)
            putExtra(
                RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS
            )
        } else {
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1_200)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_400)
        }
    }

    private fun buildBiasingStrings(): List<String> {
        val recent = currentInputConnection?.getTextBeforeCursor(180, 0)?.toString().orEmpty()
        val recentWords = Regex("[\\p{L}\\u200C]{3,}").findAll(recent)
            .map { it.value }.distinct().toList().takeLast(12)
        val common = if (isPersian()) {
            listOf("فارسی", "کیبورد", "تایپ صوتی", "میکروفون", "اندروید", "اینترنت", "برنامه", "کتابخانه", "دانلود", "تنظیمات", "پیام", "ارسال", "نصب")
        } else {
            listOf("keyboard", "voice typing", "Android", "microphone", "download", "settings", "message")
        }
        return (recentWords + common).distinct().take(24)
    }

    private fun normalizeSpeech(text: String): String = if (isPersian()) {
        SmartTextCorrector.correctSentence(VoiceTextNormalizer.normalize(text))
    } else text.trim()

    private fun bestText(bundle: Bundle?): String = bundle
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull { it.isNotBlank() }
        .orEmpty()

    private fun commitSpeechBundle(bundle: Bundle?) {
        val text = normalizeSpeech(bestText(bundle))
        if (text.isBlank()) return
        currentInputConnection?.setComposingText(utterancePrefix + text, 1)
        currentInputConnection?.finishComposingText()
        showVoiceMessage(text)
    }

    private fun startSpeechLoop() {
        if (!hasMicPermission()) {
            wantListening = false
            setVoiceUi(false, if (isPersian()) "اجازه میکروفون لازم است" else "Microphone permission required")
            return
        }
        if (speechRecognizer == null) prepareRecognizer()
        if (speechRecognizer == null) {
            wantListening = false
            setVoiceUi(false, if (isPersian()) "سرویس تشخیص گفتار در دسترس نیست" else "Speech service unavailable")
            return
        }
        wantListening = true
        startOneRecognition()
    }

    private fun startOneRecognition() {
        if (!wantListening || actuallyListening || !isInputViewShown) return
        mainHandler.removeCallbacks(restartRunnable)
        try {
            actuallyListening = true
            setVoiceUi(true, if (isPersian()) "آماده‌سازی میکروفون…" else "Preparing microphone…")
            speechRecognizer?.startListening(recognitionIntent())
        } catch (_: Throwable) {
            actuallyListening = false
            setVoiceUi(false, if (isPersian()) "شروع میکروفون ناموفق بود" else "Could not start microphone")
            scheduleRestart(1800)
        }
    }

    private fun stopSpeechLoop(clearComposing: Boolean, cancel: Boolean = false) {
        wantListening = false
        mainHandler.removeCallbacks(restartRunnable)
        try {
            if (cancel) speechRecognizer?.cancel() else if (actuallyListening) speechRecognizer?.stopListening()
        } catch (_: Throwable) {}
        actuallyListening = false
        if (clearComposing) currentInputConnection?.finishComposingText()
        setVoiceUi(false, if (isPersian()) "میکروفون خاموش" else "Microphone off")
    }

    private val restartRunnable = Runnable { startOneRecognition() }

    private fun scheduleRestart(delayMs: Long) {
        if (!wantListening) return
        mainHandler.removeCallbacks(restartRunnable)
        mainHandler.postDelayed(restartRunnable, delayMs)
    }

    private fun restartDelayFor(error: Int): Long = when (error) {
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1800L
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> 2400L
        else -> 1400L
    }

    private fun showKeyboard() {
        if (!::container.isInitialized) return
        suggestionViews.clear()
        container.removeAllViews()
        addToolbar()
        addSuggestionBar()

        val rows = when {
            numberMode -> KeyboardLayouts.numberRows
            isPersian() -> KeyboardLayouts.persianRows
            else -> KeyboardLayouts.englishRows.map { row -> if (shiftEnabled) row.map { it.uppercase(Locale.US) } else row }
        }

        rows.forEachIndexed { index, row ->
            val rowView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutDirection = if (!numberMode && isPersian()) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                isSoundEffectsEnabled = false
            }

            if (!numberMode && index == 2) {
                if (isPersian()) {
                    rowView.addView(keyButton("؟", 0f, 48, KeyStyle.SPECIAL) { commitKey("؟") })
                } else {
                    rowView.addView(keyButton(if (shiftEnabled) "⇧" else "↑", 0f, 50, KeyStyle.SPECIAL) {
                        shiftEnabled = !shiftEnabled
                        showKeyboard()
                    })
                }
            }

            row.forEach { key ->
                rowView.addView(keyButton(key, 1f, style = KeyStyle.NORMAL) {
                    commitKey(key)
                    if (!numberMode && !isPersian() && shiftEnabled) shiftEnabled = false
                })
            }

            if (index == rows.lastIndex) rowView.addView(repeatingDeleteButton(54))
            container.addView(rowView, LinearLayout.LayoutParams(-1, dp(50)))
        }

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            isSoundEffectsEnabled = false
        }
        bottom.addView(keyButton(if (numberMode) "ABC" else "?123", 0f, 58, KeyStyle.SPECIAL) {
            numberMode = !numberMode
            shiftEnabled = false
            showKeyboard()
        })
        bottom.addView(keyButton("🌐", 0f, 52, KeyStyle.SPECIAL) { toggleLanguage() })
        bottom.addView(keyButton(if (isPersian()) "فارسی" else "English", 2.8f, style = KeyStyle.SPACE) { smartSpace() })
        bottom.addView(keyButton(if (isPersian()) "،" else ".", 0f, 46, KeyStyle.NORMAL) { commitKey(if (isPersian()) "،" else ".") })
        bottom.addView(keyButton("↵", 0f, 60, KeyStyle.ACTION) { sendEnter() })
        container.addView(bottom, LinearLayout.LayoutParams(-1, dp(54)))

        refreshSuggestions()
        updateMicStyle(wantListening || actuallyListening)
    }

    private fun addToolbar() {
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(1), dp(2), dp(1))
            isSoundEffectsEnabled = false
        }

        val title = TextView(this).apply {
            text = if (isPersian()) "VoxFa  •  فارسی" else "VoxFa  •  English"
            textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(54, 57, 69))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        toolbar.addView(title, LinearLayout.LayoutParams(0, dp(40), 1f))

        statusText = TextView(this).apply {
            text = if (wantListening) (if (isPersian()) "در حال شنیدن" else "Listening") else ""
            textSize = 11.5f
            setTextColor(Color.rgb(94, 97, 108))
            gravity = Gravity.CENTER
            maxLines = 1
        }
        toolbar.addView(statusText, LinearLayout.LayoutParams(dp(90), dp(40)))

        micButton = TextView(this).apply {
            text = "🎙"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(66, 133, 244), 20f)
            isSoundEffectsEnabled = false
            setOnClickListener {
                if (wantListening) stopSpeechLoop(clearComposing = false) else startSpeechLoop()
            }
        }
        toolbar.addView(micButton, LinearLayout.LayoutParams(dp(52), dp(40)).apply { setMargins(dp(4), 0, dp(2), 0) })
        container.addView(toolbar, LinearLayout.LayoutParams(-1, dp(42)))
    }

    private fun addSuggestionBar() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(2), dp(2), dp(2))
            isSoundEffectsEnabled = false
        }
        repeat(3) { index ->
            val view = TextView(this).apply {
                textSize = 13.8f
                gravity = Gravity.CENTER
                maxLines = 1
                setTextColor(Color.rgb(55, 58, 68))
                setTypeface(typeface, if (index == 0) Typeface.BOLD else Typeface.NORMAL)
                background = rounded(Color.rgb(247, 248, 251), 12f, Color.rgb(220, 223, 230))
                isSoundEffectsEnabled = false
                setOnClickListener {
                    val suggestion = text.toString()
                    if (suggestion.isNotBlank() && !actuallyListening) replaceCurrentWord(suggestion)
                }
            }
            suggestionViews += view
            bar.addView(view, LinearLayout.LayoutParams(0, dp(39), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        container.addView(bar, LinearLayout.LayoutParams(-1, dp(43)))
    }

    private fun refreshSuggestions() {
        if (suggestionViews.isEmpty() || actuallyListening) return
        val prefix = currentWordBeforeCursor()
        val suggestions = when {
            numberMode -> emptyList()
            isPersian() -> SmartTextCorrector.suggestions(prefix)
            else -> englishSuggestions(prefix)
        }
        val defaults = if (isPersian()) listOf("سلام", "ممنون", "باشه") else listOf("hello", "thanks", "okay")
        val display = if (suggestions.isEmpty()) defaults else suggestions
        suggestionViews.forEachIndexed { i, v -> v.text = display.getOrNull(i).orEmpty() }
    }

    private fun showVoiceMessage(message: String) {
        if (suggestionViews.isEmpty()) return
        suggestionViews.forEachIndexed { i, v -> v.text = if (i == 1) message else "" }
    }

    private fun setVoiceUi(active: Boolean, message: String) {
        statusText?.text = message
        updateMicStyle(active)
        if (active && message.length < 80) showVoiceMessage(message)
        else if (!active) mainHandler.postDelayed({ refreshSuggestions() }, 500)
    }

    private fun updateMicStyle(active: Boolean) {
        micButton?.apply {
            text = if (active) "■" else "🎙"
            textSize = if (active) 14f else 18f
            background = rounded(if (active) Color.rgb(234, 67, 53) else Color.rgb(66, 133, 244), 20f)
            alpha = 1f
        }
    }

    private fun englishSuggestions(prefix: String): List<String> {
        val p = prefix.lowercase(Locale.US)
        if (p.isBlank()) return listOf("hello", "thanks", "okay")
        val words = listOf("the", "this", "that", "thank", "thanks", "hello", "good", "great", "please", "keyboard", "voice", "text", "android", "download", "today", "tomorrow", "message")
        return words.filter { it.startsWith(p) && it != p }.take(3)
    }

    private fun smartSpace() {
        if (isPersian() && !numberMode) {
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
        return before.takeLastWhile { it.isLetter() || it == '‌' || it == '\'' }
    }

    private fun commitKey(key: String) {
        if (actuallyListening) stopSpeechLoop(clearComposing = true)
        commit(key)
        refreshSuggestions()
    }

    private fun toggleLanguage() {
        val resume = wantListening
        stopSpeechLoop(clearComposing = true, cancel = true)
        languageMode = if (isPersian()) LanguageMode.ENGLISH else LanguageMode.PERSIAN
        numberMode = false
        shiftEnabled = false
        prepareRecognizer()
        showKeyboard()
        if (resume) mainHandler.postDelayed({ startSpeechLoop() }, 350)
    }

    private fun keyButton(label: String, weight: Float, widthDp: Int? = null, style: KeyStyle, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = if (label.length == 1) 18.5f else 13f
        gravity = Gravity.CENTER
        setTextColor(if (style == KeyStyle.ACTION) Color.WHITE else Color.rgb(40, 43, 51))
        background = when (style) {
            KeyStyle.ACTION -> rounded(Color.rgb(66, 133, 244), 10f)
            KeyStyle.SPECIAL -> rounded(Color.rgb(218, 222, 229), 10f)
            KeyStyle.SPACE -> rounded(Color.rgb(248, 249, 251), 10f, Color.rgb(222, 225, 231))
            KeyStyle.NORMAL -> rounded(Color.WHITE, 10f, Color.rgb(226, 229, 235))
        }
        elevation = dp(if (style == KeyStyle.NORMAL || style == KeyStyle.ACTION) 1 else 0).toFloat()
        isSoundEffectsEnabled = false
        setOnClickListener { onClick() }
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(55).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
            }
            false
        }
        layoutParams = if (widthDp != null) {
            LinearLayout.LayoutParams(dp(widthDp), -1).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
        } else {
            LinearLayout.LayoutParams(0, -1, weight).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
        }
    }

    private fun repeatingDeleteButton(widthDp: Int): TextView {
        val repeat = object : Runnable {
            override fun run() {
                deleteOne()
                mainHandler.postDelayed(this, 68)
            }
        }
        return keyButton("⌫", 0f, widthDp, KeyStyle.SPECIAL) { }.apply {
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if (actuallyListening) stopSpeechLoop(clearComposing = true)
                        view.alpha = 0.65f
                        deleteOne()
                        mainHandler.postDelayed(repeat, 370)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.alpha = 1f
                        mainHandler.removeCallbacks(repeat)
                        refreshSuggestions()
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
        val ic = currentInputConnection ?: return
        ic.finishComposingText()
        val selected = try { ic.getSelectedText(0) } catch (_: Throwable) { null }
        if (!selected.isNullOrEmpty()) {
            if (!ic.commitText("", 1)) sendDeleteKey(ic)
            return
        }
        if (!ic.deleteSurroundingTextInCodePoints(1, 0)) sendDeleteKey(ic)
    }

    private fun sendDeleteKey(ic: InputConnection) {
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
    }

    private fun sendEnter() {
        if (actuallyListening) stopSpeechLoop(clearComposing = true)
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

    private fun isPersian(): Boolean = languageMode == LanguageMode.PERSIAN
    private fun hasMicPermission(): Boolean = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun rounded(fill: Int, radiusDp: Float, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
