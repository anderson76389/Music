package fr.makoff.music.utils

/**
 * Only present changes specific to the published APK. The full GitHub
 * release body also lists architectures, signing, download instructions and
 * a source SHA; those are NOT new application features and should never be
 * presented as release highlights in the update pop-up.
 */
internal object ReleaseHighlights {
    private const val HEADER = "## Nouveautés de cette version"

    fun forPopup(releaseBody: String): List<String> {
        val body = releaseBody.substringAfter(HEADER, missingDelimiterValue = "")
        if (body.isBlank()) return emptyList()
        return body.lineSequence()
            .dropWhile(String::isBlank)
            .takeWhile { !it.trimStart().startsWith("## ") }
            .map(String::trim)
            .filter { it.startsWith("- ") || it.startsWith("* ") }
            .map { line ->
                line.drop(2).trim()
                    .replace("**", "")
                    .replace("`", "")
            }
            .filter(String::isNotBlank)
            .take(8)
            .toList()
    }
}
