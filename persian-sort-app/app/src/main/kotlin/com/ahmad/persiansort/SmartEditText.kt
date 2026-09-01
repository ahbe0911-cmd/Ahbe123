package com.ahmad.persiansort

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText

class SmartEditText(context: Context) : EditText(context) {
    var onBackspaceWhenEmpty: (() -> Unit)? = null
    private var lastBackspaceCallbackAt = 0L

    private fun notifyBackspaceWhenEmpty() {
        if (!text.isNullOrEmpty()) return
        val now = SystemClock.uptimeMillis()
        if (now - lastBackspaceCallbackAt < 90L) return
        lastBackspaceCallbackAt = now
        onBackspaceWhenEmpty?.invoke()
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(base, true) {
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0) notifyBackspaceWhenEmpty()
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DEL) {
                    notifyBackspaceWhenEmpty()
                }
                return super.sendKeyEvent(event)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DEL) notifyBackspaceWhenEmpty()
        return super.onKeyDown(keyCode, event)
    }
}
