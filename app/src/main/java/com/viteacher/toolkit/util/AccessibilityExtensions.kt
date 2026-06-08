package com.viteacher.toolkit.util

import android.view.View
import android.view.ViewGroup
import android.widget.EditText

/**
 * Traverses a View hierarchy recursively and registers focus change listeners
 * on every EditText to place the cursor at the end of the text upon gaining focus.
 */
fun View.setupCursorEndForEditTexts() {
    if (this is EditText) {
        val originalListener = this.onFocusChangeListener
        this.setOnFocusChangeListener { v, hasFocus ->
            originalListener?.onFocusChange(v, hasFocus)
            if (hasFocus) {
                val length = this.text?.length ?: 0
                if (length > 0) {
                    this.post {
                        this.setSelection(length)
                    }
                }
            }
        }
    } else if (this is ViewGroup) {
        for (i in 0 until this.childCount) {
            this.getChildAt(i).setupCursorEndForEditTexts()
        }
    }
}
