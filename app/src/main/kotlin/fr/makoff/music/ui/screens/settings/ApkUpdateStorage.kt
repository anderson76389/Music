package fr.makoff.music.ui.screens.settings

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import androidx.core.content.pm.PackageInfoCompat
import fr.makoff.music.BuildConfig
import java.io.File

/**
 * Music's in-app update always uses ONE staged installer. Android replaces the
 * installed package, but its DownloadManager record and staged APK are not
 * automatically removed after package installation.
 *
 * Only an installer for THIS package at an already installed version may be
 * deleted. A newer update waiting to be installed must be left untouched.
 * This never touches offline songs, Media3 caches or other user downloads.
 */
internal object ApkUpdateStorage {
    private const val PREFS = "apk-update"
    private const val STAGED_APK = "updates/Apple Music.apk"

    fun apkFile(context: Context): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), STAGED_APK)

    fun isObsoleteInstaller(
        installedPackage: String,
        installedVersion: Long,
        archivePackage: String?,
        archiveVersion: Long?,
    ): Boolean =
        archivePackage == installedPackage && archiveVersion != null &&
            archiveVersion > 0 && archiveVersion <= installedVersion

    @Suppress("DEPRECATION")
    fun cleanupIfInstalled(context: Context) {
        val apk = apkFile(context)
        if (!apk.isFile) return
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0) ?: return
        if (!isObsoleteInstaller(
                installedPackage = context.packageName,
                installedVersion = BuildConfig.VERSION_CODE.toLong(),
                archivePackage = archive.packageName,
                archiveVersion = PackageInfoCompat.getLongVersionCode(archive),
            )
        ) return

        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val downloadId = preferences.getLong("id", -1L)
        // Removing the completed DownloadManager row can also delete its file.
        if (downloadId >= 0L) {
            runCatching {
                context.getSystemService(DownloadManager::class.java)?.remove(downloadId)
            }
        }
        if (apk.exists() && !apk.delete()) return
        preferences.edit().remove("id").remove("url").apply()
    }
}
