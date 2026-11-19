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

/** Output format, independent of streaming quality. FLAC cannot restore a lossy source. */
enum class DownloadAudioFormat(val label: Int, val selector: String) {
    BEST(R.string.download_format_best, "bestaudio"),
    M4A(R.string.download_format_m4a, "bestaudio[ext=m4a]"),
    AAC(R.string.download_format_aac, "bestaudio[ext=m4a]"),
    OPUS(R.string.download_format_opus, "bestaudio[acodec=opus]"),
    FLAC(R.string.download_format_flac, "bestaudio"),
    ;

    companion object {
        // M4A stays the default. Obsolete BEST/AAC/FLAC settings migrate to M4A.
        // Keep their enum members for previously queued WorkManager jobs.
        fun parse(value: String?): DownloadAudioFormat = when (value) {
            OPUS.name -> OPUS
            else -> M4A
        }
        val portableChoices: List<DownloadAudioFormat> = listOf(M4A, OPUS)
    }
}

/** Preserve the best original stream of the requested codec (no transcoding).
 * A failed Opus match must fail explicitly, never silently export Vorbis/AAC.
 * The legacy low-quality flag never degrades the chosen original stream.
 */
internal fun selectYtDlpAudioFormat(format: DownloadAudioFormat, lowQuality: Boolean, isLocalSource: Boolean): String =
    if (isLocalSource) "best" else if (format == DownloadAudioFormat.OPUS) "bestaudio[acodec=opus]" else "bestaudio[ext=m4a]"


/** Android's FLAC MediaExtractor may expose decoded PCM; preserve the source codec. */
internal fun sourceAudioMime(input: java.io.InputStream, trackMime: String): String {
    val header = ByteArray(4)
    java.io.DataInputStream(input).readFully(header)
    return if (header.toString(Charsets.US_ASCII) == "fLaC") "audio/flac" else trackMime
}

/** Old BEST/FLAC/Opus preferences are migrated at read time without data loss. */
internal fun Preferences.selectedDownloadFormat(): DownloadAudioFormat =
    DownloadAudioFormat.parse(
        this[UnifiedDownloadFormatKey]
            ?: this[ExternalDownloadFormatKey]
            ?: this[InternalDownloadFormatKey],
    )

internal fun Context.selectedDownloadFormat(): DownloadAudioFormat = runBlocking(Dispatchers.IO) {
    dataStore.data.first().selectedDownloadFormat()
}
