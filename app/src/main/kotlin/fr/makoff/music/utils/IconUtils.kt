package fr.makoff.music.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object IconUtils {
    fun setIcon(context: Context, enabled: Boolean) {
        val pm = context.packageManager
        val desired = listOf(
            ComponentName(context, "fr.makoff.music.MainActivityAlias") to enabled,
            ComponentName(context, "fr.makoff.music.MainActivityStatic") to !enabled,
        )
        val changes = desired.filter { (component, target) ->
            val current = when (pm.getComponentEnabledSetting(component)) {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ->
                    pm.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS).enabled
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                else -> false
            }
            current != target
        }.sortedByDescending { it.second }
        // Reapplying unchanged aliases needlessly forces the launcher to rebuild its app list.
        if (changes.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33) {
            pm.setComponentEnabledSettings(changes.map { (component, target) ->
                PackageManager.ComponentEnabledSetting(component,
                    if (target) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP)
            })
        } else {
            for ((component, target) in changes) pm.setComponentEnabledSetting(component,
                if (target) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP)
        }
    }
}
