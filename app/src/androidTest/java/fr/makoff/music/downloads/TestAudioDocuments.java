package fr.makoff.music.downloads;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Standalone test-APK provider: depends only on framework classes, not the target APK. */
public final class TestAudioDocuments extends ContentProvider {
    private File root() {
        File root = new File(getContext().getFilesDir(), "audio-documents");
        if (!root.isDirectory() && !root.mkdirs()) throw new IllegalStateException("Cannot create test tree");
        return root;
    }
    private String validName(String name) {
        if (name == null || name.equals(".") || name.equals("..") || !new File(name).getName().equals(name))
            throw new IllegalArgumentException("Invalid document name");
        return name;
    }
    private File file(Uri uri) {
        String id = DocumentsContract.getDocumentId(uri);
        if (id.equals("root")) return root();
        for (String part : id.split("/")) validName(part);
        return new File(root(), id);
    }
    private String documentId(File file) {
        return file.equals(root()) ? "root" : root().toPath().relativize(file.toPath()).toString();
    }
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) {
        return file(uri).isDirectory() ? DocumentsContract.Document.MIME_TYPE_DIR : "audio/wav";
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        File requested = file(uri);
        if (!requested.exists() && requested.getName().startsWith("query-null")) return null;
        if (!requested.exists() && requested.getName().startsWith("query-error")) throw new IllegalArgumentException("Document missing");
        if (requested.getName().startsWith("query-denied")) throw new SecurityException("Access denied");
        String[] columns = projection != null ? projection : new String[]{"document_id", "_display_name", "mime_type", "flags", "_size"};
        MatrixCursor cursor = new MatrixCursor(columns);
        File[] files = "children".equals(uri.getLastPathSegment()) ? file(uri).listFiles() : new File[]{file(uri)};
        if (files == null) files = new File[0];
        for (File f : files) {
            if (!f.exists() && !f.getName().startsWith("query-stale")) continue;
            Object[] values = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                switch (columns[i]) {
                    case "document_id": values[i] = documentId(f); break;
                    case "_display_name": values[i] = f.getName(); break;
                    case "mime_type": values[i] = f.isDirectory() ? DocumentsContract.Document.MIME_TYPE_DIR : "audio/wav"; break;
                    case "flags": values[i] = DocumentsContract.Document.FLAG_SUPPORTS_WRITE | DocumentsContract.Document.FLAG_SUPPORTS_DELETE | DocumentsContract.Document.FLAG_SUPPORTS_RENAME | DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE; break;
                    case "_size": values[i] = f.length(); break;
                }
            }
            cursor.addRow(values);
        }
        return cursor;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.parseMode(mode));
    }
    @Override public Bundle call(String method, String arg, Bundle input) {
        Uri uri = input.getParcelable("uri");
        File source = file(uri);
        Uri tree = DocumentsContract.buildTreeDocumentUri(getContext().getPackageName() + ".foldertest", "root");
        Bundle result = new Bundle();
        switch (method) {
            case "android:createDocument": {
                String name = validName(input.getString("_display_name"));
                try {
                    File target = new File(source, name);
                    boolean created = DocumentsContract.Document.MIME_TYPE_DIR.equals(input.getString("mime_type")) ? target.mkdir() : target.createNewFile();
                    if (!created) throw new IllegalStateException("Document exists");
                } catch (IOException error) { throw new IllegalStateException(error); }
                result.putParcelable("uri", DocumentsContract.buildDocumentUriUsingTree(tree, documentId(new File(source, name))));
                break;
            }
            case "android:renameDocument": {
                String name = validName(input.getString("_display_name"));
                if (name.startsWith("reject")) throw new UnsupportedOperationException("Test provider refuses rename");
                File target = new File(source.getParentFile(), name);
                if (source.equals(root()) || target.exists() || !source.renameTo(target)) throw new IllegalStateException("Cannot rename document");
                result.putParcelable("uri", DocumentsContract.buildDocumentUriUsingTree(tree, documentId(target)));
                break;
            }
            case "android:deleteDocument":
                if (source.equals(root()) || !source.delete()) throw new IllegalStateException("Cannot delete document");
                break;
            default: throw new UnsupportedOperationException(method);
        }
        return result;
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
