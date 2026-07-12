package com.viteacher.toolkit.util

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.widget.TextView

object ThemeUtils {

    /**
     * Dynamically styles a roster list item (attendance row, submission status row, etc.)
     * to ensure perfect contrast and accessibility in both Light and Dark themes.
     *
     * @param context The context
     * @param rowView The parent row container view
     * @param tvRoll The text view displaying the roll number
     * @param tvName The text view displaying the student name
     * @param tvStatus The text view displaying the status label (e.g. Present/Absent, Completed/Pending)
     * @param isPositiveState True for positive/active states (Present, Completed), false for negative/pending states (Absent, Pending)
     */
    fun styleRosterRow(
        context: Context,
        rowView: View,
        tvRoll: TextView,
        tvName: TextView,
        tvStatus: TextView,
        isPositiveState: Boolean
    ) {
        val isDarkMode = (context.resources.configuration.uiMode and 
                          Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        if (isPositiveState) {
            val attrs = intArrayOf(android.R.attr.textColorPrimary, android.R.attr.textColorSecondary)
            val typedArray = context.theme.obtainStyledAttributes(attrs)
            val primaryColor = typedArray.getColor(0, Color.BLACK)
            val secondaryColor = typedArray.getColor(1, Color.GRAY)
            typedArray.recycle()

            rowView.setBackgroundColor(Color.TRANSPARENT)
            tvRoll.setTextColor(primaryColor)
            tvName.setTextColor(primaryColor)
            tvStatus.setTextColor(secondaryColor)
        } else {
            // Negative state (Absent, Pending) - Highlight in soft red/dark red
            if (isDarkMode) {
                rowView.setBackgroundColor(Color.parseColor("#5C0000")) // Dark red
                tvRoll.setTextColor(Color.WHITE)
                tvName.setTextColor(Color.WHITE)
                tvStatus.setTextColor(Color.parseColor("#FFD2D2")) // Light red/pinkish status text
            } else {
                rowView.setBackgroundColor(Color.parseColor("#FFEAEA")) // Light soft red background
                tvRoll.setTextColor(Color.parseColor("#B71C1C")) // Deep dark red text
                tvName.setTextColor(Color.parseColor("#B71C1C"))
                tvStatus.setTextColor(Color.parseColor("#D32F2F"))
            }
        }
    }
}
