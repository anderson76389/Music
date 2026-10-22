package fr.makoff.music.downloads

import fr.makoff.music.R

/** Output format, independent of streaming quality. FLAC cannot restore a lossy source. */
enum class DownloadAudioFormat(val label: Int, val selector: String) {
    BEST(R.string.download_format_best, "bestaudio"),
    M4A(R.string.download_format_m4a, "bestaudio[ext=m4a]"),
    FLAC(R.string.download_format_flac, "bestaudio"),
    ;

    companion object {
        fun parse(value: String?): DownloadAudioFormat = entries.firstOrNull { it.name == value } ?: BEST
    }
}

/** Android's FLAC MediaExtractor may expose decoded PCM; preserve the source codec. */
internal fun sourceAudioMime(input: java.io.InputStream, trackMime: String): String {
    val header = ByteArray(4)
    java.io.DataInputStream(input).readFully(header)
    return if (header.toString(Charsets.US_ASCII) == "fLaC") "audio/flac" else trackMime
}
