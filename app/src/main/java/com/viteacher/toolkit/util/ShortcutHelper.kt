package com.viteacher.toolkit.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.viteacher.toolkit.ui.PinLoginActivity

object ShortcutHelper {
    fun pinShortcut(context: Context, id: String, label: String, targetIntent: Intent) {
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            val pinIntent = Intent(context, PinLoginActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("target", "shortcut")
                putExtra("shortcut_target_intent", targetIntent)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            val shortcutInfo = ShortcutInfoCompat.Builder(context, id)
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(IconCompat.createWithResource(context, com.viteacher.toolkit.R.mipmap.ic_launcher))
                .setIntent(pinIntent)
                .build()

            ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
            Toast.makeText(context, "Adding shortcut to home screen...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Shortcut pinning not supported by your launcher.", Toast.LENGTH_LONG).show()
        }
    }
}
