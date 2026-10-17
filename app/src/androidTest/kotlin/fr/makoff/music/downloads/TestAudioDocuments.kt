package fr.makoff.music.downloads

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import java.io.File

/** Minimal SAF protocol fixture; only installed with the instrumentation test APK. */
class TestAudioDocuments : ContentProvider() {
    private val root: File get() = File(requireNotNull(context).filesDir, "audio-documents").apply { mkdirs() }
    private fun file(uri: Uri): File {
        val id = DocumentsContract.getDocumentId(uri)
        require(id == File(id).name)
        return if (id == "root") root else File(root, id)
    }
    override fun onCreate() = true
    override fun getType(uri: Uri) = if (file(uri).isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else "audio/wav"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val columns = projection ?: arrayOf("document_id", "_display_name", "mime_type", "flags", "_size")
        val cursor = MatrixCursor(columns)
        val files = if (uri.lastPathSegment == "children") root.listFiles().orEmpty().toList() else listOf(file(uri))
        files.forEach { f -> cursor.addRow(columns.map { column -> when (column) {
            "document_id" -> if (f == root) "root" else f.name
            "_display_name" -> f.name
            "mime_type" -> if (f.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else "audio/wav"
            "flags" -> DocumentsContract.Document.FLAG_SUPPORTS_WRITE or DocumentsContract.Document.FLAG_SUPPORTS_DELETE or DocumentsContract.Document.FLAG_SUPPORTS_RENAME or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
            "_size" -> f.length()
            else -> null
        } }.toTypedArray<Any?>()) }
        return cursor
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.parseMode(mode))
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val tree = DocumentsContract.buildTreeDocumentUri(requireNotNull(context).packageName + ".foldertest", "root")
        val input = requireNotNull(extras)
        @Suppress("DEPRECATION") val uri = requireNotNull(input.getParcelable<Uri>("uri"))
        val parent = file(uri)
        val result = Bundle()
        when (method) {
            "android:createDocument" -> {
                val name = requireNotNull(input.getString("_display_name"))
                require(name == File(name).name)
                check(File(root, name).createNewFile())
                result.putParcelable("uri", DocumentsContract.buildDocumentUriUsingTree(tree, name))
            }
            "android:renameDocument" -> {
                val name = requireNotNull(input.getString("_display_name"))
                require(name == File(name).name)
                if (name.startsWith("reject")) throw UnsupportedOperationException("Rename refused by test provider")
                check(!File(root, name).exists())
                check(parent.renameTo(File(root, name)))
                result.putParcelable("uri", DocumentsContract.buildDocumentUriUsingTree(tree, name))
            }
            "android:deleteDocument" -> check(parent.delete())
            else -> throw UnsupportedOperationException(method)
        }
        return result
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}

/** Grant from the provider owner's UID, as the system document picker would. */
class GrantAudioTreeActivity : android.app.Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val tree = DocumentsContract.buildTreeDocumentUri(packageName + ".foldertest", "root")
        grantUriPermission(packageName.removeSuffix(".test"), tree,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        finish()
    }
}
