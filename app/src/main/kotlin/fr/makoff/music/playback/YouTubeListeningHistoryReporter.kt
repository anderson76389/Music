package fr.makoff.music.playback

import kotlin.math.abs
import kotlin.random.Random

/**
 * An independent YouTube Music listening session. Sending only one "playback"
 * ping after ExoPlayer finishes a track loses pauses, skips and app restarts.
 * This reporter sends the initial play event and incremental watch-time ranges
 * while audio actually plays, retaining ONE cpn throughout a listening session.
 *
 * No networking, preferences or player lifecycle are owned by this class.
 * Only the media service drives it, on its sequential background coroutine.
 */
internal class YouTubeListeningHistoryReporter(
    private val sendPlayback: suspend (url: String, cpn: String, format: Int?) -> Boolean,
    private val sendWatchtime: suspend (
        url: String,
        cpn: String,
        startSeconds: Double,
        endSeconds: Double,
        currentSeconds: Double,
        realSeconds: Double,
        isFinal: Boolean,
        format: Int?,
    ) -> Boolean,
) {
    data class Tracking(
        val playbackUrl: String?,
        val watchtimeUrl: String?,
        val format: Int? = null,
    )

    data class Sample(
        val mediaId: String?,
        val account: String,
        val positionMs: Long,
        val elapsedRealtimeMs: Long,
        val isPlaying: Boolean,
        val canSync: Boolean,
        val tracking: Tracking?,
    )

    private data class Session(
        val mediaId: String,
        val account: String,
        val cpn: String,
        var lastObservedAtMs: Long,
        var lastPositionMs: Long,
        var lastReportedPositionMs: Long,
        var listenedMs: Long = 0L,
        var lastReportedListenedMs: Long = 0L,
        var playbackSent: Boolean = false,
        var lastPlaybackAttemptMs: Long = 0L,
        var lastWatchtimeAttemptMs: Long = 0L,
        var tracking: Tracking? = null,
    )

    private var session: Session? = null

    suspend fun observe(sample: Sample) {
        val id = sample.mediaId
        val eligible = sample.canSync && sample.account.isNotBlank() &&
            id != null && isYouTubeVideoId(id)
        val current = session
        if (!eligible) {
            // An explicit history pause, logout or account switch must never
            // send the previous session to a different account.
            session = null
            return
        }
        if (current != null && (current.mediaId != id || current.account != sample.account)) {
            // The authenticated identity is unchanged on an ordinary track skip.
            if (current.account == sample.account) finish(current)
            session = null
        }
        if (!sample.isPlaying) {
            session?.let { finish(it) }
            session = null
            return
        }

        var s = session
        if (s == null) {
            s = Session(
                mediaId = id!!,
                account = sample.account,
                cpn = createCpn(),
                lastObservedAtMs = sample.elapsedRealtimeMs,
                lastPositionMs = sample.positionMs.coerceAtLeast(0),
                lastReportedPositionMs = sample.positionMs.coerceAtLeast(0),
                tracking = sample.tracking,
            )
            session = s
            return
        }

        // A manual seek should not claim that skipped audio was listened to.
        val elapsedMs = (sample.elapsedRealtimeMs - s.lastObservedAtMs).coerceIn(0L, 7_000L)
        val positionMs = sample.positionMs.coerceAtLeast(0L)
        val deltaMs = positionMs - s.lastPositionMs
        if (abs(deltaMs - elapsedMs) > 12_000L) {
            if (s.playbackSent) reportWatchtime(s, isFinal = false)
            s.lastReportedPositionMs = positionMs
        } else {
            s.listenedMs += elapsedMs
        }
        s.lastObservedAtMs = sample.elapsedRealtimeMs
        s.lastPositionMs = positionMs
        if (sample.tracking != null) s.tracking = sample.tracking

        if (!s.playbackSent && s.listenedMs >= 10_000L &&
            (s.lastPlaybackAttemptMs == 0L ||
                sample.elapsedRealtimeMs - s.lastPlaybackAttemptMs >= 60_000L)
        ) {
            val url = s.tracking?.playbackUrl
            if (url != null) {
                s.lastPlaybackAttemptMs = sample.elapsedRealtimeMs
                s.playbackSent = sendPlayback(url, s.cpn, s.tracking?.format)
            }
        }

        if (s.playbackSent && s.listenedMs - s.lastReportedListenedMs >= 15_000L &&
            (s.lastWatchtimeAttemptMs == 0L ||
                sample.elapsedRealtimeMs - s.lastWatchtimeAttemptMs >= 30_000L)
        ) {
            reportWatchtime(s, isFinal = false)
        }
    }

    private suspend fun finish(s: Session) {
        if (s.listenedMs >= 10_000L && !s.playbackSent) {
            val url = s.tracking?.playbackUrl
            if (url != null) {
                s.playbackSent = sendPlayback(url, s.cpn, s.tracking?.format)
            }
        }
        if (s.playbackSent) reportWatchtime(s, isFinal = true)
    }

    private suspend fun reportWatchtime(s: Session, isFinal: Boolean) {
        val url = s.tracking?.watchtimeUrl ?: return
        val endMs = s.lastPositionMs
        // A session still needs its final flag when the last periodic report
        // already covered its full position. Repeat at most the last second,
        // never an entire track or a seeked-over range.
        val startMs = if (isFinal && endMs <= s.lastReportedPositionMs + 1_000L) {
            (endMs - 1_000L).coerceAtLeast(0L)
        } else s.lastReportedPositionMs
        if (endMs <= startMs) return
        s.lastWatchtimeAttemptMs = s.lastObservedAtMs
        val success = sendWatchtime(
            url,
            s.cpn,
            startMs / 1000.0,
            endMs / 1000.0,
            endMs / 1000.0,
            s.listenedMs / 1000.0,
            isFinal,
            s.tracking?.format,
        )
        if (success) {
            s.lastReportedPositionMs = endMs
            s.lastReportedListenedMs = s.listenedMs
        }
    }

    internal fun reset() { session = null }

    private fun createCpn(): String = buildString(16) {
        repeat(16) {
            append(CPN_CHARS[Random.nextInt(CPN_CHARS.length)])
        }
    }

    companion object {
        private const val CPN_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"

        fun isYouTubeVideoId(id: String): Boolean =
            id.length == 11 && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }
    }
}
