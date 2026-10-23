package fr.makoff.music.ui.screens.settings

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import fr.makoff.music.R
import fr.makoff.music.ui.theme.MetrolistTheme
import fr.makoff.music.utils.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ApkUpdateActivity : ComponentActivity() {
    private val downloads by lazy { requireNotNull(getSystemService(DownloadManager::class.java)) }
    private val preferences by lazy { getSharedPreferences("apk-update", MODE_PRIVATE) }
    private val apk by lazy { File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates/Metrolist.apk") }
    private var message by mutableStateOf("")
    private var ready by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var fraction by mutableFloatStateOf(0f)
    private var poll: Job? = null
    private var awaitingPermission = false
    private var offerInstall = true
    private val installer = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        message = getString(R.string.apk_update_install_result)
    }
    private val permission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        awaitingPermission = false
        if (packageManager.canRequestPackageInstalls()) installApk()
        else message = getString(R.string.apk_update_permission_needed)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        awaitingPermission = savedInstanceState?.getBoolean("permission") ?: false
        offerInstall = savedInstanceState?.getBoolean("offerInstall") ?: true
        setContent {
            MetrolistTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(24.dp).windowInsetsPadding(WindowInsets.systemBars), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.updater), style = MaterialTheme.typography.headlineSmall)
                        Text(message)
                        if (busy) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        if (ready) Button(onClick = ::installApk, enabled = !busy) { Text(stringResource(R.string.apk_update_install)) }
                        if (!busy && !ready) Button(onClick = { startDownload(retry = true) }) { Text(stringResource(R.string.apk_update_retry)) }
                        TextButton(onClick = { finish() }) { Text(stringResource(R.string.close)) }
                    }
                }
            }
        }
        startDownload(retry = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("permission", awaitingPermission)
        outState.putBoolean("offerInstall", offerInstall)
        super.onSaveInstanceState(outState)
    }

    private fun startDownload(retry: Boolean) {
        poll?.cancel()
        poll = lifecycleScope.launch {
            busy = true
            ready = false
            try {
                val url = requireNotNull(intent.getStringExtra(EXTRA_URL))
                require(Updater.isOwnApkUrl(url)) { getString(R.string.apk_update_invalid_source) }
                var id = preferences.getLong("id", -1)
                val reusable = !retry && preferences.getString("url", null) == url && id >= 0 &&
                    withContext(Dispatchers.IO) { downloads.query(DownloadManager.Query().setFilterById(id)).use { it.moveToFirst() } }
                if (!reusable) {
                    id = withContext(Dispatchers.IO) {
                        if (id >= 0) downloads.remove(id)
                        apk.parentFile?.mkdirs()
                        apk.delete()
                        downloads.enqueue(DownloadManager.Request(Uri.parse(url))
                            .setTitle("Metrolist.apk")
                            .setDescription(getString(R.string.apk_update_downloading))
                            .setMimeType("application/vnd.android.package-archive")
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                            .setDestinationUri(Uri.fromFile(apk)))
                    }
                    preferences.edit().putLong("id", id).putString("url", url).apply()
                }
                message = getString(R.string.apk_update_downloading)
                while (true) {
                    val result = withContext(Dispatchers.IO) {
                        downloads.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                            check(cursor.moveToFirst()) { getString(R.string.apk_update_download_failed) }
                            Triple(cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                                cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                                cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)))
                        }
                    }
                    fraction = if (result.third > 0) (result.second.toFloat() / result.third).coerceIn(0f, 1f) else 0f
                    check(result.first != DownloadManager.STATUS_FAILED) { getString(R.string.apk_update_download_failed) }
                    if (result.first == DownloadManager.STATUS_SUCCESSFUL) break
                    delay(500)
                }
                withContext(Dispatchers.IO) { verifyApk() }
                ready = true
                busy = false
                message = getString(R.string.apk_update_ready)
                if (offerInstall && !awaitingPermission && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    offerInstall = false
                    installApk()
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message = error.message ?: getString(R.string.apk_update_download_failed)
                busy = false
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyApk() {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val candidate = packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error(getString(R.string.apk_update_invalid_apk))
        val installed = packageManager.getPackageInfo(packageName, flags)
        check(candidate.packageName == packageName) { getString(R.string.apk_update_package_mismatch) }
        check(PackageInfoCompat.getLongVersionCode(candidate) > PackageInfoCompat.getLongVersionCode(installed)) {
            getString(R.string.apk_update_not_newer)
        }
        val incoming = if (Build.VERSION.SDK_INT >= 28) candidate.signingInfo?.apkContentsSigners else candidate.signatures
        val current = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo?.apkContentsSigners else installed.signatures
        check(!incoming.isNullOrEmpty() && !current.isNullOrEmpty() && incoming.toSet() == current.toSet()) {
            getString(R.string.apk_update_signature_mismatch)
        }
    }

    private fun installApk() {
        if (!ready || busy) return
        offerInstall = false
        if (!packageManager.canRequestPackageInstalls()) {
            message = getString(R.string.apk_update_permission_needed)
            awaitingPermission = true
            runCatching { permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) }
                .onFailure { awaitingPermission = false; message = getString(R.string.apk_update_install_failed) }
            return
        }
        lifecycleScope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { verifyApk() }
                val uri = FileProvider.getUriForFile(this@ApkUpdateActivity, "$packageName.FileProvider", apk)
                installer.launch(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            } catch (error: Exception) {
                message = error.message ?: getString(R.string.apk_update_install_failed)
            } finally { busy = false }
        }
    }

    companion object {
        private const val EXTRA_URL = "apk-url"
        fun intent(context: Context, url: String): Intent = Intent(context, ApkUpdateActivity::class.java).putExtra(EXTRA_URL, url)
    }
}
