package fr.makoff.music.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** ZIP sizes are deliberately untrusted, including when the data compresses well. */
class BackupArchiveLimitsTest {
    @Test fun copyOfValidEntryIsExact() {
        val source = ByteArray(1_024) { (it % 251).toByte() }
        val out = ByteArrayOutputStream()
        assertEquals(source.size.toLong(), BackupArchive.copyEntryBounded(
            ByteArrayInputStream(source), out, 1_024,
        ))
        assertArrayEquals(source, out.toByteArray())
    }

    @Test fun oversizedEntryIsRejectedBeforeExceedingDiskQuota() {
        val source = ByteArray(2_048) { 7 }
        val out = ByteArrayOutputStream()
        assertTrue(runCatching {
            BackupArchive.copyEntryBounded(ByteArrayInputStream(source), out, 1_024)
        }.isFailure)
        assertTrue(out.size() <= 1_024)
    }

    @Test fun highlyCompressedZipBombCannotExpandBeyondLimit() {
        val bytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("settings.preferences_pb"))
                zip.write(ByteArray(256 * 1_024))
                zip.closeEntry()
            }
        }.toByteArray()
        assertTrue("Test data should be very compressible", bytes.size < 2_048)
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            assertEquals("settings.preferences_pb", zip.nextEntry.name)
            assertTrue(runCatching {
                BackupArchive.copyEntryBounded(zip, ByteArrayOutputStream(), 4_096)
            }.isFailure)
        }
    }

    @Test fun differentEntryTypesGetDifferentUpperBounds() {
        assertEquals(BackupArchive.MAX_SETTINGS_BYTES, BackupArchive.maxEntryBytes(BackupArchive.SETTINGS))
        assertEquals(1L * 1_024 * 1_024, BackupArchive.maxEntryBytes("unknown.verylarge"))
        assertTrue(BackupArchive.maxEntryBytes("unknown") < BackupArchive.maxEntryBytes("settings.preferences_pb"))
    }
}
