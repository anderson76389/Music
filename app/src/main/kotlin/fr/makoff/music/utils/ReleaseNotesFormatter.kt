package fr.makoff.music.utils

/**
 * The update dialog displays only the changes in the release being installed,
 * not the APK download instructions, legacy changelog or source hash.
 */
internal object ReleaseNotesFormatter {
    private const val HEADING = "## Modifications de cette version"

    fun changes(releaseBody: String): List<String> {
        val marker = releaseBody.indexOf(HEADING, ignoreCase = true)
        if (marker < 0) return emptyList()
        val lines = releaseBody.substring(marker + HEADING.length).lineSequence()
        return lines.takeWhile { line ->
            val trimmed = line.trim()
            !trimmed.startsWith("## ") && !trimmed.startsWith("Sources :")
        }.map { it.trim() }
            .filter { it.startsWith("- ") || it.startsWith("* ") }
            .map { it.drop(2).replace("**", "").trim() }
            .filter(String::isNotBlank)
            .take(12)
            .toList()
    }
}
