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

/** Portable export formats; legacy enum values remain to finish queued jobs
 * created by previous APKs, but cannot be selected for new downloads. */
enum class DownloadAudioFormat(val label: Int, val selector: String) {
    M4A(R.string.download_format_m4a, "bestaudio[ext=m4a]/bestaudio"),
    AAC(R.string.download_format_aac, "bestaudio[ext=m4a]/bestaudio"),
    // Compatibility only for pre-update queued WorkManager jobs.
    BEST(R.string.download_format_best, "bestaudio"),
    FLAC(R.string.download_format_flac, "bestaudio"),
    ;

    companion object {
        val portableChoices: List<DownloadAudioFormat> = listOf(M4A, AAC)
        fun parse(value: String?): DownloadAudioFormat = entries.firstOrNull { it.name == value } ?: M4A
    }
}

/** Exact yt-dlp format expression. BEST always targets the highest audio
 * available to this session; a low-quality request is explicit only.
 * Local fixtures use "best" to exercise the file input path.
 */
internal fun selectYtDlpAudioFormat(format: DownloadAudioFormat, lowQuality: Boolean, isLocalSource: Boolean): String =
    when {
        isLocalSource -> "best"
        lowQuality && format == DownloadAudioFormat.BEST -> "worstaudio"
        else -> format.selector
    }

/** Android's FLAC MediaExtractor may expose decoded PCM; preserve the source codec. */
internal fun sourceAudioMime(input: java.io.InputStream, trackMime: String): String {
    val header = ByteArray(4)
    java.io.DataInputStream(input).readFully(header)
    return if (header.toString(Charsets.US_ASCII) == "fLaC") "audio/flac" else trackMime
}

/** Keep an explicit legacy format instead of silently replacing it with BEST. */
internal fun Preferences.selectedDownloadFormat(): DownloadAudioFormat {
    fun valid(value: String?) = DownloadAudioFormat.portableChoices.firstOrNull { it.name == value }
    // A previously saved BEST/FLAC preference automatically becomes M4A;
    // never silently export Opus after updating to the portable-only picker.
    return valid(this[UnifiedDownloadFormatKey])
        ?: valid(this[ExternalDownloadFormatKey])
        ?: valid(this[InternalDownloadFormatKey])
        ?: DownloadAudioFormat.M4A
}

internal fun Context.selectedDownloadFormat(): DownloadAudioFormat = runBlocking(Dispatchers.IO) {
    dataStore.data.first().selectedDownloadFormat()
}
