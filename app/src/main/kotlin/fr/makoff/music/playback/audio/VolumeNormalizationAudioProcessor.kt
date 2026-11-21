package fr.makoff.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.withSign

@UnstableApi
class VolumeNormalizationAudioProcessor : BaseAudioProcessor() {
    private var encoding = C.ENCODING_INVALID
    private var bytesPerSample = 0

    @Volatile
    var enabled = false
        set(value) {
            if (field != value) {
                field = value
                Timber.tag(TAG).d("Normalization processor enabled: $value")
            }
        }

    private data class GainState(val targetGainMb: Int, val linearGain: Double)

    @Volatile
    private var currentGain = GainState(0, 1.0)

    @Synchronized
    fun setTargetGain(gainMb: Int) {
        if (currentGain.targetGainMb != gainMb) {
            val linearGain = 10.0.pow(gainMb / 2000.0)
            currentGain = GainState(gainMb, linearGain)
            Timber.tag(TAG).d("Target gain set to $gainMb mB (Linear multiplier: $linearGain)")
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        encoding = inputAudioFormat.encoding
        bytesPerSample =
            when (encoding) {
                C.ENCODING_PCM_16BIT -> 2
                C.ENCODING_PCM_24BIT -> 3
                C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
                else -> throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
            }

        Timber.tag(TAG).d(
            "Configured: sampleRate=${inputAudioFormat.sampleRate}, channels=${inputAudioFormat.channelCount}, encoding=$encoding",
        )
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val gain = currentGain
        val applyGain = enabled && gain.targetGainMb != 0
        val output = replaceOutputBuffer(inputBuffer.remaining())
        if (!applyGain) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        output.order(ByteOrder.LITTLE_ENDIAN)
        val sampleCount = inputBuffer.remaining() / bytesPerSample

        when (encoding) {
            C.ENCODING_PCM_16BIT ->
                repeat(sampleCount) {
                    val sample = normalizedSample(inputBuffer.getShort().toDouble(), gain.linearGain, 32768.0)
                    output.putShort(sample.toInt().coerceIn(-32768, 32767).toShort())
                }

            C.ENCODING_PCM_24BIT ->
                repeat(sampleCount) {
                    val b0 = inputBuffer.get().toInt() and 0xFF
                    val b1 = inputBuffer.get().toInt() and 0xFF
                    val b2 = inputBuffer.get().toInt()
                    val sample = (b2 shl 16) or (b1 shl 8) or b0
                    val processed = normalizedSample(sample.toDouble(), gain.linearGain, 8388608.0)
                        .toInt().coerceIn(-8388608, 8388607)
                    output.put((processed and 0xFF).toByte())
                    output.put(((processed shr 8) and 0xFF).toByte())
                    output.put(((processed shr 16) and 0xFF).toByte())
                }

            C.ENCODING_PCM_32BIT ->
                repeat(sampleCount) {
                    val sample = normalizedSample(inputBuffer.getInt().toDouble(), gain.linearGain, 2147483648.0)
                    output.putInt(sample.toLong().coerceIn(-2147483648L, 2147483647L).toInt())
                }

            C.ENCODING_PCM_FLOAT ->
                repeat(sampleCount) {
                    val sample = normalizedSample(inputBuffer.getFloat().toDouble(), gain.linearGain, 1.0)
                    output.putFloat(sample.toFloat().coerceIn(-1f, 1f))
                }
        }

        output.flip()
    }

    override fun onReset() {
        encoding = C.ENCODING_INVALID
        bytesPerSample = 0
    }

    private companion object {
        const val TAG = "VolumeNormalizationProcessor"
    }
}


// The positive-gain knee keeps the slope continuous at the threshold and leaves headroom at full scale.
internal fun normalizedSample(sample: Double, gain: Double, fullScale: Double): Double {
    val amplified = sample * gain
    if (gain <= 1.0) return amplified
    val knee = fullScale * 0.89
    val excess = abs(amplified) - knee
    if (excess <= 0.0) return amplified
    val headroom = fullScale * 0.109
    return (knee + headroom * excess / (headroom + excess)).withSign(amplified)
}
