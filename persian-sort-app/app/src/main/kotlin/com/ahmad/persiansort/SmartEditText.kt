package com.ahmad.persiansort

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText

class SmartEditText(context: Context) : EditText(context) {
    var onBackspaceWhenEmpty: (() -> Unit)? = null

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(base, true) {
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (text.isNullOrEmpty() && beforeLength > 0) {
                    onBackspaceWhenEmpty?.invoke()
                }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DEL && text.isNullOrEmpty()) {
                    onBackspaceWhenEmpty?.invoke()
                }
                return super.sendKeyEvent(event)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DEL && text.isNullOrEmpty()) {
            onBackspaceWhenEmpty?.invoke()
        }
        return super.onKeyDown(keyCode, event)
    }
}
