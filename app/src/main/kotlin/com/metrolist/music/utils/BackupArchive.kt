package fr.makoff.music.utils

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import fr.makoff.music.constants.*
import fr.makoff.music.db.InternalDatabase
import fr.makoff.music.db.MusicDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal object BackupArchive {
    const val SETTINGS = "settings.preferences_pb"
    private val entries = setOf(SETTINGS, InternalDatabase.DB_NAME,
        "${InternalDatabase.DB_NAME}-wal", "${InternalDatabase.DB_NAME}-shm", ArtistNameAliases.BACKUP_FILENAME)

    suspend fun readSettings(context: Context, bytes: ByteArray): Preferences =
        preferencesFile(context, bytes) { it.data.first() }

    private suspend fun <T> preferencesFile(
        context: Context,
        bytes: ByteArray?,
        action: suspend (androidx.datastore.core.DataStore<Preferences>) -> T,
    ): T {
        val file = File.createTempFile("backup-preferences-", ".preferences_pb", context.cacheDir)
        if (bytes != null) file.writeBytes(bytes) else file.delete()
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        try {
            return action(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
        } finally {
            job.cancelAndJoin()
            file.delete()
        }
    }

    private suspend fun settingsBytes(context: Context, settings: Preferences): ByteArray {
        val file = File.createTempFile("restored-preferences-", ".preferences_pb", context.cacheDir)
        file.delete()
        val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file })
            store.updateData { settings }
            return file.readBytes()
        } finally {
            job.cancelAndJoin()
            file.delete()
        }
    }

    suspend fun write(context: Context, database: MusicDatabase, uri: Uri) = withContext(Dispatchers.IO) {
        val preferences = settingsBytes(context, context.dataStore.data.first())
        val snapshot = File.createTempFile("music-database-", ".db", context.cacheDir)
        snapshot.delete()
        val live = File(requireNotNull(database.openHelper.writableDatabase.path))
        try {
            val sqliteVersion = database.openHelper.writableDatabase.query("SELECT sqlite_version()").use {
                check(it.moveToFirst())
                it.getString(0).split('.').map { part -> part.toIntOrNull() ?: 0 }
            }
            val consistentSnapshot = sqliteVersion[0] > 3 ||
                (sqliteVersion[0] == 3 && sqliteVersion.getOrElse(1) { 0 } >= 27)
            if (consistentSnapshot) {
                // VACUUM INTO takes a consistent SQLite snapshot while playback may still write.
                database.openHelper.writableDatabase.execSQL(
                    "VACUUM INTO '${snapshot.path.replace("'", "''")}'")
            } else {
                database.checkpoint()
            }
            val raw = requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) {
                "Cannot open backup destination"
            }
            ZipOutputStream(raw.buffered()).use { zip ->
                fun add(name: String, file: File) {
                    zip.putNextEntry(ZipEntry(name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry(SETTINGS))
                zip.write(preferences)
                zip.closeEntry()
                add(InternalDatabase.DB_NAME, if (consistentSnapshot) snapshot else live)
                if (!consistentSnapshot) {
                    File("${live.path}-wal").takeIf(File::exists)?.let { add("${InternalDatabase.DB_NAME}-wal", it) }
                }
                zip.putNextEntry(ZipEntry(ArtistNameAliases.BACKUP_FILENAME))
                zip.write(ArtistNameAliases.serialize().encodeToByteArray())
                zip.closeEntry()
            }
        } finally {
            snapshot.delete()
        }
    }

    suspend fun prepare(context: Context, uri: Uri, currentVersion: Int, clearAuth: Boolean): File =
        withContext(Dispatchers.IO) {
            val root = File(context.noBackupFilesDir, "restore-staging").apply { mkdirs() }
            val stage = File(root, java.util.UUID.randomUUID().toString()).apply { check(mkdirs()) }
            try {
                val seen = mutableSetOf<String>()
                val raw = requireNotNull(context.contentResolver.openInputStream(uri))
                ZipInputStream(raw.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.name in entries) {
                            require(seen.add(entry.name)) { "Duplicate backup entry" }
                            File(stage, entry.name).outputStream().use { zip.copyTo(it) }
                        }
                        zip.closeEntry()
                    }
                }
                require(InternalDatabase.DB_NAME in seen || SETTINGS in seen) { "Empty backup" }
                File(stage, SETTINGS).takeIf(File::exists)?.let { file ->
                    val prefs = readSettings(context, file.readBytes()).toMutablePreferences()
                    if (clearAuth) {
                        prefs.remove(InnerTubeCookieKey); prefs.remove(VisitorDataKey)
                        prefs.remove(DataSyncIdKey); prefs.remove(InnerTubeAuthUserKey)
                        prefs.remove(AccountNameKey); prefs.remove(AccountEmailKey)
                        prefs.remove(AccountChannelHandleKey)
                    }
                    file.writeBytes(settingsBytes(context, prefs))
                }
                File(stage, InternalDatabase.DB_NAME).takeIf(File::exists)?.let { file ->
                    SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                        require(db.version in 1..currentVersion) { "Incompatible backup database" }
                        db.rawQuery("PRAGMA quick_check", null).use {
                            require(it.moveToFirst() && it.getString(0) == "ok") { "Invalid database" }
                        }
                        // Recover older archives that contain committed rows only in their WAL.
                        db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
                    }
                    val validated = InternalDatabase.build(context, file.path,
                        withPragmaCallback = false, allowDestructiveMigration = false)
                    try {
                        validated.openHelper.writableDatabase.query("SELECT 1").close()
                    } finally {
                        validated.close()
                    }
                }
                File(stage, ArtistNameAliases.BACKUP_FILENAME).takeIf(File::exists)?.let {
                    ArtistNameAliases.deserialize(it.readText())
                }
                File(stage, "ready").writeText("1")
                stage
            } catch (error: Throwable) {
                stage.deleteRecursively()
                throw error
            }
        }

    fun checkedStage(context: Context, name: String): File {
        require(name.matches(Regex("[0-9a-f-]{36}")))
        val root = File(context.noBackupFilesDir, "restore-staging").canonicalFile
        val stage = File(root, name).canonicalFile
        require(stage.parentFile == root && File(stage, "ready").isFile)
        return stage
    }

    // Called only after the main process has stopped: Room, DataStore and playback are closed.
    fun apply(context: Context, stage: File) {
        val files = mutableListOf<Pair<File?, File>>()
        File(stage, InternalDatabase.DB_NAME).takeIf(File::exists)?.let {
            val target = context.getDatabasePath(InternalDatabase.DB_NAME)
            files += it to target
            files += null to File("${target.path}-wal")
            files += null to File("${target.path}-shm")
        }
        File(stage, SETTINGS).takeIf(File::exists)?.let {
            files += it to File(context.filesDir, "datastore/$SETTINGS")
        }
        val saved = mutableListOf<Pair<File, File>>()
        val promoted = mutableListOf<File>()
        try {
            files.forEachIndexed { index, (_, target) ->
                target.parentFile?.mkdirs()
                if (target.exists()) {
                    val rollback = File(stage, "rollback-$index")
                    check(target.renameTo(rollback)) { "Cannot preserve existing data" }
                    saved += rollback to target
                }
            }
            files.forEach { (source, target) ->
                if (source != null) {
                    promoted += target
                    check(source.renameTo(target)) { "Cannot promote restored data" }
                }
            }
            val aliases = File(stage, ArtistNameAliases.BACKUP_FILENAME)
            ArtistNameAliases.restore(context,
                if (aliases.exists()) ArtistNameAliases.deserialize(aliases.readText()) else emptyMap())
        } catch (error: Throwable) {
            promoted.forEach(File::delete)
            saved.asReversed().forEach { (backup, target) -> backup.renameTo(target) }
            throw error
        }
    }
}
