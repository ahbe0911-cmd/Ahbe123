package ir.voxfa.keyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import java.util.Locale

class VoiceImeService : InputMethodService(), KeyboardCanvasView.Callback, SuggestionBarView.Callback {

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var wantsVoice = false
    private var listening = false
    private var utterancePrefix = ""

    private lateinit var root: LinearLayout
    private lateinit var suggestionBar: SuggestionBarView
    private lateinit var keyboardView: KeyboardCanvasView

    private var language = KeyboardCanvasView.Language.PERSIAN
    private var mode = KeyboardCanvasView.Mode.LETTERS
    private var shift = false

    override fun onCreate() {
        super.onCreate()
        prepareRecognizer()
    }

    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(0), dp(2), dp(0), dp(4))
        }

        suggestionBar = SuggestionBarView(this, this)
        keyboardView = KeyboardCanvasView(this, this).apply {
            language = this@VoiceImeService.language
            mode = this@VoiceImeService.mode
            shift = this@VoiceImeService.shift
        }

        root.addView(suggestionBar, LinearLayout.LayoutParams(-1, dp(48)))
        root.addView(keyboardView, LinearLayout.LayoutParams(-1, dp(218)))
        refreshSuggestions()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        refreshSuggestions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        stopVoice(clearComposing = true)
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { recognizer?.destroy() } catch (_: Throwable) {}
        recognizer = null
        super.onDestroy()
    }

    override fun onKey(key: KeyboardCanvasView.Key) {
        when (key.action) {
            KeyboardCanvasView.Action.TEXT -> {
                commit(key.value)
                if (language == KeyboardCanvasView.Language.ENGLISH && shift) {
                    shift = false
                    syncKeyboardState()
                }
                refreshSuggestions()
            }
            KeyboardCanvasView.Action.BACKSPACE -> {
                deleteOne()
                refreshSuggestions()
            }
            KeyboardCanvasView.Action.SHIFT -> {
                shift = !shift
                syncKeyboardState()
            }
            KeyboardCanvasView.Action.LANGUAGE -> toggleLanguage()
            KeyboardCanvasView.Action.SYMBOLS -> {
                mode = KeyboardCanvasView.Mode.SYMBOLS
                shift = false
                syncKeyboardState()
            }
            KeyboardCanvasView.Action.LETTERS -> {
                mode = KeyboardCanvasView.Mode.LETTERS
                shift = false
                syncKeyboardState()
            }
            KeyboardCanvasView.Action.SPACE -> smartSpace()
            KeyboardCanvasView.Action.ENTER -> sendEnter()
        }
    }

    override fun onBackspaceRepeat() {
        deleteOne()
        refreshSuggestions()
    }

    override fun onSuggestion(text: String) {
        replaceCurrentWord(text)
    }

    override fun onMic() {
        if (wantsVoice) stopVoice(clearComposing = true) else startVoice()
    }

    private fun syncKeyboardState() {
        if (!::keyboardView.isInitialized) return
        keyboardView.language = language
        keyboardView.mode = mode
        keyboardView.shift = shift
        refreshSuggestions()
    }

    private fun toggleLanguage() {
        val resumeVoice = wantsVoice
        stopVoice(clearComposing = true)
        language = if (language == KeyboardCanvasView.Language.PERSIAN) {
            KeyboardCanvasView.Language.ENGLISH
        } else KeyboardCanvasView.Language.PERSIAN
        mode = KeyboardCanvasView.Mode.LETTERS
        shift = false
        syncKeyboardState()
        if (resumeVoice) handler.postDelayed({ startVoice() }, 180)
    }

    private fun refreshSuggestions() {
        if (!::suggestionBar.isInitialized) return
        if (wantsVoice) return
        val prefix = currentWordBeforeCursor()
        suggestionBar.suggestions = when {
            mode != KeyboardCanvasView.Mode.LETTERS -> emptyList()
            language == KeyboardCanvasView.Language.PERSIAN -> SmartTextCorrector.suggestions(prefix)
            else -> englishSuggestions(prefix)
        }
        suggestionBar.statusText = if (language == KeyboardCanvasView.Language.PERSIAN) "فارسی" else "English"
    }

    private fun englishSuggestions(prefix: String): List<String> {
        val p = prefix.lowercase(Locale.US)
        val words = listOf(
            "the", "this", "that", "there", "their", "thank", "thanks", "hello", "good", "great",
            "please", "keyboard", "voice", "text", "android", "download", "install", "message", "today"
        )
        if (p.isBlank()) return listOf("the", "and", "you")
        return words.filter { it.startsWith(p) && it != p }.take(3)
    }

    private fun smartSpace() {
        if (language == KeyboardCanvasView.Language.PERSIAN && mode == KeyboardCanvasView.Mode.LETTERS) {
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
        val before = currentInputConnection?.getTextBeforeCursor(80, 0)?.toString().orEmpty()
        if (before.isBlank()) return ""
        return before.takeLastWhile { ch -> ch.isLetter() || ch == '‌' || ch == '\'' }
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

    private fun prepareRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        try { recognizer?.destroy() } catch (_: Throwable) {}
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    utterancePrefix = computeUtterancePrefix()
                    listening = true
                    updateVoiceUi(if (language == KeyboardCanvasView.Language.PERSIAN) "در حال شنیدن…" else "Listening…")
                }

                override fun onBeginningOfSpeech() {
                    updateVoiceUi(if (language == KeyboardCanvasView.Language.PERSIAN) "صدای شما دریافت شد…" else "Voice detected…")
                }

                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    listening = false
                    updateVoiceUi(if (language == KeyboardCanvasView.Language.PERSIAN) "در حال تبدیل گفتار…" else "Converting speech…")
                }

                override fun onError(error: Int) {
                    listening = false
                    currentInputConnection?.finishComposingText()
                    if (!wantsVoice) return
                    val delay = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 260L
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 900L
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> 1500L
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            wantsVoice = false
                            updateVoiceUi("اجازه میکروفون لازم است")
                            return
                        }
                        else -> 1000L
                    }
                    updateVoiceUi(if (language == KeyboardCanvasView.Language.PERSIAN) "آماده شنیدن…" else "Ready to listen…")
                    scheduleRestart(delay)
                }

                override fun onResults(results: Bundle?) {
                    listening = false
                    val best = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull { it.isNotBlank() }.orEmpty()
                    if (best.isNotBlank()) {
                        val normalized = normalizeSpeech(best)
                        if (normalized.isNotBlank()) {
                            currentInputConnection?.setComposingText(utterancePrefix + normalized, 1)
                            currentInputConnection?.finishComposingText()
                            suggestionBar.partialText = normalized
                        }
                    }
                    updateVoiceUi(if (language == KeyboardCanvasView.Language.PERSIAN) "آماده جمله بعدی…" else "Ready for the next sentence…")
                    scheduleRestart(320)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull { it.isNotBlank() }.orEmpty()
                    if (partial.isNotBlank()) {
                        val normalized = normalizeSpeech(partial)
                        suggestionBar.partialText = normalized
                        currentInputConnection?.setComposingText(utterancePrefix + normalized, 1)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun normalizeSpeech(text: String): String {
        return if (language == KeyboardCanvasView.Language.PERSIAN) {
            SmartTextCorrector.correctSentence(VoiceTextNormalizer.normalize(text))
        } else text.trim()
    }

    private fun recognitionIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        val locale = if (language == KeyboardCanvasView.Language.PERSIAN) "fa-IR" else "en-US"
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
    }

    private fun startVoice() {
        if (!hasMicPermission()) {
            updateVoiceUi("ابتدا اجازه میکروفون VoxFa را فعال کنید")
            return
        }
        if (recognizer == null) prepareRecognizer()
        if (recognizer == null) {
            updateVoiceUi("سرویس تشخیص گفتار روی گوشی در دسترس نیست")
            return
        }
        wantsVoice = true
        suggestionBar.voiceActive = true
        suggestionBar.partialText = ""
        updateVoiceUi(if (language == KeyboardCanvasView.Language.PERSIAN) "در حال آماده‌سازی میکروفون…" else "Preparing microphone…")
        startOneRecognition()
    }

    private fun startOneRecognition() {
        if (!wantsVoice || listening || !isInputViewShown) return
        handler.removeCallbacks(restartRunnable)
        try {
            recognizer?.startListening(recognitionIntent())
        } catch (_: Throwable) {
            scheduleRestart(1000)
        }
    }

    private fun stopVoice(clearComposing: Boolean) {
        wantsVoice = false
        listening = false
        handler.removeCallbacks(restartRunnable)
        try { recognizer?.cancel() } catch (_: Throwable) {}
        if (clearComposing) currentInputConnection?.finishComposingText()
        if (::suggestionBar.isInitialized) {
            suggestionBar.voiceActive = false
            suggestionBar.partialText = ""
            refreshSuggestions()
        }
    }

    private fun updateVoiceUi(text: String) {
        if (::suggestionBar.isInitialized) {
            suggestionBar.statusText = text
            suggestionBar.voiceActive = wantsVoice
            suggestionBar.invalidate()
        }
    }

    private val restartRunnable = Runnable { startOneRecognition() }
    private fun scheduleRestart(delay: Long) {
        if (!wantsVoice) return
        handler.removeCallbacks(restartRunnable)
        handler.postDelayed(restartRunnable, delay)
    }

    private fun computeUtterancePrefix(): String {
        val before = currentInputConnection?.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        return if (before.isNotEmpty() && !before.last().isWhitespace() && before.last() !in listOf('(', '[', '{', '«')) " " else ""
    }

    private fun hasMicPermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
