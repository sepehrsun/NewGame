package com.sepehrsun.cometleap.game

import android.graphics.Shader

class Planet {
    var id = 0
    var x = 0f
    var y = 0f
    var baseX = 0f
    var r = 40f
    var orbit = 120f        // base capture / orbit radius
    var orbitCur = 120f     // current radius (shrinks while decaying)
    var moveAmp = 0f
    var moveSpeed = 0f
    var phase = 0f
    var light = 0
    var dark = 0
    var bodyShader: Shader? = null
    var glowShader: Shader? = null
    var hasRing = false
    var ringTilt = 0f
    var craters = FloatArray(0)   // triples: dx, dy, radius
    var asteroids = 0
    var astAngle = 0f
    var astSpeed = 0f
    var astAlive = BooleanArray(3)
    var visited = false
    var decay = 0f
    var warned = false
    var spin = 0f

    fun asteroidAngle(k: Int): Float = astAngle + k * (Math.PI.toFloat() * 2f / asteroids)
}

class Hole {
    var x = 0f
    var y = 0f
    var r = 24f
    var reach = 300f
    var spin = 0f
}

class Pickup {
    var x = 0f
    var y = 0f
    var kind = STAR
    var alive = true
    var phase = 0f

    companion object {
        const val STAR = 0
        const val SHIELD = 1
        const val MAGNET = 2
    }
}

/** Struct-of-arrays particle pool; no per-frame allocation. */
class Particles(val cap: Int = 600) {
    val x = FloatArray(cap)
    val y = FloatArray(cap)
    val vx = FloatArray(cap)
    val vy = FloatArray(cap)
    val life = FloatArray(cap)
    val max = FloatArray(cap)
    val size = FloatArray(cap)
    val color = IntArray(cap)
    val drag = FloatArray(cap)
    private var next = 0

    fun emit(px: Float, py: Float, pvx: Float, pvy: Float, l: Float, s: Float, c: Int, d: Float = 1.5f) {
        val i = next
        next = (next + 1) % cap
        x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy
        life[i] = l; max[i] = l; size[i] = s; color[i] = c; drag[i] = d
    }

    fun update(dt: Float) {
        for (i in 0 until cap) {
            if (life[i] <= 0f) continue
            life[i] -= dt
            val k = 1f / (1f + drag[i] * dt)
            vx[i] *= k; vy[i] *= k
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
        }
    }

    fun clear() {
        java.util.Arrays.fill(life, 0f)
    }
}

class FloatText {
    var text = ""
    var x = 0f
    var y = 0f
    var life = 0f
    var max = 1f
    var color = -1
    var size = 60f
}

class Btn(var label: String = "") {
    var x = 0f
    var y = 0f
    var w = 0f
    var h = 0f
    var visible = false
    var enabled = true
    var press = 0f

    fun set(cx: Float, cy: Float, bw: Float, bh: Float) {
        x = cx - bw / 2f; y = cy - bh / 2f; w = bw; h = bh; visible = true
    }

    fun hit(px: Float, py: Float) = visible && px >= x - 12 && px <= x + w + 12 && py >= y - 12 && py <= y + h + 12
    val cx get() = x + w / 2f
    val cy get() = y + h / 2f
}
