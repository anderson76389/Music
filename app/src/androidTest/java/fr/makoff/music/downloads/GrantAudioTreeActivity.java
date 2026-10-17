package fr.makoff.music.downloads;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;

/** Runs as the provider owner, mirroring the system picker's URI grant. */
public final class GrantAudioTreeActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String owner = getPackageName();
        String target = owner.endsWith(".test") ? owner.substring(0, owner.length() - 5) : owner;
        Uri tree = DocumentsContract.buildTreeDocumentUri(owner + ".foldertest", "root");
        grantUriPermission(target, tree, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        finish();
    }
}
