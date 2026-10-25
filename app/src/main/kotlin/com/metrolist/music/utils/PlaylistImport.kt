package fr.makoff.music.utils

import java.net.URI
import java.net.URLDecoder

internal object PlaylistImport {
    data class Track(val id: String, val title: String, val artists: List<String>, val duration: Int = -1)

    fun youtubeId(value: String): String? {
        val text = value.trim()
        if (text.matches(Regex("[A-Za-z0-9_-]{11}"))) return text
        return runCatching {
            val uri = URI(text)
            val host = uri.host?.lowercase() ?: return null
            val candidate = when {
                host == "youtu.be" -> uri.path.trim('/').substringBefore('/')
                host == "youtube.com" || host.endsWith(".youtube.com") -> {
                    if (uri.path == "/watch") uri.rawQuery.orEmpty().split('&').firstOrNull {
                        it.substringBefore('=') == "v"
                    }?.substringAfter('=')?.let { URLDecoder.decode(it, "UTF-8") }
                    else uri.path.split('/').filter(String::isNotBlank).let {
                        if (it.firstOrNull() in listOf("shorts", "embed", "live")) it.getOrNull(1) else null
                    }
                }
                else -> null
            }
            candidate?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
        }.getOrNull()
    }

    fun csv(text: String): List<List<String>> {
        val clean = text.removePrefix("\uFEFF")
        val first = clean.lineSequence().firstOrNull().orEmpty()
        fun count(delimiter: Char): Int {
            var quoted = false
            return first.count { c ->
                if (c == '"') quoted = !quoted
                c == delimiter && !quoted
            }
        }
        val delimiter = listOf(',', ';', '\t').maxBy(::count)
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        fun cell() { row += field.toString().trim(); field.clear() }
        fun record() {
            cell()
            if (row.any(String::isNotBlank)) rows += row.toList()
            row.clear()
        }
        while (index < clean.length) {
            val c = clean[index]
            when {
                c == '"' && quoted && clean.getOrNull(index + 1) == '"' -> { field.append('"'); index++ }
                c == '"' -> quoted = !quoted
                c == delimiter && !quoted -> cell()
                (c == '\r' || c == '\n') && !quoted -> {
                    record()
                    if (c == '\r' && clean.getOrNull(index + 1) == '\n') index++
                }
                else -> field.append(c)
            }
            index++
        }
        require(!quoted) { "Unclosed CSV quote" }
        if (field.isNotEmpty() || row.isNotEmpty()) record()
        return rows
    }

    fun headerIndex(row: List<String>, kind: String): Int {
        val names = when (kind) {
            "artist" -> setOf("artist", "artists", "artist name", "artist names", "artiste", "artistes")
            "title" -> setOf("title", "track", "track name", "track title", "song", "name", "titre")
            else -> setOf("url", "uri", "link", "lien", "video id", "videoid", "youtube url", "track url")
        }
        return row.indexOfFirst { it.trim().lowercase() in names }
    }

    fun m3u(text: String): List<Track> {
        val result = mutableListOf<Track>()
        var pending: Track? = null
        text.removePrefix("\uFEFF").lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF:", true) -> {
                    pending?.let(result::add)
                    val label = line.substringAfter(',', "").trim()
                    val artist = label.substringBefore(" - ", "").trim()
                    val title = if (artist.isNotEmpty()) label.substringAfter(" - ").trim() else label
                    pending = Track("", title, artist.split(';').filter(String::isNotBlank),
                        line.substringAfter(':').substringBefore(',').toIntOrNull() ?: -1)
                }
                line.isNotBlank() && !line.startsWith('#') -> {
                    val id = youtubeId(line).orEmpty()
                    val item = pending ?: id.takeIf(String::isNotEmpty)?.let { Track(it, it, emptyList()) }
                    item?.let { result += it.copy(id = id) }
                    pending = null
                }
            }
        }
        pending?.let(result::add)
        return result.filter { it.title.isNotBlank() || it.id.isNotBlank() }
    }
}
