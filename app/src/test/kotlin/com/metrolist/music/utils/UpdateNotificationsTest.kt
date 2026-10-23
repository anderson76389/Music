package fr.makoff.music.utils

import android.app.Application
import android.app.NotificationManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import fr.makoff.music.ui.screens.settings.ApkUpdateActivity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class UpdateNotificationsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun release(tag: String, url: String) = ReleaseInfo(tag, tag.removePrefix("v"), "", "",
        listOf(ReleaseAsset("Metrolist.apk", url, 100, "universal", "gms")))

    @Test fun notificationOpensLatestApkInIntegratedInstaller() {
        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
        val oldUrl = "https://github.com/anderson76389/Music/releases/download/v13.7.0.13/Metrolist.apk"
        val newUrl = "https://github.com/anderson76389/Music/releases/download/v13.7.0.14/Metrolist.apk"
        UpdateNotifications.show(context, release("v13.7.0.13", oldUrl))
        UpdateNotifications.show(context, release("v13.7.0.14", newUrl))
        val intent = shadowOf(manager.getNotification(1001).contentIntent).savedIntent
        assertEquals(ApkUpdateActivity::class.java.name, intent.component?.className)
        assertEquals(newUrl, intent.getStringExtra("apk-url"))
        assertEquals(1, manager.allNotifications.size)
        UpdateNotifications.cancel(context)
        assertNull(manager.getNotification(1001))
    }

    @Test fun foreignApkCannotCreateUpdateNotification() {
        UpdateNotifications.cancel(context)
        UpdateNotifications.show(context, release("v99", "https://example.com/Metrolist.apk"))
        assertNull(shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(1001))
    }
}
