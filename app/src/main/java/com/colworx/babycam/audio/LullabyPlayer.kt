package com.colworx.babycam.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.content.Context
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Plays alert / soothing sounds on the baby unit's speaker, fully synthesized in code
 * (no audio files needed). Called by BabyCamConnection when the parent sends a "lullaby"
 * signaling message.
 *
 * The primary sound used by the UI is [Sound.BELL] — a loud, repeating struck-bell ring,
 * played on the ALARM stream at max volume so it can get someone's attention from another
 * room. The other (older, soothing) sounds remain available but are no longer surfaced.
 */
object LullabyPlayer {

    private var job: Job? = null
    private var audioTrack: AudioTrack? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    var isPlaying = false
        private set

    enum class Sound { BELL, WHITE_NOISE, HEARTBEAT, RAIN }

    /**
     * @param appContext if provided and [sound] is [Sound.BELL], the device's alarm volume is
     *        raised to max so the bell is genuinely loud regardless of the current media volume.
     */
    fun play(sound: Sound = Sound.BELL, appContext: Context? = null) {
        stop()
        isPlaying = true
        val sampleRate = 44100
        val bufSize = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        // The bell is an alert, so route it through STREAM_ALARM (loud, ignores media volume)
        // and crank the alarm volume to max. Soothing sounds keep using STREAM_MUSIC.
        val streamType =
            if (sound == Sound.BELL) AudioManager.STREAM_ALARM else AudioManager.STREAM_MUSIC
        if (sound == Sound.BELL && appContext != null) {
            runCatching {
                val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    am.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                    0
                )
            }
        }

        @Suppress("DEPRECATION")
        val track = AudioTrack(
            streamType, sampleRate,
            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
            bufSize, AudioTrack.MODE_STREAM
        )
        audioTrack = track

        job = scope.launch {
            val buf = ShortArray(bufSize / 2)
            track.play()
            var phase = 0.0
            var n = 0L // running sample index, for time-based envelopes that span buffers
            while (isActive) {
                when (sound) {
                    Sound.BELL -> {
                        // Struck metal bell: inharmonic partials with a sharp attack and
                        // exponential decay, re-struck every 0.9s so it "rings" continuously.
                        val period = (sampleRate * 0.9).toInt()
                        val base = 2.0 * PI * 660.0
                        for (i in buf.indices) {
                            val t = (n % period).toDouble() / sampleRate
                            val env = exp(-5.5 * t)
                            var s = sin(base * t)
                            s += 0.6 * sin(base * 2.0 * t)
                            s += 0.4 * sin(base * 2.4 * t)
                            s += 0.25 * sin(base * 3.0 * t)
                            s += 0.15 * sin(base * 4.5 * t)
                            buf[i] = ((s / 2.4) * env * 30000.0).toInt()
                                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                            n++
                        }
                    }
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
