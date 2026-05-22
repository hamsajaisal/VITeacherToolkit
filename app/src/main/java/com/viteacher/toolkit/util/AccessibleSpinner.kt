package com.viteacher.toolkit.util

import android.view.View
import android.widget.AdapterView
import android.widget.Spinner

fun Spinner.setAccessibleSelection(label: String, onItemSelected: ((position: Int) -> Unit)? = null) {
    this.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val selectedItem = parent?.getItemAtPosition(position).toString()
            this@setAccessibleSelection.contentDescription = "$label, $selectedItem, combo box"
            this@setAccessibleSelection.announceForAccessibility("$label, $selectedItem, selected")
            onItemSelected?.invoke(position)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            this@setAccessibleSelection.contentDescription = "$label, nothing selected, combo box"
        }
    }
}