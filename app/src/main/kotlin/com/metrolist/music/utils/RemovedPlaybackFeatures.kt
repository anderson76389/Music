package fr.makoff.music.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.booleanPreferencesKey
import fr.makoff.music.playback.MusicService
import kotlinx.coroutines.flow.first
import org.json.JSONArray

/** Cancel PendingIntents created by earlier versions before discarding their settings. */
internal object RemovedPlaybackFeatures {
    suspend fun clean(context: Context) {
        val done = booleanPreferencesKey("removedPlaybackFeaturesV1")
        val prefs = context.dataStore.data.first()
        if (prefs[done] == true) return
        val legacyAlarmPrefs = context.createDeviceProtectedStorageContext()
            .getSharedPreferences("alarm_store", Context.MODE_PRIVATE)
        val ids = mutableSetOf("legacy-main-alarm")
        val rawEntries = listOf(legacyAlarmPrefs.getString("alarm_entries", "").orEmpty(),
            prefs.asMap().entries.firstOrNull { it.key.name == "alarmEntries" }?.value as? String ?: "")
        for (raw in rawEntries) {
            if (raw.isBlank()) continue
            val entries = runCatching { JSONArray(raw) }.getOrNull() ?: continue
            for (index in 0 until entries.length()) {
                entries.optJSONObject(index)?.optString("id")?.takeIf { it.isNotBlank() }?.let(ids::add)
            }
        }
        val manager = context.getSystemService(AlarmManager::class.java)
        for (id in ids) {
            val code = id.hashCode() and Int.MAX_VALUE
            val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            val service = Intent(context, MusicService::class.java).setAction("fr.makoff.music.action.ALARM_TRIGGER")
            val receiver = Intent().setComponent(ComponentName(context.packageName,
                "fr.makoff.music.playback.alarm.MusicAlarmReceiver")).setAction("fr.makoff.music.action.TRIGGER_ALARM")
            val pending = listOf(PendingIntent.getForegroundService(context, code, service, flags),
                PendingIntent.getBroadcast(context, code, receiver, flags))
            pending.filterNotNull().forEach { manager?.cancel(it); it.cancel() }
        }
        check(legacyAlarmPrefs.edit().clear().commit())
        context.safeDataStoreEdit { settings ->
            settings.asMap().keys.filter { key -> key.name.startsWith("alarm") || key.name.startsWith("sleepTimer") ||
                key.name.contains("lyrics", ignoreCase = true) }.forEach { key -> settings.remove(key) }
            settings[done] = true
        }
    }
}
