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

    fun apkFile(context: Context): File {
        // A null external-files directory must never resolve to a relative
        // "updates/..." path. DownloadManager requires a real, app-owned path.
        val directory = requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)) {
            "Le stockage temporaire des mises à jour est indisponible"
        }
        return File(directory, STAGED_APK)
    }

    fun isObsoleteInstaller(
        installedPackage: String,
        installedVersion: Long,
        archivePackage: String?,
        archiveVersion: Long?,
    ): Boolean =
        archivePackage == installedPackage && archiveVersion != null &&
            archiveVersion > 0 && archiveVersion <= installedVersion

    /**
     * An update the user abandons or that fails verification should not leave
     * a ~200 MB installer in app storage. The only affected file is the fixed,
     * app-private staging path and its own DownloadManager row; never touch
     * exported media, downloads, offline tracks, or the player cache.
     */
    fun discardPendingInstaller(context: Context) {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val downloadId = preferences.getLong("id", -1L)
        if (downloadId >= 0L) {
            runCatching {
                context.getSystemService(DownloadManager::class.java)?.remove(downloadId)
            }
        }
        val staged = apkFile(context)
        if (staged.isFile && !staged.delete()) return
        preferences.edit().remove("id").remove("url").apply()
    }

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
