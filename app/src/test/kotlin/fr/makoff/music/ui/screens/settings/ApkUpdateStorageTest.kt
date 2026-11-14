package fr.makoff.music.ui.screens.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkUpdateStorageTest {
    @Test fun completedInstallIsObsolete() {
        assertTrue(ApkUpdateStorage.isObsoleteInstaller("fr.makoff.music", 154L, "fr.makoff.music", 154L))
        assertTrue(ApkUpdateStorage.isObsoleteInstaller("fr.makoff.music", 154L, "fr.makoff.music", 153L))
    }

    @Test fun pendingNewerInstallAndForeignApksArePreserved() {
        assertFalse(ApkUpdateStorage.isObsoleteInstaller("fr.makoff.music", 154L, "fr.makoff.music", 155L))
        assertFalse(ApkUpdateStorage.isObsoleteInstaller("fr.makoff.music", 154L, "com.other.app", 153L))
        assertFalse(ApkUpdateStorage.isObsoleteInstaller("fr.makoff.music", 154L, null, null))
        assertFalse(ApkUpdateStorage.isObsoleteInstaller("fr.makoff.music", 154L, "fr.makoff.music", 0L))
    }
}
