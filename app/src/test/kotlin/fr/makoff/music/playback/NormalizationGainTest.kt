package fr.makoff.music.playback

import fr.makoff.music.constants.LoudnessLevel
import fr.makoff.music.playback.audio.normalizedSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizationGainTest {
    @Test
    fun `perceptual loudness takes priority`() {
        assertEquals(-506, normalizationGainMb(-20.0, -8.94, -14f))
    }

    @Test
    fun `legacy loudness uses YouTube reference level`() {
        assertEquals(-506, normalizationGainMb(-1.94, null, -14f))
    }

    @Test
    fun `gain is clamped to processor limits`() {
        assertEquals(600, normalizationGainMb(-30.0, null, -14f))
        assertEquals(-1500, normalizationGainMb(null, 10.0, -14f))
    }

    @Test
    fun `missing loudness disables normalization`() {
        assertNull(normalizationGainMb(null, null, -14f))
    }

    @Test
    fun `default raises a recording just below its target`() {
        val balanced = normalizationGainMb(null, -8.0, LoudnessLevel.BALANCED.targetLufs)
        val defaultGain = normalizationGainMb(null, -8.0, LoudnessLevel.DEFAULT.targetLufs)
        assertEquals(-600, balanced)
        assertEquals(100, defaultGain)
    }

    @Test
    fun `positive gain keeps quiet samples linear and separates loud peaks`() {
        val gain = 1.4
        for (fullScale in listOf(1.0, 32768.0, 8388608.0, 2147483648.0)) {
            assertEquals(fullScale * 0.35, normalizedSample(fullScale * 0.25, gain, fullScale), fullScale * 1e-9)
            assertEquals(fullScale * 0.5, normalizedSample(fullScale, 0.5, fullScale), 0.0)
            val peak = normalizedSample(fullScale, gain, fullScale)
            assertTrue(peak > normalizedSample(fullScale * 0.96, gain, fullScale))
            assertTrue(peak < fullScale)
            assertEquals(-peak, normalizedSample(-fullScale, gain, fullScale), 0.0)
        }
    }

}
