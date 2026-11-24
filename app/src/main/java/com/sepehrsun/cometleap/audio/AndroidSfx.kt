package com.sepehrsun.cometleap.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import com.sepehrsun.cometleap.game.Sfx
import java.io.File
import java.util.concurrent.Executors

/** SoundPool for effects, a looping static AudioTrack for the (seamless) music. */
class AndroidSfx(context: Context, private val musicWanted: () -> Boolean) : Sfx {
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val pool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build()
    private val ids = IntArray(Sfx.COUNT) { -1 }
    @Volatile private var ready = false
    @Volatile private var music: AudioTrack? = null
    @Volatile private var foreground = false
    private val exec = Executors.newSingleThreadExecutor()

    init {
        val dir = File(context.cacheDir, "sfx-v1").apply { mkdirs() }
        exec.execute {
            try { prepare(dir) } catch (_: Throwable) { /* audio is optional */ }
        }
    }

    private fun prepare(dir: File) {
        run {
            for (id in 0 until Sfx.COUNT) {
                val f = File(dir, "s$id.wav")
                if (!f.exists() || f.length() < 64) f.writeBytes(Synth.wav(Synth.effect(id)))
                ids[id] = pool.load(f.absolutePath, 1)
            }
            ready = true
            val pcm = Synth.music()
            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder().setSampleRate(Synth.RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.setLoopPoints(0, pcm.size, -1)
                track.setVolume(0.45f)
                music = track
                syncMusic()
            } catch (_: Throwable) {
                // music is optional – never crash the game for it
            }
        }
    }

    override fun play(id: Int, rate: Float, volume: Float) {
        if (!ready) return
        val sid = ids.getOrElse(id) { -1 }
        if (sid > 0) pool.play(sid, volume, volume, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    override fun setMusic(on: Boolean) = syncMusic()

    fun onResume() { foreground = true; syncMusic() }
    fun onPause() { foreground = false; syncMusic() }

    @Synchronized
    private fun syncMusic() {
        val t = music ?: return
        try {
            val want = foreground && musicWanted()
            if (want && t.playState != AudioTrack.PLAYSTATE_PLAYING) t.play()
            else if (!want && t.playState == AudioTrack.PLAYSTATE_PLAYING) t.pause()
        } catch (_: IllegalStateException) { }
    }

    fun release() {
        exec.shutdownNow()
        pool.release()
        music?.release()
        music = null
    }
}
