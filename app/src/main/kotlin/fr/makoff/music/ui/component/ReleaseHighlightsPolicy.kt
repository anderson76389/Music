package fr.makoff.music.ui.component

/** The update popup must never display old APK install instructions or
 * cumulative changelogs as though they were changes in the new release.
 *
 * Release workflow publishes a dedicated per-version section. On historical
 * releases without the marker, ignore installation/architecture bullet points.
 */
internal object ReleaseHighlightsPolicy {
    private const val HEADER = "## Changements de cette version"
    fun items(description: String): List<String> {
        val specific = description.substringAfter(HEADER, description)
            .substringBefore("\n## Installer Music")
            .substringBefore("\n## Installation")
        val hasSection = description.contains(HEADER)
        return specific.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("- ") || it.startsWith("• ") }
            .map { it.substring(2).replace("**", "").trim() }
            .filter { it.isNotBlank() }
            .filter { hasSection || (!it.contains(".apk", ignoreCase = true) &&
                !it.contains("SHA256", ignoreCase = true) &&
                !it.contains("signature", ignoreCase = true)) }
            .distinct()
            .take(9)
            .toList()
    }
}
