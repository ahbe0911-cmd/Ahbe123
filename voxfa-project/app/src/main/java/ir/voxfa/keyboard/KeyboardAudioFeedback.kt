package ir.voxfa.keyboard

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Low-latency keyboard feedback backed by Android's familiar system key effects.
 * No audio files are decoded and all playback calls are asynchronous.
 */
class KeyboardAudioFeedback(context: Context) {

    enum class KeyKind { STANDARD, SPACE, DELETE, RETURN }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val micTone = runCatching {
        ToneGenerator(AudioManager.STREAM_SYSTEM, MIC_TONE_VOLUME)
    }.getOrNull()

    fun prepare() {
        runCatching { audioManager.loadSoundEffects() }
    }

    fun playKey(kind: KeyKind = KeyKind.STANDARD) {
        val effect = when (kind) {
            KeyKind.STANDARD -> AudioManager.FX_KEYPRESS_STANDARD
            KeyKind.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
            KeyKind.DELETE -> AudioManager.FX_KEYPRESS_DELETE
            KeyKind.RETURN -> AudioManager.FX_KEYPRESS_RETURN
        }
        runCatching { audioManager.playSoundEffect(effect, KEY_VOLUME) }
    }

    fun playMicrophoneStarted() {
        playMicTone(
            tone = ToneGenerator.TONE_PROP_ACK,
            durationMs = 90,
            fallback = AudioManager.FX_KEYPRESS_RETURN
        )
    }

    fun playMicrophoneStopped() {
        playMicTone(
            tone = ToneGenerator.TONE_PROP_BEEP2,
            durationMs = 110,
            fallback = AudioManager.FX_KEYPRESS_DELETE
        )
    }

    fun release() {
        runCatching { micTone?.release() }
    }

    private fun playMicTone(tone: Int, durationMs: Int, fallback: Int) {
        val played = runCatching { micTone?.startTone(tone, durationMs) == true }.getOrDefault(false)
        if (!played) runCatching { audioManager.playSoundEffect(fallback, MIC_FALLBACK_VOLUME) }
    }

    private companion object {
        const val KEY_VOLUME = 0.34f
        const val MIC_FALLBACK_VOLUME = 0.55f
        const val MIC_TONE_VOLUME = 62
    }
}
