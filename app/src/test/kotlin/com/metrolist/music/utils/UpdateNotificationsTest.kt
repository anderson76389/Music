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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class UpdateNotificationsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun release(tag: String, url: String) = ReleaseInfo(tag, tag.removePrefix("v"), "", "",
        listOf(ReleaseAsset("Metrolist.apk", url, 100, "universal", "gms")))

    @Test fun foreignApkCannotCreateUpdateNotification() {
        UpdateNotifications.cancel(context)
        UpdateNotifications.show(context, release("v99", "https://example.com/Metrolist.apk"))
        assertNull(shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(1001))
    }
}
