package fr.makoff.music.settings

import android.content.Intent
import android.net.Uri
import android.os.Process
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.makoff.music.MainActivity
import fr.makoff.music.db.InternalDatabase
import fr.makoff.music.db.entities.SongEntity
import fr.makoff.music.utils.*
import fr.makoff.music.viewmodels.BackupRestoreViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupRestoreProbe {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val proofKey = stringPreferencesKey("backupRoundTripProbe")

    @Test(timeout = 45000) fun prepareAndRestore() = runBlocking(Dispatchers.IO) {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val database = InternalDatabase.newInstance(context)
        val id = java.util.UUID.randomUUID().toString().replace("-", "").take(11)
        val archive = File(context.cacheDir, "roundtrip.backup")
        database.insert(SongEntity(id, "Original sauvegardé"))
        context.dataStore.edit { it[proofKey] = "snapshot-$id" }
        ArtistNameAliases.set(context, "backup-probe", null, "Original", "Alias sauvegardé")
        BackupArchive.write(context, database, Uri.fromFile(archive))
        assertTrue(archive.length() > 100)
        val preview = BackupRestoreViewModel(database).previewBackup(context, Uri.fromFile(archive))
        assertFalse(preview.hasAuthData)
        database.update(SongEntity(id, "Modification après sauvegarde"))
        context.dataStore.edit { it[proofKey] = "changed" }
        ArtistNameAliases.set(context, "backup-probe", null, "Original", "Alias modifié")
        File(context.noBackupFilesDir, "restore-proof").writeText("$id\n${Process.myPid()}")
        println("RESTORE_PREPARED: actual SQLite archive and changed preferences ready")
        BackupRestoreViewModel(database).restore(context, Uri.fromFile(archive))
        delay(25000)
        fail("Restore must restart the main process")
    }

    @Test(timeout = 45000) fun verifyAfterRestart() = runBlocking(Dispatchers.IO) {
        val marker = File(context.noBackupFilesDir, "restore-proof")
        val parts = marker.readLines()
        val id = parts[0]
        assertNotEquals("Main process must have restarted", parts[1].toInt(), Process.myPid())
        val database = InternalDatabase.newInstance(context)
        try {
            assertEquals("Original sauvegardé", database.song(id).first()?.title)
            assertEquals("snapshot-$id", context.dataStore.data.first()[proofKey])
            assertEquals("Alias sauvegardé", ArtistNameAliases.resolve("backup-probe", "Original"))
            val csv = File(context.cacheDir, "import-probe.csv").apply {
                writeText("Artist,Title,URL\n\"Artiste, A\",\"Titre avec virgule, B\",https://youtu.be/abcdefghijk\n")
            }
            val model = BackupRestoreViewModel(database)
            val mapping = model.previewCsvFile(context, Uri.fromFile(csv))
            assertTrue(mapping.hasHeader)
            assertEquals(2, mapping.urlColumnIndex)
            val songs = model.importPlaylistFromCsv(context, Uri.fromFile(csv), mapping)
            assertEquals(1, songs.size)
            val media = fr.makoff.music.ui.menu.resolveImportedMedia(songs.single())!!
            assertEquals("abcdefghijk", media.id)
            database.insert(media)
            assertEquals("Titre avec virgule, B", database.song(media.id).first()?.title)
            val m3u = File(context.cacheDir, "import-probe.m3u").apply {
                writeText("\uFEFF#EXTM3U\n#EXTINF:90,Artiste - Titre\nhttps://music.youtube.com/watch?v=lmnopqrstuv\n")
            }
            val imported = model.loadM3UOnline(context, Uri.fromFile(m3u))
            assertEquals("lmnopqrstuv", imported.single().id)
            val m3uMedia = fr.makoff.music.ui.menu.resolveImportedMedia(imported.single())!!
            assertEquals("lmnopqrstuv", m3uMedia.id)
            database.insert(m3uMedia)
            val playlistEntity = fr.makoff.music.db.entities.PlaylistEntity(name = "Import CSV et M3U")
            database.insert(playlistEntity)
            val playlist = requireNotNull(database.playlist(playlistEntity.id).first())
            database.addSongsToPlaylist(playlist, listOf(media.id to null, m3uMedia.id to null))
            assertEquals(listOf(media.id, m3uMedia.id),
                database.playlistSongs(playlistEntity.id).first().map { it.song.id })
            database.delete(playlistEntity)
            database.song(m3uMedia.id).first()?.let { database.delete(it.song) }
            csv.delete(); m3u.delete()
            database.song(media.id).first()?.let { database.delete(it.song) }
            val invalid = File(context.cacheDir, "invalid.backup")
            java.util.zip.ZipOutputStream(invalid.outputStream()).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry(InternalDatabase.DB_NAME))
                zip.write("not a database".toByteArray())
                zip.closeEntry()
            }
            assertTrue("Invalid backup must be rejected before changing live data",
                runCatching { BackupArchive.prepare(context, Uri.fromFile(invalid),
                    database.openHelper.writableDatabase.version, false) }.isFailure)
            assertEquals("Original sauvegardé", database.song(id).first()?.title)
            invalid.delete()
            println("BACKUP_RESTORE_OK: original SQLite rows, preferences and aliases restored after process restart")
            println("PLAYLIST_IMPORT_OK: real CSV/M3U files keep exact YouTube IDs without network search")
        } finally {
            database.song(id).first()?.let { database.delete(it.song) }
            context.dataStore.edit { it.remove(proofKey) }
            marker.delete()
            File(context.cacheDir, "roundtrip.backup").delete()
            database.close()
        }
    }
}
