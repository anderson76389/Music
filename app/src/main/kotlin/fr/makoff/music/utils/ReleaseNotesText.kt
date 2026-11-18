package fr.makoff.music.utils

/** User-visible differences for ONE release only. Never repeat the generic
 * GitHub asset list or changes inherited from older versions in the popup.
 */
internal data class ReleaseChangeGroup(
    val title: String,
    val items: List<String>,
)

internal fun currentReleaseChanges(body: String): List<ReleaseChangeGroup> {
    val header = "## Changements de cette version"
    val start = body.indexOf(header)
    if (start < 0) return emptyList()
    val section = body.substring(start + header.length)
        .substringBefore("\n## ")
    val groups = mutableListOf<ReleaseChangeGroup>()
    var category = ""
    var entries = mutableListOf<String>()
    fun flush() {
        if (entries.isNotEmpty()) {
            groups.add(ReleaseChangeGroup(category.ifBlank { "Améliorations" }, entries.toList()))
            entries = mutableListOf()
        }
    }
    section.lineSequence().forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.startsWith("### ") -> {
                flush()
                category = trimmed.removePrefix("### ").trim()
            }
            trimmed.startsWith("- ") -> {
                val item = trimmed.removePrefix("- ").trim()
                if (item.isNotBlank()) entries.add(item.take(220))
            }
        }
    }
    flush()
    return groups
}
