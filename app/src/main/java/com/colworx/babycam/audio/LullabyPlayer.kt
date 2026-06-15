package com.colworx.babycam.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Plays soothing sounds on the baby unit's speaker.
 * White noise = random PCM via AudioTrack (offline, no files needed).
 * Heartbeat = low-frequency sine pulse.
 * Called by BabyCamConnection when parent sends a "lullaby" signaling message.
 */
object LullabyPlayer {

    private var job: Job? = null
    private var audioTrack: AudioTrack? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    var isPlaying = false
        private set

    enum class Sound { WHITE_NOISE, HEARTBEAT, RAIN }

    fun play(sound: Sound = Sound.WHITE_NOISE) {
        stop()
        isPlaying = true
        val sampleRate = 44100
        val bufSize = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        @Suppress("DEPRECATION")
        val track = AudioTrack(
            AudioManager.STREAM_MUSIC, sampleRate,
            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
            bufSize, AudioTrack.MODE_STREAM
        )
        audioTrack = track

        job = scope.launch {
            val buf = ShortArray(bufSize / 2)
            track.play()
            var phase = 0.0
            while (isActive) {
                when (sound) {
                    Sound.WHITE_NOISE -> {
                        for (i in buf.indices)
                            buf[i] = (Random.nextInt(-8192, 8192)).toShort()
                    }
                    Sound.HEARTBEAT -> {
                        // 60 BPM double-pulse (lub-dub)
                        for (i in buf.indices) {
                            phase += 2.0 * PI * 60.0 / sampleRate
                            val env = sin(phase).coerceIn(0.0, 1.0)
                            buf[i] = (sin(phase * 4) * env * 16000).toInt().toShort()
                        }
                    }
                    Sound.RAIN -> {
                        for (i in buf.indices) {
                            val noise = Random.nextInt(-4096, 4096)
                            buf[i] = (noise * if (Random.nextFloat() < 0.02f) 3f else 0.5f).toInt()
                                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                        }
                    }
                }
                track.write(buf, 0, buf.size)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        audioTrack?.let { runCatching { it.stop() }; runCatching { it.release() } }
        audioTrack = null
        isPlaying = false
    }
}
