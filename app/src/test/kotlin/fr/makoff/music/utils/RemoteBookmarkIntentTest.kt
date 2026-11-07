package fr.makoff.music.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteBookmarkIntentTest {
    @Test fun remoteListCannotRemoveJustLikedAlbumOrPlaylist() {
        assertFalse(remoteBookmarkMatchesIntent(true, false))
    }

    @Test fun remoteListCannotReAddRecentlyRemovedCollection() {
        assertFalse(remoteBookmarkMatchesIntent(false, true))
    }

    @Test fun serverConfirmationCanClearPendingIntent() {
        assertTrue(remoteBookmarkMatchesIntent(true, true))
        assertTrue(remoteBookmarkMatchesIntent(false, false))
    }

    @Test fun confirmedRemoteUpdatesStillWorkWithoutPendingIntent() {
        assertTrue(remoteBookmarkMatchesIntent(null, false))
        assertTrue(remoteBookmarkMatchesIntent(null, true))
    }
}
