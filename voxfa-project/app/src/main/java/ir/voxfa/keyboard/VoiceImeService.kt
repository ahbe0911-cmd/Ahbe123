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
    private var isListening = false
    private var numberMode = false
    private var shiftEnabled = false
    private var languageMode = LanguageMode.PERSIAN
    private var utterancePrefix = ""

    private lateinit var container: LinearLayout
    private var micButton: TextView? = null
    private var brandText: TextView? = null
    private val suggestionViews = mutableListOf<TextView>()

    private val vazirTypeface: Typeface by lazy {
        try {
            resources.getFont(R.font.vazirmatn_regular)
        } catch (_: Throwable) {
            Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Locale.setDefault(Locale("fa", "IR"))
        prepareRecognizer()
    }

    override fun onCreateInputView(): View {
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(5), dp(6), dp(7))
            background = verticalGradient(
                intArrayOf(
                    Color.rgb(247, 248, 255),
                    Color.rgb(239, 243, 250)
                ),
                0f
            )
            isSoundEffectsEnabled = false
            isHapticFeedbackEnabled = false
        }
        showKeyboardPanel()
        return container
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        stopSpeech(clearComposing = true, keepMessage = true)
        showKeyboardPanel()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        stopSpeech(clearComposing = true, keepMessage = true)
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
                    isListening = true
                    setVoiceUi(active = true, message = listeningLabel())
                }

                override fun onBeginningOfSpeech() {
                    setVoiceUi(active = true, message = heardLabel())
                }

                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    setVoiceUi(active = true, message = processingLabel())
                }

                override fun onError(error: Int) {
                    isListening = false
                    currentInputConnection?.finishComposingText()
                    val message = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> noSpeechLabel()
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> busyLabel()
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                        SpeechRecognizer.ERROR_SERVER -> networkLabel()
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> permissionLabel()
                        else -> stoppedLabel()
                    }
                    setVoiceUi(active = false, message = message)
                    restoreSuggestionsSoon()
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val best = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull { it.isNotBlank() }
                        .orEmpty()

                    if (best.isNotBlank()) {
                        val normalized = normalizeSpeech(best)
                        if (normalized.isNotBlank()) {
                            currentInputConnection?.setComposingText(utterancePrefix + normalized, 1)
                            currentInputConnection?.finishComposingText()
                        }
                    }

                    setVoiceUi(active = false, message = doneLabel())
                    mainHandler.postDelayed({ refreshSuggestions() }, 500)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull { it.isNotBlank() }
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        val normalized = normalizeSpeech(partial)
                        currentInputConnection?.setComposingText(utterancePrefix + normalized, 1)
                        showVoiceMessage(normalized)
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

    private fun toggleSpeech() {
        if (isListening) stopSpeech(clearComposing = false, keepMessage = false) else startSpeech()
    }

    private fun startSpeech() {
        if (!hasMicPermission()) {
            setVoiceUi(active = false, message = permissionLabel())
            restoreSuggestionsSoon()
            return
        }
        if (speechRecognizer == null) prepareRecognizer()
        val recognizer = speechRecognizer
        if (recognizer == null) {
            setVoiceUi(active = false, message = unavailableLabel())
            restoreSuggestionsSoon()
            return
        }

        try {
            currentInputConnection?.finishComposingText()
            isListening = true
            setVoiceUi(active = true, message = preparingLabel())
            recognizer.startListening(recognitionIntent())
        } catch (_: Throwable) {
            isListening = false
            setVoiceUi(active = false, message = stoppedLabel())
            restoreSuggestionsSoon()
        }
    }

    private fun stopSpeech(clearComposing: Boolean, keepMessage: Boolean) {
        isListening = false
        try { speechRecognizer?.cancel() } catch (_: Throwable) {}
        if (clearComposing) currentInputConnection?.finishComposingText()
        micButton?.animate()?.cancel()
        micButton?.scaleX = 1f
        micButton?.scaleY = 1f
        updateMicStyle(false)
        if (!keepMessage && ::container.isInitialized) refreshSuggestions()
    }

    private fun showKeyboardPanel() {
        if (!::container.isInitialized) return
        suggestionViews.clear()
        container.removeAllViews()

        addToolbar()
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
                } else {
                    View.LAYOUT_DIRECTION_LTR
                }
                isSoundEffectsEnabled = false
                isHapticFeedbackEnabled = false
            }

            if (!numberMode && languageMode == LanguageMode.ENGLISH && index == 2) {
                rowView.addView(keyButton(if (shiftEnabled) "⇧" else "↑", 0f, 50, KeyStyle.SPECIAL) {
                    stopSpeech(clearComposing = false, keepMessage = true)
                    shiftEnabled = !shiftEnabled
                    showKeyboardPanel()
                })
            }

            row.forEach { key ->
                rowView.addView(keyButton(key, 1f, style = KeyStyle.NORMAL) {
                    commitKey(key)
                    if (!numberMode && languageMode == LanguageMode.ENGLISH && shiftEnabled) {
                        shiftEnabled = false
                    }
                })
            }

            if (!numberMode && languageMode == LanguageMode.ENGLISH && index == 2) {
                rowView.addView(repeatingDeleteButton(widthDp = 54))
            }
            container.addView(rowView, LinearLayout.LayoutParams(-1, dp(51)))
        }

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            isSoundEffectsEnabled = false
            isHapticFeedbackEnabled = false
        }

        if (numberMode || languageMode == LanguageMode.PERSIAN) {
            bottom.addView(repeatingDeleteButton())
        }

        bottom.addView(keyButton(if (numberMode) "ABC" else "🌐", 0f, 55, KeyStyle.SPECIAL) {
            stopSpeech(clearComposing = false, keepMessage = true)
            if (numberMode) {
                numberMode = false
                showKeyboardPanel()
            } else {
                toggleLanguage()
            }
        })

        bottom.addView(keyButton(if (languageMode == LanguageMode.PERSIAN) "فارسی" else "English", 2.75f, style = KeyStyle.SPACE) {
            smartSpace()
        })

        bottom.addView(keyButton(if (languageMode == LanguageMode.PERSIAN) "،" else ".", 0f, 48, KeyStyle.NORMAL) {
            commit(if (languageMode == LanguageMode.PERSIAN) "،" else ".")
            refreshSuggestions()
        })

        bottom.addView(keyButton("↵", 0f, 60, KeyStyle.ACTION) { sendEnter() })
        container.addView(bottom, LinearLayout.LayoutParams(-1, dp(54)))

        refreshSuggestions()
        updateMicStyle(isListening)
    }

    private fun addToolbar() {
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(1), dp(2), dp(2))
            isSoundEffectsEnabled = false
            isHapticFeedbackEnabled = false
        }

        val modeButton = chipButton(if (numberMode) "ABC" else "123", Color.rgb(238, 232, 255)) {
            stopSpeech(clearComposing = false, keepMessage = true)
            numberMode = !numberMode
            shiftEnabled = false
            showKeyboardPanel()
        }
        toolbar.addView(modeButton, LinearLayout.LayoutParams(dp(58), dp(38)).apply {
            setMargins(dp(1), 0, dp(4), 0)
        })

        brandText = TextView(this).apply {
            text = brandLabel()
            textSize = 13.2f
            typeface = Typeface.create(vazirTypeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(58, 60, 76))
            background = horizontalGradient(
                intArrayOf(
                    Color.rgb(236, 246, 255),
                    Color.rgb(242, 236, 255),
                    Color.rgb(255, 239, 247)
                ),
                18f
            )
            setPadding(dp(9), 0, dp(9), 0)
            isSoundEffectsEnabled = false
            isHapticFeedbackEnabled = false
        }
        toolbar.addView(brandText, LinearLayout.LayoutParams(0, dp(38), 1f).apply {
            setMargins(dp(2), 0, dp(4), 0)
        })

        val lang = chipButton(if (languageMode == LanguageMode.PERSIAN) "EN" else "فا", Color.rgb(230, 248, 241)) {
            stopSpeech(clearComposing = false, keepMessage = true)
            toggleLanguage()
        }
        toolbar.addView(lang, LinearLayout.LayoutParams(dp(52), dp(38)).apply {
            setMargins(dp(1), 0, dp(4), 0)
        })

        micButton = TextView(this).apply {
            text = "🎙"
            textSize = 18f
            typeface = vazirTypeface
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = horizontalGradient(
                intArrayOf(Color.rgb(109, 91, 255), Color.rgb(255, 79, 139)),
                19f
            )
            elevation = dp(2).toFloat()
            isSoundEffectsEnabled = false
            isHapticFeedbackEnabled = false
            setOnClickListener { toggleSpeech() }
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(if (isListening) 1.06f else 1f).scaleY(if (isListening) 1.06f else 1f).setDuration(100).start()
                }
                false
            }
        }
        toolbar.addView(micButton, LinearLayout.LayoutParams(dp(50), dp(38)).apply {
            setMargins(dp(1), 0, dp(1), 0)
        })

        container.addView(toolbar, LinearLayout.LayoutParams(-1, dp(42)))
    }

    private fun addSuggestionBar() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(1), dp(2), dp(1), dp(2))
            isSoundEffectsEnabled = false
            isHapticFeedbackEnabled = false
        }

        val fills = intArrayOf(
            Color.rgb(236, 248, 244),
            Color.rgb(241, 237, 255),
            Color.rgb(255, 241, 247)
        )
        val strokes = intArrayOf(
            Color.rgb(207, 233, 224),
            Color.rgb(221, 214, 248),
            Color.rgb(244, 217, 229)
        )

        repeat(3) { index ->
            val view = TextView(this).apply {
                text = ""
                textSize = 13.6f
                typeface = vazirTypeface
                maxLines = 1
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(55, 58, 72))
                background = rounded(fills[index], 18f, strokes[index])
                setPadding(dp(7), 0, dp(7), 0)
                isSoundEffectsEnabled = false
                isHapticFeedbackEnabled = false
                setOnClickListener {
                    val suggestion = text.toString()
                    if (suggestion.isNotBlank() && !isListening) replaceCurrentWord(suggestion)
                }
            }
            suggestionViews += view
            bar.addView(view, LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            })
        }
        container.addView(bar, LinearLayout.LayoutParams(-1, dp(43)))
    }

    private fun refreshSuggestions() {
        if (suggestionViews.isEmpty() || isListening) return
        brandText?.text = brandLabel()
        val prefix = currentWordBeforeCursor()
        val suggestions = when {
            numberMode -> emptyList()
            languageMode == LanguageMode.PERSIAN -> SmartTextCorrector.suggestions(prefix)
            else -> englishSuggestions(prefix)
        }
        val defaults = if (languageMode == LanguageMode.PERSIAN) {
            listOf("سلام", "ممنون", "باشه")
        } else {
            listOf("hello", "thanks", "okay")
        }
        val display = if (suggestions.isEmpty()) defaults else suggestions
        suggestionViews.forEachIndexed { index, view ->
            view.text = display.getOrNull(index).orEmpty()
        }
    }

    private fun showVoiceMessage(message: String) {
        if (suggestionViews.isEmpty()) return
        suggestionViews.forEachIndexed { index, view ->
            view.text = if (index == 1) message else ""
        }
    }

    private fun setVoiceUi(active: Boolean, message: String) {
        isListening = active
        updateMicStyle(active)
        brandText?.text = if (active) listeningBrandLabel() else brandLabel()
        showVoiceMessage(message)
    }

    private fun updateMicStyle(active: Boolean) {
        micButton?.apply {
            text = if (active) "■" else "🎙"
            textSize = if (active) 14f else 18f
            background = if (active) {
                horizontalGradient(
                    intArrayOf(Color.rgb(255, 76, 126), Color.rgb(255, 119, 82)),
                    19f
                )
            } else {
                horizontalGradient(
                    intArrayOf(Color.rgb(109, 91, 255), Color.rgb(255, 79, 139)),
                    19f
                )
            }
            animate().cancel()
            animate().scaleX(if (active) 1.06f else 1f).scaleY(if (active) 1.06f else 1f).setDuration(140).start()
        }
    }

    private fun restoreSuggestionsSoon() {
        mainHandler.postDelayed({ refreshSuggestions() }, 900)
    }

    private fun englishSuggestions(prefix: String): List<String> {
        val p = prefix.lowercase(Locale.US)
        if (p.isBlank()) return listOf("hello", "thanks", "okay")
        val words = listOf(
            "the", "this", "that", "thank", "thanks", "hello", "good", "great", "please",
            "keyboard", "voice", "text", "android", "download", "today", "tomorrow", "message"
        )
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
        if (isListening) stopSpeech(clearComposing = true, keepMessage = true)
        commit(key)
        refreshSuggestions()
    }

    private fun toggleLanguage() {
        stopSpeech(clearComposing = true, keepMessage = true)
        languageMode = if (languageMode == LanguageMode.PERSIAN) LanguageMode.ENGLISH else LanguageMode.PERSIAN
        numberMode = false
        shiftEnabled = false
        showKeyboardPanel()
    }

    private enum class KeyStyle { NORMAL, SPECIAL, ACTION, SPACE }

    private fun keyButton(
        label: String,
        weight: Float,
        widthDp: Int? = null,
        style: KeyStyle,
        onClick: () -> Unit
    ): TextView = TextView(this).apply {
        text = label
        textSize = when {
            label.length == 1 -> 18.5f
            label.length > 6 -> 12.2f
            else -> 13.2f
        }
        typeface = if (style == KeyStyle.ACTION) Typeface.create(vazirTypeface, Typeface.BOLD) else vazirTypeface
        gravity = Gravity.CENTER
        setTextColor(if (style == KeyStyle.ACTION) Color.WHITE else Color.rgb(38, 41, 55))
        background = when (style) {
            KeyStyle.ACTION -> horizontalGradient(
                intArrayOf(Color.rgb(92, 91, 255), Color.rgb(118, 86, 230)),
                13f
            )
            KeyStyle.SPECIAL -> rounded(Color.rgb(228, 230, 244), 13f, Color.rgb(214, 216, 234))
            KeyStyle.SPACE -> rounded(Color.rgb(247, 248, 252), 13f, Color.rgb(222, 224, 234))
            KeyStyle.NORMAL -> rounded(Color.rgb(255, 255, 255), 13f, Color.rgb(226, 228, 236))
        }
        elevation = dp(if (style == KeyStyle.NORMAL || style == KeyStyle.ACTION) 1 else 0).toFloat()
        isSoundEffectsEnabled = false
        isHapticFeedbackEnabled = false
        setOnClickListener { onClick() }
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.93f).scaleY(0.93f).setDuration(65).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
            }
            false
        }
        layoutParams = if (widthDp != null) {
            LinearLayout.LayoutParams(dp(widthDp), -1).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
        } else {
            LinearLayout.LayoutParams(0, -1, weight).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
        }
    }

    private fun chipButton(label: String, fill: Int, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 12.6f
        typeface = Typeface.create(vazirTypeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(57, 59, 72))
        background = rounded(fill, 18f)
        isSoundEffectsEnabled = false
        isHapticFeedbackEnabled = false
        setOnClickListener { onClick() }
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(60).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
            }
            false
        }
    }

    private fun repeatingDeleteButton(widthDp: Int? = null): TextView {
        val repeat = object : Runnable {
            override fun run() {
                deleteOne()
                refreshSuggestions()
                mainHandler.postDelayed(this, 62)
            }
        }

        return keyButton("⌫", if (widthDp == null) 1f else 0f, widthDp, KeyStyle.SPECIAL) { }.apply {
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if (isListening) stopSpeech(clearComposing = true, keepMessage = true)
                        view.animate().scaleX(0.93f).scaleY(0.93f).setDuration(60).start()
                        deleteOne()
                        refreshSuggestions()
                        mainHandler.postDelayed(repeat, 350)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
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
        if (isListening) stopSpeech(clearComposing = true, keepMessage = true)
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

    private fun brandLabel(): String = if (languageMode == LanguageMode.PERSIAN) "VoxFa  •  فارسی" else "VoxFa  •  English"
    private fun listeningBrandLabel(): String = if (languageMode == LanguageMode.PERSIAN) "VoxFa  •  میکروفون روشن" else "VoxFa  •  Mic on"
    private fun preparingLabel(): String = if (languageMode == LanguageMode.PERSIAN) "آماده‌سازی…" else "Preparing…"
    private fun listeningLabel(): String = if (languageMode == LanguageMode.PERSIAN) "در حال شنیدن…" else "Listening…"
    private fun heardLabel(): String = if (languageMode == LanguageMode.PERSIAN) "صدات رو گرفتم…" else "Voice detected…"
    private fun processingLabel(): String = if (languageMode == LanguageMode.PERSIAN) "در حال نوشتن…" else "Writing…"
    private fun doneLabel(): String = if (languageMode == LanguageMode.PERSIAN) "انجام شد ✓" else "Done ✓"
    private fun noSpeechLabel(): String = if (languageMode == LanguageMode.PERSIAN) "صدایی دریافت نشد" else "No speech detected"
    private fun busyLabel(): String = if (languageMode == LanguageMode.PERSIAN) "موتور صوتی مشغول است" else "Speech engine busy"
    private fun networkLabel(): String = if (languageMode == LanguageMode.PERSIAN) "اینترنت یا سرویس در دسترس نیست" else "Network/service unavailable"
    private fun permissionLabel(): String = if (languageMode == LanguageMode.PERSIAN) "اجازه میکروفون لازم است" else "Microphone permission required"
    private fun unavailableLabel(): String = if (languageMode == LanguageMode.PERSIAN) "تایپ صوتی روی این گوشی در دسترس نیست" else "Voice typing is unavailable"
    private fun stoppedLabel(): String = if (languageMode == LanguageMode.PERSIAN) "میکروفون خاموش شد" else "Microphone stopped"

    private fun hasMicPermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

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
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp.toInt()).toFloat()
    }

    private fun verticalGradient(colors: IntArray, radiusDp: Float): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        colors
    ).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp.toInt()).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
