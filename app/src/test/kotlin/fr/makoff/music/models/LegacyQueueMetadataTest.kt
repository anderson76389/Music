package fr.makoff.music.models

import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyQueueMetadataTest {
    @Test
    fun signedV46MetadataRemainsReadableAfterLibraryPackageRename() {
        // Fixture follows the field descriptors and serial UIDs extracted from the signed v46 APK.
        val encoded = requireNotNull(javaClass.getResourceAsStream("/legacy-media-metadata-v46.b64"))
            .bufferedReader().use { it.readText().trim() }
        val metadata = ObjectInputStream(ByteArrayInputStream(Base64.getDecoder().decode(encoded)))
            .use { it.readObject() as MediaMetadata }
        assertEquals("legacy-v46", metadata.id)
        assertEquals("Reprise conservée", metadata.title)
        assertEquals(193, metadata.duration)
        assertEquals("Artiste", metadata.artists.single().name)
        assertEquals("Album", metadata.album?.title)
    }
}
