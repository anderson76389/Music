package fr.makoff.music.utils

import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import fr.makoff.music.MainActivity
import fr.makoff.music.R
import fr.makoff.music.playback.MusicService
import kotlinx.coroutines.*

/** A separate process can replace Room and DataStore after all main-process owners have closed. */
class RestoreApplyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(ProgressBar(this@RestoreApplyActivity))
            addView(TextView(this@RestoreApplyActivity).apply { setText(R.string.restore) })
        })
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val stage = BackupArchive.checkedStage(this@RestoreApplyActivity,
                        requireNotNull(intent.getStringExtra("stage")))
                    try {
                        val pid = intent.getIntExtra("mainPid", -1)
                        require(pid > 0 && pid != Process.myPid())
                        val main = getSystemService(ActivityManager::class.java).runningAppProcesses
                            ?.firstOrNull { it.pid == pid }
                        if (main != null) {
                            require(main.uid == Process.myUid() && main.processName == packageName)
                            stopService(Intent(this@RestoreApplyActivity, MusicService::class.java))
                            Process.killProcess(pid)
                            repeat(100) {
                                if (!java.io.File("/proc/$pid").exists()) return@repeat
                                delay(50)
                            }
                            check(!java.io.File("/proc/$pid").exists()) { "Main process did not stop" }
                        } else {
                            check(!java.io.File("/proc/$pid").exists()) { "Cannot identify main process" }
                        }
                        BackupArchive.apply(this@RestoreApplyActivity, stage)
                        listOf(MusicService.PERSISTENT_QUEUE_FILE, MusicService.PERSISTENT_AUTOMIX_FILE,
                            MusicService.PERSISTENT_PLAYER_STATE_FILE).forEach { filesDir.resolve(it).delete() }
                        android.util.Log.i("RestoreApply", "RESTORE_APPLY_OK")
                    } finally {
                        stage.deleteRecursively()
                    }
                }
            }
            if (result.isFailure) Toast.makeText(this@RestoreApplyActivity, R.string.restore_failed,
                Toast.LENGTH_LONG).show()
            startActivity(Intent(this@RestoreApplyActivity, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }
    }
}
