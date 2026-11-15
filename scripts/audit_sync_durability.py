#!/usr/bin/env python3
"""Guard the account, backup and signing invariants that protect Music data.

These checks complement Kotlin unit tests and Android real-device smoke tests.
"""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
source = root / "app/src/main/kotlin/fr/makoff/music"
sync = (source / "utils/SyncUtils.kt").read_text()
backup = (source / "utils/BackupArchive.kt").read_text()
viewmodel = (source / "viewmodels/BackupRestoreViewModel.kt").read_text()
service = (source / "playback/MusicService.kt").read_text()
soundproof = (source / "playback/SoundProofRepository.kt").read_text()
release = (root / ".github/workflows/music-release.yml").read_text()

# A snapshot from a different account must never be interpreted as proof of
# remote deletion for the current account. Cookie text cannot be a filename.
assert "accountScopedSyncSnapshotFileName(" in sync
assert 'MessageDigest.getInstance("SHA-256")' in sync
assert "YouTube.authUser.toString(), YouTube.cookie.orEmpty()" in sync
assert "context.filesDir.resolve(filename)" in sync
assert "context.filesDir.resolve(name)" not in sync
assert "StandardCopyOption.REPLACE_EXISTING" in sync

# Do not make an unsuccessful network pass suppress future automatic syncs.
auto = sync.split("fun tryAutoSync() {", 1)[1].split("fun likeSong(", 1)[0]
full = sync.split("private suspend fun executeFullSync()", 1)[1].split(
    "private suspend fun executeLikeCollection(", 1,
)[0]
assert "scopedSyncEpochs.getLong(accountKey, 0L)" in auto
assert "enqueue(SyncOperation.FullSync)" in auto
assert "settings[LastFullSyncKey]" not in auto
assert "fullSyncHadFailure" in full
assert "currentSyncAccountKey() != requestedAccount" in full
assert "scopedSyncEpochs.edit().putLong(requestedAccount, completedAt).commit()" in full
assert "executeSyncAutoSyncPlaylists()" not in full, "Full sync must not redundantly fetch auto-sync playlists"
assert "SyncOperation.AutoSyncPlaylists," in sync and "is SyncOperation.SinglePlaylist -> false" in sync, "Explicit playlist requests must remain independently queued"

# A ZIP can lie about its size or contain a compression bomb. Bound *actual*
# bytes inflated into staging AND the preview's in-memory settings.
assert "MAX_UNCOMPRESSED_BACKUP_BYTES" in backup
assert "copyEntryBounded(zip, destination, allowance)" in backup
assert "copyEntryBounded(zip, null, allowance)" in backup
assert "require(allowance > 0L" in backup
assert "BackupArchive.copyEntryBounded(" in viewmodel
assert "zip.readBytes()" not in viewmodel

# SoundProof is opt-in, fetches independently and checks artist credits locally.
assert "SoundProofSkipAiKey" in service
assert "soundProofRepository.shouldSkipArtist(artists)" in service
assert 'DUMP_URL = "https://raw.githubusercontent.com/NuclearBlox/SoundProof-Dumps/' in soundproof
assert "SoundProofPolicy.isAnyArtistReportedAi(credits, known)" in soundproof

# Signed releases must use the existing private GitHub Actions key, never the
# generated debug key. No private keystore may be checked into Git.
for secret in ("MUSIC_KEYSTORE_BASE64", "MUSIC_STORE_PASSWORD",
               "MUSIC_KEY_ALIAS", "MUSIC_KEY_PASSWORD"):
    assert "secrets." + secret in release
assert 'apksigner" verify --verbose --print-certs' in release
tracked = subprocess.check_output(["git", "ls-files"], cwd=root, text=True).splitlines()
assert not any(p.endswith((".keystore", ".p12", ".jks", ".pem", ".key"))
               and "test" not in p.lower() for p in tracked), "Private signing material tracked"
print("PASS: scoped sync, bounded backup imports, opt-in SoundProof, and signing provenance")
