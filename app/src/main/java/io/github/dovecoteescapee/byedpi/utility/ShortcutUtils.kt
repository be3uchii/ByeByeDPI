package io.github.dovecoteescapee.byedpi.utility

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import io.github.dovecoteescapee.byedpi.R
import io.github.dovecoteescapee.byedpi.activities.ToggleActivity

object ShortcutUtils {

    fun update(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java)
            val shortcuts = mutableListOf<ShortcutInfo>()

            val toggleIntent = Intent(context, ToggleActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val toggleShortcut = ShortcutInfo.Builder(context, "toggle_service")
                .setShortLabel(context.getString(R.string.toggle_connect))
                .setLongLabel(context.getString(R.string.toggle_connect))
                .setIcon(Icon.createWithResource(context, R.drawable.ic_toggle))
                .setIntent(toggleIntent)
                .build()

            shortcuts.add(toggleShortcut)

            shortcutManager.dynamicShortcuts = shortcuts
        }
    }
}
