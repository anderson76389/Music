package fr.makoff.music.ui.player

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.FlagSet
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
class PlayerVideoSynchronizerTest {
    private class ClockPlayer(var id: String = "test-song") {
        var position = 0L
        var state = Player.STATE_READY
        var playing = true
        var ready = true
        var volume = 1f
        var parameters = PlaybackParameters.DEFAULT
        val seeks = mutableListOf<Long>()
        val mutations = mutableListOf<String>()
        val listeners = mutableSetOf<Player.Listener>()
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { proxy, method, args ->
            when (method.name) {
                "getCurrentMediaItem" -> MediaItem.Builder().setMediaId(id).build()
                "getCurrentPosition" -> position
                "getPlaybackState" -> state
                "isPlaying" -> playing
                "getPlayWhenReady" -> ready
                "getPlaybackParameters" -> parameters
                "setPlaybackParameters" -> { mutations += method.name; parameters = args!![0] as PlaybackParameters; null }
                "setPlayWhenReady" -> { mutations += method.name; ready = args!![0] as Boolean; null }
                "setVolume" -> { mutations += method.name; volume = args!![0] as Float; null }
                "seekTo" -> { mutations += method.name; position = args!!.last() as Long; seeks += position; null }
                "addListener" -> { listeners += args!![0] as Player.Listener; null }
                "removeListener" -> { listeners -= args!![0] as Player.Listener; null }
                "equals" -> proxy === args!![0]
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "ClockPlayer($id)"
                else -> error("Unexpected Player call: ${method.name}")
            }
        } as Player
    }

    private val audio = ClockPlayer()
    private val video = ClockPlayer()
    private var now = 0L
    private val session = PlayerVideoSynchronizer(audio.player, video.player, "test-song") { now }

    @Test fun switchingAlignsOnlyTheMutedVideoWithoutChangingAudio() {
        audio.position = 12_000L
        session.attach()
        session.setActive(true)
        assertEquals(listOf(12_000L), video.seeks)
        assertEquals(0f, video.volume, 0f)
        assertTrue(video.ready)
        assertTrue(audio.mutations.isEmpty())
    }

    @Test fun rapidSwitchesStopImmediatelyAndNeverFlushAnAlignedDecoder() {
        session.attach()
        repeat(20) {
            session.setActive(true)
            assertTrue(video.ready)
            session.setActive(false)
            assertFalse(video.ready)
        }
        assertTrue(video.seeks.isEmpty())
        assertTrue(audio.mutations.isEmpty())
    }

    @Test fun audioBufferingAndFocusLossPauseVideoDespitePlayWhenReady() {
        session.attach()
        session.setActive(true)
        audio.playing = false
        audio.ready = true
        session.tick()
        assertFalse(video.ready)
        audio.playing = true
        session.tick()
        assertTrue(video.ready)
        assertTrue(audio.mutations.isEmpty())
    }

    @Test fun audioEventsStopVideoImmediatelyWithoutWaitingForThePeriodicTick() {
        session.attach()
        session.setActive(true)
        audio.playing = false
        val event = Player.Events(FlagSet.Builder().add(Player.EVENT_IS_PLAYING_CHANGED).build())
        audio.listeners.single().onEvents(audio.player, event)
        assertFalse(video.ready)
        assertTrue(audio.mutations.isEmpty())
    }

    @Test fun speedChangesFollowTheAudioClockWithoutPeriodicSeekLoops() {
        session.attach()
        session.setActive(true)
        audio.parameters = PlaybackParameters(1.75f)
        session.tick()
        assertEquals(audio.parameters, video.parameters)
        assertTrue(video.seeks.isEmpty())
        assertTrue(audio.mutations.isEmpty())
    }

    @Test fun closingOrDisposingStopsTheDecoderAndRemovesTheListener() {
        session.attach()
        assertEquals(setOf(session), audio.listeners)
        session.setActive(true)
        session.detach()
        assertFalse(video.ready)
        assertTrue(audio.listeners.isEmpty())
        session.tick()
        assertFalse(video.ready)
    }

    @Test fun changingSongCannotLeaveThePreviousVideoPlaying() {
        session.attach()
        session.setActive(true)
        audio.id = "next-song"
        session.tick()
        assertFalse(video.ready)
        assertTrue(audio.mutations.isEmpty())
    }

    @Test fun driftCorrectionWaitsForReadyAndDoesNotSeekAgainWhileRebuffering() {
        session.attach()
        session.setActive(true)
        audio.position = 10_000L
        video.state = Player.STATE_BUFFERING
        now = 6_000L
        session.tick()
        assertTrue(video.seeks.isEmpty())
        video.state = Player.STATE_READY
        session.tick()
        assertEquals(listOf(10_000L), video.seeks)
        audio.position = 15_000L
        now += 1_500L
        session.tick()
        assertEquals(1, video.seeks.size)
        now += 5_000L
        session.tick()
        assertEquals(listOf(10_000L, 15_000L), video.seeks)
    }

    @Test fun repeatAndExplicitSeeksFollowTheAudioTimelineImmediately() {
        session.attach()
        session.setActive(true)
        video.position = 19_000L
        val end = Player.PositionInfo(null, 0, null, 0, 19_000L, 19_000L, -1, -1)
        val start = Player.PositionInfo(null, 0, null, 0, 0L, 0L, -1, -1)
        session.onPositionDiscontinuity(end, start, Player.DISCONTINUITY_REASON_AUTO_TRANSITION)
        assertEquals(listOf(0L), video.seeks)
        val sought = Player.PositionInfo(null, 0, null, 0, 8_000L, 8_000L, -1, -1)
        session.onPositionDiscontinuity(start, sought, Player.DISCONTINUITY_REASON_SEEK)
        assertEquals(listOf(0L, 8_000L), video.seeks)
        assertTrue(audio.mutations.isEmpty())
    }

    @Test fun returningToVideoAlignsEvenIfTheOldDecoderWasStillBuffering() {
        session.attach()
        audio.position = 25_000L
        video.state = Player.STATE_BUFFERING
        session.setActive(true)
        assertEquals(listOf(25_000L), video.seeks)
        assertTrue(audio.mutations.isEmpty())
    }
}
