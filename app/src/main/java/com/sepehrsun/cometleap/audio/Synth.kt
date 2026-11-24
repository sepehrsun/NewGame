package com.sepehrsun.cometleap.audio

import com.sepehrsun.cometleap.game.Sfx
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural audio: every sound effect and the music loop are synthesised at start-up,
 * so the game ships without a single audio asset.
 */
object Synth {
    const val RATE = 22050
    private const val TAU = 2.0 * PI

    fun effect(id: Int): ShortArray = when (id) {
        Sfx.LAUNCH -> launch()
        Sfx.CAPTURE -> bell(880.0, 0.45)
        Sfx.PERFECT -> arpeggio(doubleArrayOf(1046.5, 1318.5, 1568.0, 2093.0), 0.055, 0.35)
        Sfx.STAR -> blip()
        Sfx.BOOM -> boom()
        Sfx.CLICK -> click()
        Sfx.POWER -> arpeggio(doubleArrayOf(523.25, 659.25, 783.99, 1046.5, 1318.5), 0.045, 0.3)
        Sfx.SHIELD -> shimmer()
        Sfx.WARN -> warn()
        else -> ShortArray(1)
    }

    private fun buf(seconds: Double) = DoubleArray((seconds * RATE).toInt())

    private fun launch(): ShortArray {
        val b = buf(0.32)
        val rnd = Random(7)
        var lp = 0.0
        var ph = 0.0
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            val env = (1 - t / 0.32).pow(2.0) * min(1.0, t * 80)
            lp += (rnd.nextDouble(-1.0, 1.0) - lp) * (0.08 + t * 0.6)
            ph += TAU * (280 + 900 * t / 0.32) / RATE
            b[i] = env * (lp * 0.9 + sin(ph) * 0.35)
        }
        return pcm(b, 0.7)
    }

    private fun bell(f: Double, len: Double): ShortArray {
        val b = buf(len)
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            val a = min(1.0, t * 400)
            b[i] = a * (sin(TAU * f * t) * exp(-t * 9) +
                0.5 * sin(TAU * f * 2.0 * t) * exp(-t * 14) +
                0.25 * sin(TAU * f * 3.01 * t) * exp(-t * 20))
        }
        return pcm(b, 0.6)
    }

    private fun arpeggio(notes: DoubleArray, gap: Double, tail: Double): ShortArray {
        val b = buf(gap * notes.size + tail)
        for ((n, f) in notes.withIndex()) {
            val start = (n * gap * RATE).toInt()
            for (i in start until b.size) {
                val t = (i - start).toDouble() / RATE
                b[i] += min(1.0, t * 500) * exp(-t * 11) * (sin(TAU * f * t) + 0.3 * sin(TAU * f * 2 * t))
            }
        }
        return pcm(b, 0.55)
    }

    private fun blip(): ShortArray {
        val b = buf(0.11)
        var ph = 0.0
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            ph += TAU * (1250 + 6000 * t) / RATE
            val s = sin(ph)
            b[i] = exp(-t * 28) * min(1.0, t * 600) * (s + 0.25 * s * s * s)
        }
        return pcm(b, 0.5)
    }

    private fun boom(): ShortArray {
        val b = buf(0.9)
        val rnd = Random(3)
        var lp = 0.0
        var ph = 0.0
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            lp += (rnd.nextDouble(-1.0, 1.0) - lp) * max(0.02, 0.35 * exp(-t * 6))
            ph += TAU * (90 * exp(-t * 3) + 28) / RATE
            b[i] = exp(-t * 4.5) * (lp * 1.4 + sin(ph) * 0.9) * min(1.0, t * 300)
        }
        return pcm(b, 0.9)
    }

    private fun click(): ShortArray {
        val b = buf(0.05)
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            b[i] = exp(-t * 90) * sin(TAU * 720 * t)
        }
        return pcm(b, 0.45)
    }

    private fun shimmer(): ShortArray {
        val b = buf(0.6)
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            val env = exp(-t * 5) * min(1.0, t * 200)
            b[i] = env * (sin(TAU * 1760 * t + 3 * sin(TAU * 7 * t)) + 0.6 * sin(TAU * 2637 * t))
        }
        return pcm(b, 0.5)
    }

    private fun warn(): ShortArray {
        val b = buf(0.36)
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            val gate = if (t < 0.12 || (t > 0.18 && t < 0.3)) 1.0 else 0.0
            b[i] = gate * (sin(TAU * 440 * t) + 0.3 * sin(TAU * 880 * t)) * 0.8
        }
        smooth(b)
        return pcm(b, 0.4)
    }

    /**
     * 16-second seamless ambient loop: Am – F – C – G pad, a plucked arpeggio and a soft pulse.
     */
    fun music(): ShortArray {
        val bpm = 120.0
        val beat = 60.0 / bpm
        val chordLen = beat * 8
        val chords = arrayOf(
            doubleArrayOf(57.0, 60.0, 64.0),  // Am
            doubleArrayOf(53.0, 57.0, 60.0),  // F
            doubleArrayOf(48.0, 52.0, 55.0),  // C
            doubleArrayOf(55.0, 59.0, 62.0),  // G
        )
        val total = (chordLen * chords.size * RATE).toInt()
        val b = DoubleArray(total)
        val rnd = Random(11)

        fun midi(n: Double) = 440.0 * 2.0.pow((n - 69) / 12)

        // pad – detuned saws through a one-pole low-pass, written with wrap-around so the loop is seamless
        for ((ci, chord) in chords.withIndex()) {
            val start = (ci * chordLen * RATE).toInt()
            val len = ((chordLen + 1.2) * RATE).toInt()
            for (note in chord) {
                val f = midi(note - 12)
                for (d in doubleArrayOf(-0.08, 0.08)) {
                    var ph = rnd.nextDouble()
                    var lp = 0.0
                    val inc = f * 2.0.pow(d / 12) / RATE
                    for (i in 0 until len) {
                        val t = i.toDouble() / RATE
                        val env = min(1.0, t / 0.8) * min(1.0, max(0.0, (chordLen + 1.2 - t) / 1.2))
                        ph += inc; if (ph >= 1) ph -= 1
                        val saw = 2 * ph - 1
                        lp += (saw - lp) * 0.06
                        b[(start + i) % total] += lp * env * 0.11
                    }
                }
            }
            // bass
            val bf = midi(chord[0] - 24)
            for (i in 0 until len) {
                val t = i.toDouble() / RATE
                val env = min(1.0, t / 0.05) * min(1.0, max(0.0, (chordLen + 0.3 - t) / 0.3))
                b[(start + i) % total] += sin(TAU * bf * t) * env * 0.22
            }
            // arpeggio – 16th notes, pattern over chord tones
            val pattern = intArrayOf(0, 1, 2, 3, 2, 1, 0, 2, 1, 2, 3, 4, 3, 2, 1, 2)
            val sixteenth = beat / 4
            val steps = (chordLen / sixteenth).toInt()
            for (sIdx in 0 until steps) {
                val p = pattern[sIdx % pattern.size]
                val note = chord[p % 3] + 12 * (1 + p / 3)
                val f = midi(note)
                val ns = start + (sIdx * sixteenth * RATE).toInt()
                val vel = if (sIdx % 4 == 0) 0.16 else 0.1
                for (i in 0 until (0.35 * RATE).toInt()) {
                    val t = i.toDouble() / RATE
                    val env = min(1.0, t * 800) * exp(-t * 12)
                    b[(ns + i) % total] += env * vel * (sin(TAU * f * t) + 0.25 * sin(TAU * f * 2 * t))
                }
            }
        }
        // soft heartbeat kick on every other beat
        val beats = (total / (beat * RATE)).toInt()
        for (k in 0 until beats step 2) {
            val ns = (k * beat * RATE).toInt()
            var ph = 0.0
            for (i in 0 until (0.25 * RATE).toInt()) {
                val t = i.toDouble() / RATE
                ph += TAU * (45 + 90 * exp(-t * 30)) / RATE
                b[(ns + i) % total] += sin(ph) * exp(-t * 14) * 0.3
            }
        }
        return pcm(b, 0.55)
    }

    private fun smooth(b: DoubleArray) {
        var lp = 0.0
        for (i in b.indices) { lp += (b[i] - lp) * 0.5; b[i] = lp }
    }

    private fun pcm(b: DoubleArray, peak: Double): ShortArray {
        var m = 1e-9
        for (v in b) m = max(m, abs(v))
        val g = peak / m
        // short fade-out to avoid clicks
        val fade = min(b.size, 120)
        return ShortArray(b.size) { i ->
            val f = if (i >= b.size - fade) (b.size - i).toDouble() / fade else 1.0
            (b[i] * g * f * 32767).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    fun wav(data: ShortArray): ByteArray {
        val bytes = data.size * 2
        val out = java.io.ByteArrayOutputStream(44 + bytes)
        fun i32(v: Int) { out.write(v and 0xFF); out.write(v shr 8 and 0xFF); out.write(v shr 16 and 0xFF); out.write(v shr 24 and 0xFF) }
        fun i16(v: Int) { out.write(v and 0xFF); out.write(v shr 8 and 0xFF) }
        out.write("RIFF".toByteArray()); i32(36 + bytes); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); i32(16); i16(1); i16(1); i32(RATE); i32(RATE * 2); i16(2); i16(16)
        out.write("data".toByteArray()); i32(bytes)
        for (s in data) i16(s.toInt())
        return out.toByteArray()
    }
}
