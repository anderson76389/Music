package fr.makoff.music.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.makoff.music.BuildConfig
import fr.makoff.music.R
import fr.makoff.music.constants.CheckForUpdatesKey
import fr.makoff.music.constants.DismissedStandaloneUpdateKey
import fr.makoff.music.constants.UpdateNotificationsEnabledKey
import fr.makoff.music.ui.screens.settings.ApkUpdateActivity
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

object UpdateNotifications {
    private const val WORK_NAME = "published-apk-update"
    private const val NOTIFICATION_ID = 1001

    fun schedule(context: Context, enabled: Boolean) {
        val work = WorkManager.getInstance(context)
        if (enabled && BuildConfig.UPDATER_AVAILABLE) {
            work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build())
        } else {
            work.cancelUniqueWork(WORK_NAME)
            cancel(context)
        }
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    fun show(context: Context, release: ReleaseInfo) {
        val url = Updater.getDownloadUrlForCurrentVariant(release) ?: return
        if (!Updater.isOwnApkUrl(url)) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("updates", context.getString(R.string.update_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.update_channel_desc) })
        val installer = PendingIntent.getActivity(context, NOTIFICATION_ID,
            ApkUpdateActivity.intent(context, url), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID,
            NotificationCompat.Builder(context, "updates")
                .setSmallIcon(R.drawable.update)
                .setContentTitle(context.getString(R.string.update_available_title))
                .setContentText(release.versionName)
                .setContentIntent(installer)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build())
    }
}

class UpdateCheckWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!BuildConfig.UPDATER_AVAILABLE) return Result.success()
        val preferences = applicationContext.dataStore.data.first()
        if (preferences[CheckForUpdatesKey] == false || preferences[UpdateNotificationsEnabledKey] == false) {
            UpdateNotifications.cancel(applicationContext)
            return Result.success()
        }
        val (release, hasUpdate) = Updater.checkForUpdate(forceRefresh = true).getOrElse {
            if (it is kotlinx.coroutines.CancellationException) throw it
            return Result.retry()
        }
        if (hasUpdate && release != null && release.tagName != preferences[DismissedStandaloneUpdateKey]) {
            UpdateNotifications.show(applicationContext, release)
        } else {
            UpdateNotifications.cancel(applicationContext)
        }
        return Result.success()
    }
}
