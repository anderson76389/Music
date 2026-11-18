package fr.makoff.music.downloads

import fr.makoff.music.R
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import fr.makoff.music.constants.UnifiedDownloadFormatKey
import fr.makoff.music.constants.InternalDownloadFormatKey
import fr.makoff.music.constants.ExternalDownloadFormatKey
import fr.makoff.music.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Best native AAC stream in an M4A container by default. Raw AAC is an
 * optional export for hardware players requiring .aac.
 *
 * BEST/FLAC remain readable for old queued WorkManager jobs, but are mapped
 * to M4A instead of producing another output codec or fake lossless audio.
 */
enum class DownloadAudioFormat(val label: Int, val selector: String) {
    M4A(R.string.download_format_m4a, "bestaudio[ext=m4a]"),
    AAC(R.string.download_format_aac, "bestaudio[ext=m4a]"),
    BEST(R.string.download_format_m4a, "bestaudio[ext=m4a]"),
    FLAC(R.string.download_format_m4a, "bestaudio[ext=m4a]");

    companion object {
        val available = listOf(M4A, AAC)
        fun parse(value: String?): DownloadAudioFormat =
            if (value == AAC.name) AAC else M4A
    }
}

/** Never downgrade exports due to the playback's low-bandwidth setting.
 * Both choices request the highest available native AAC audio stream.
 */
internal fun selectYtDlpAudioFormat(
    format: DownloadAudioFormat, lowQuality: Boolean, isLocalSource: Boolean,
): String = if (isLocalSource) "best" else "bestaudio[ext=m4a]"

/** Android's FLAC MediaExtractor may expose decoded PCM; preserve the source codec. */
internal fun sourceAudioMime(input: java.io.InputStream, trackMime: String): String {
    val header = ByteArray(4)
    java.io.DataInputStream(input).readFully(header)
    return if (header.toString(Charsets.US_ASCII) == "fLaC") "audio/flac" else trackMime
}

internal fun Preferences.selectedDownloadFormat(): DownloadAudioFormat =
    DownloadAudioFormat.parse(
        this[UnifiedDownloadFormatKey]
            ?: this[ExternalDownloadFormatKey]
            ?: this[InternalDownloadFormatKey],
    )

internal fun Context.selectedDownloadFormat(): DownloadAudioFormat = runBlocking(Dispatchers.IO) {
    dataStore.data.first().selectedDownloadFormat()
}
