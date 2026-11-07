package fr.makoff.music.ui.screens.settings

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import fr.makoff.music.R

/**
 * Optional UI-task restart after large configuration changes. Playback
 * service and saved queue are not force-stopped or cleared.
 */
@Composable
internal fun RestartAppRecommendationDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val context = LocalContext.current
    val activity = LocalActivity.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restart_app_recommended_title)) },
        text = { Text(stringResource(R.string.restart_app_recommended_message)) },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.restart_app_later))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    context.startActivity(launch)
                    activity?.finishAffinity()
                } else {
                    activity?.recreate()
                }
            }) {
                Text(stringResource(R.string.restart_app_now))
            }
        },
    )
}
