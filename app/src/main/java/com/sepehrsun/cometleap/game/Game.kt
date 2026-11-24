package com.sepehrsun.cometleap.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Comet Leap – the whole game: simulation, procedural world, rendering and UI.
 * World units: the screen is always 1000 units wide; height depends on aspect ratio.
 */
class Game(
    private val s: Strings,
    private val store: Store,
    private val sfx: Sfx,
    private val haptic: (Int) -> Unit = {},
    seed: Long = System.nanoTime(),
    private val today: () -> Long = { System.currentTimeMillis() / 86_400_000L },
) {
    companion object {
        const val W = 1000f
        const val MENU = 0
        const val PLAY = 1
        const val PAUSE = 2
        const val OVER = 3
        const val SHOP = 4
        const val HAPTIC_LIGHT = 0
        const val HAPTIC_HEAVY = 1
        const val GIFT = 25
        const val CONTINUE_COST = 30
        private const val STEP = 1f / 120f
        private const val TWO_PI = (PI * 2).toFloat()
        private const val TRAIL = 28
        private const val DEATH_LOST = 0
        private const val DEATH_CRASH = 1
        private const val DEATH_VOID = 2
    }

    private val rnd = Random(seed)

    // ---------------------------------------------------------------- screen
    var scale = 1f; private set
    var H = 1800f; private set
    private var offX = 0f          // horizontal letterbox offset in px (wide screens)
    private var vl = 0f            // visible world-left (may be < 0 on wide screens)
    private var vr = W             // visible world-right
    private var safeTop = 40f
    private var safeBottom = 20f

    // ---------------------------------------------------------------- state
    var state = MENU; private set
    private var uiTime = 0f
    private var stateTime = 0f
    private var simTime = 0f
    private var acc = 0f
    private var timeScale = 1f

    // world
    private val planets = ArrayList<Planet>()
    private val holes = ArrayList<Hole>()
    private val pickups = ArrayList<Pickup>()
    private val parts = Particles()
    private val texts = Array(10) { FloatText() }
    private var planetCounter = 0
    private var camY = 0f
    private var shake = 0f
    private var flash = 0f
    private var flashColor = Color.WHITE

    // player
    private var px = 0f
    private var py = 0f
    private var vx = 0f
    private var vy = 0f
    private var orbiting: Planet? = null
    private var lastPlanet: Planet? = null
    private var ang = 0f
    private var dir = 1f
    private var flyTime = 0f
    private var alive = true
    private var deathTimer = 0f
    private var deathReason = DEATH_LOST
    private var shield = false
    private var magnet = 0f
    private var invuln = 0f
    private val trailX = FloatArray(TRAIL)
    private val trailY = FloatArray(TRAIL)
    private var trailHead = 0
    private var trailCount = 0
    private var trailTick = 0

    // run stats
    var score = 0; private set
    private var combo = 0
    private var runStars = 0
    private var continued = false
    private var newBest = false
    private var displayScore = 0f
    private var scorePop = 0f
    private var hintDecayShown = false
    private var hintDecay = 0f
    private var toast = ""
    private var toastTime = 0f

    // ---------------------------------------------------------------- paints & caches
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    }
    private val textThin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val starPath = Path()
    private val tmpPath = Path()
    private var bgShader: Shader? = null
    private var bgKey = -1
    private var nebula: Array<Bitmap>? = null
    private val nebulaColors = intArrayOf(0xFF6A2BFF.toInt(), 0xFF00B3FF.toInt(), 0xFFFF2B8A.toInt())
    private val bgStarsX = FloatArray(150)
    private val bgStarsY = FloatArray(150)
    private val bgStarsS = FloatArray(150)
    private val bgStarsP = FloatArray(150)
    private var headShader: Shader? = null
    private var headShaderSkin = -1

    private val palettes = arrayOf(
        intArrayOf(0xFF7FE7FF.toInt(), 0xFF2C3FBF.toInt()),
        intArrayOf(0xFFFFB37F.toInt(), 0xFFB23A1C.toInt()),
        intArrayOf(0xFFB7FF8A.toInt(), 0xFF1E7F4A.toInt()),
        intArrayOf(0xFFFF9BE0.toInt(), 0xFF7A2BB0.toInt()),
        intArrayOf(0xFFFFE58A.toInt(), 0xFFC46B12.toInt()),
        intArrayOf(0xFF9AA8FF.toInt(), 0xFF30307A.toInt()),
        intArrayOf(0xFF7FFFD4.toInt(), 0xFF137A70.toInt()),
        intArrayOf(0xFFFF8A8A.toInt(), 0xFF7A1530.toInt()),
    )
    private val skyTop = intArrayOf(0xFF0B0F2E.toInt(), 0xFF081B38.toInt(), 0xFF22082F.toInt(), 0xFF03161D.toInt(), 0xFF0E0020.toInt())
    private val skyBot = intArrayOf(0xFF1D0B3A.toInt(), 0xFF2A0B44.toInt(), 0xFF3A0B1F.toInt(), 0xFF0B2E3B.toInt(), 0xFF02020A.toInt())

    // ---------------------------------------------------------------- buttons
    private val bPlay = Btn()
    private val bShop = Btn()
    private val bSound = Btn()
    private val bMusic = Btn()
    private val bGift = Btn()
    private val bPause = Btn()
    private val bResume = Btn()
    private val bMenu = Btn()
    private val bRetry = Btn()
    private val bContinue = Btn()
    private val bBack = Btn()
    private val skinCards = Array(Skins.all.size) { Btn() }
    private val allButtons = arrayOf(bPlay, bShop, bSound, bMusic, bGift, bPause, bResume, bMenu, bRetry, bContinue, bBack) + skinCards
    private var shopShake = -1
    private var shopShakeT = 0f

    init {
        buildStarPath()
        for (i in bgStarsX.indices) {
            bgStarsX[i] = rnd.nextFloat() * W
            bgStarsY[i] = rnd.nextFloat()
            bgStarsS[i] = 1.2f + rnd.nextFloat() * 2.6f
            bgStarsP[i] = rnd.nextFloat() * TWO_PI
        }
        resetWorld()
    }

    // ================================================================= public API

    fun resize(pw: Int, ph: Int, safeTopPx: Int, safeBottomPx: Int) {
        if (pw <= 0 || ph <= 0) return
        // Portrait phones: the 1000-unit play column fills the width.
        // Wide screens (tablets, foldables, landscape on Android 16+): keep a tall
        // play column centred and extend the sky to the sides.
        val minH = 1500f
        if (ph / pw.toFloat() >= minH / W) {
            scale = pw / W
            offX = 0f
        } else {
            scale = ph / minH
            offX = (pw - W * scale) / 2f
        }
        H = ph / scale
        vl = -offX / scale
        vr = W + offX / scale
        safeTop = safeTopPx / scale + 24f
        safeBottom = safeBottomPx / scale + 24f
        bgKey = -1
        if (nebula == null) nebula = Array(3) { makeNebula(nebulaColors[it]) }
        if (state == MENU) snapCamera()
    }

    /** Called with real elapsed seconds. */
    fun update(dtReal: Float) {
        val dt = dtReal.coerceIn(0f, 0.05f)
        uiTime += dt
        stateTime += dt
        for (b in allButtons) b.press = max(0f, b.press - dt * 5f)
        if (toastTime > 0) toastTime -= dt
        if (shopShakeT > 0) shopShakeT -= dt
        flash = max(0f, flash - dt * 2.5f)
        shake = max(0f, shake - dt * 60f)
        displayScore += (score - displayScore) * min(1f, dt * 12f)
        scorePop = max(0f, scorePop - dt * 3f)
        if (hintDecay > 0) hintDecay -= dt

        when (state) {
            PLAY -> {
                acc += dt * timeScale
                while (acc >= STEP) {
                    step(STEP)
                    acc -= STEP
                }
                if (!alive) {
                    deathTimer += dt
                    timeScale = if (deathTimer < 0.7f) 0.3f else 1f
                    if (deathTimer > 1.25f) enterOver()
                }
            }
            MENU, SHOP -> {
                // the menu shows a live comet circling the start planet
                acc += dt
                while (acc >= STEP) {
                    ambientStep(STEP)
                    acc -= STEP
                }
            }
            OVER -> {
                acc += dt
                while (acc >= STEP) {
                    parts.update(STEP)
                    updateTexts(STEP)
                    acc -= STEP
                }
            }
        }
    }

    fun touchDown(xPx: Float, yPx: Float) {
        val x = (xPx - offX) / scale
        val y = yPx / scale
        when (state) {
            MENU -> when {
                bPlay.hit(x, y) -> { click(bPlay); startRun() }
                bShop.hit(x, y) -> { click(bShop); setState(SHOP) }
                bSound.hit(x, y) -> { store.sound = !store.sound; click(bSound) }
                bMusic.hit(x, y) -> { store.music = !store.music; sfx.setMusic(store.music); click(bMusic) }
                bGift.hit(x, y) && giftAvailable() -> { click(bGift); claimGift() }
            }
            PLAY -> when {
                bPause.hit(x, y) && alive -> { click(bPause); setState(PAUSE) }
                else -> launch()
            }
            PAUSE -> when {
                bResume.hit(x, y) -> { click(bResume); setState(PLAY) }
                bMenu.hit(x, y) -> { click(bMenu); toMenu() }
                bSound.hit(x, y) -> { store.sound = !store.sound; click(bSound) }
                bMusic.hit(x, y) -> { store.music = !store.music; sfx.setMusic(store.music); click(bMusic) }
            }
            OVER -> if (stateTime > 0.45f) when {
                bRetry.hit(x, y) -> { click(bRetry); startRun() }
                bContinue.hit(x, y) && bContinue.enabled -> { click(bContinue); continueRun() }
                bContinue.hit(x, y) -> { showToast(s.notEnough) }
                bMenu.hit(x, y) -> { click(bMenu); toMenu() }
            }
            SHOP -> {
                if (bBack.hit(x, y)) { click(bBack); setState(MENU); return }
                for (i in skinCards.indices) if (skinCards[i].hit(x, y)) { tapSkin(i); return }
            }
        }
    }

    /** @return true if the back press was consumed. */
    fun onBack(): Boolean = when (state) {
        MENU -> false
        PLAY -> { if (alive) setState(PAUSE); true }
        PAUSE -> { setState(PLAY); true }
        OVER, SHOP -> { toMenu(); true }
        else -> false
    }

    fun onAppPaused() {
        if (state == PLAY && alive) setState(PAUSE)
    }

    // test / automation hooks
    internal fun debugOpen(st: Int) { setState(st) }
    internal var debugHideHud = false
    internal fun debugKill() { shield = false; die(DEATH_CRASH) }
    internal val planetsVisible get() = planets.size
    val isOrbiting get() = orbiting != null && alive
    fun aimQuality(): Float {
        // How well would a launch right now hit the next planet? (cosine of aim error)
        val p = orbiting ?: return -1f
        val t = nextTarget(p) ?: return -1f
        val tx = -sin(ang) * dir
        val ty = cos(ang) * dir
        val dx = t.x - px
        val dy = t.y - py
        val d = sqrt(dx * dx + dy * dy)
        return (tx * dx + ty * dy) / d
    }

    // ================================================================= flow

    private fun setState(st: Int) {
        state = st
        stateTime = 0f
    }

    private fun click(b: Btn) {
        b.press = 1f
        if (store.sound) sfx.play(Sfx.CLICK)
        haptic(HAPTIC_LIGHT)
    }

    private fun toMenu() {
        resetWorld()
        setState(MENU)
    }

    private fun startRun() {
        if (state != MENU || score > 0 || !alive) resetWorld()
        store.gamesPlayed = store.gamesPlayed + 1
        setState(PLAY)
    }

    private fun resetWorld() {
        planets.clear(); holes.clear(); pickups.clear(); parts.clear()
        for (t in texts) t.life = 0f
        planetCounter = 0
        score = 0; combo = 0; runStars = 0; continued = false; newBest = false
        displayScore = 0f
        alive = true; deathTimer = 0f; timeScale = 1f
        shield = false; magnet = 0f; invuln = 0f
        hintDecayShown = false
        val first = makePlanet(W / 2f, 0f, first = true)
        first.visited = true
        planets.add(first)
        while (planets.size < 6) spawnNext()
        orbiting = first
        lastPlanet = first
        ang = -PI.toFloat() / 2f
        dir = 1f
        placeOnOrbit(first)
        trailCount = 0
        snapCamera()
    }

    private fun snapCamera() {
        camY = (lastPlanet?.y ?: 0f) - H * 0.62f
    }

    private fun enterOver() {
        if (state != PLAY) return
        if (score > store.best) {
            store.best = score
            newBest = true
        }
        setState(OVER)
    }

    private fun continueRun() {
        if (continued || store.stars < CONTINUE_COST) return
        store.stars = store.stars - CONTINUE_COST
        continued = true
        val p = lastPlanet ?: return
        respawnAt(p)
        invuln = 2f
        setState(PLAY)
    }

    private fun respawnAt(p: Planet) {
        alive = true
        deathTimer = 0f
        timeScale = 1f
        p.decay = 0f
        p.warned = false
        p.orbitCur = p.orbit
        for (k in 0 until p.asteroids) p.astAlive[k] = false
        orbiting = p
        lastPlanet = p
        placeOnOrbit(p)
        trailCount = 0
        burst(px, py, 40, 0xFF7FF3FF.toInt(), 500f)
        flash = 0.6f; flashColor = 0xFF7FF3FF.toInt()
    }

    private fun giftAvailable() = store.lastGiftDay != today()

    private fun claimGift() {
        store.lastGiftDay = today()
        store.stars = store.stars + GIFT
        if (store.sound) sfx.play(Sfx.POWER)
        showToast(String.format(s.giftReceived, GIFT))
        for (i in 0 until 50) {
            val a = rnd.nextFloat() * TWO_PI
            val v = 200f + rnd.nextFloat() * 700f
            parts.emit(bGift.cx, bGift.cy + camY, cos(a) * v, sin(a) * v, 1.2f, 5f + rnd.nextFloat() * 6f, 0xFFFFD34D.toInt(), 2.5f)
        }
    }

    private fun tapSkin(i: Int) {
        val owned = store.ownedSkins and (1 shl i) != 0
        val skin = Skins.all[i]
        when {
            owned -> {
                store.skin = i
                skinCards[i].press = 1f
                if (store.sound) sfx.play(Sfx.CLICK)
                haptic(HAPTIC_LIGHT)
                headShaderSkin = -1
            }
            store.stars >= skin.price -> {
                store.stars = store.stars - skin.price
                store.ownedSkins = store.ownedSkins or (1 shl i)
                store.skin = i
                skinCards[i].press = 1f
                headShaderSkin = -1
                if (store.sound) sfx.play(Sfx.POWER)
                haptic(HAPTIC_HEAVY)
                val c = skinCards[i]
                for (k in 0 until 60) {
                    val a = rnd.nextFloat() * TWO_PI
                    val v = 150f + rnd.nextFloat() * 600f
                    parts.emit(c.cx, c.cy + camY, cos(a) * v, sin(a) * v, 1f, 4f + rnd.nextFloat() * 6f, skin.trailA, 2.5f)
                }
            }
            else -> {
                shopShake = i; shopShakeT = 0.4f
                showToast(s.notEnough)
                haptic(HAPTIC_LIGHT)
            }
        }
    }

    private fun showToast(t: String) {
        toast = t; toastTime = 1.6f
    }

    // ================================================================= world generation

    private fun difficulty(idx: Int) = min(1f, idx / 55f)

    private fun makePlanet(x: Float, y: Float, first: Boolean = false): Planet {
        val p = Planet()
        p.id = planetCounter++
        val d = difficulty(p.id)
        p.r = if (first) 58f else lerp(46f, 34f, d) + rnd.nextFloat() * lerp(22f, 14f, d)
        p.orbit = if (first) 150f else p.r + lerp(82f, 58f, d) + rnd.nextFloat() * 34f
        p.orbitCur = p.orbit
        p.x = x; p.baseX = x; p.y = y
        val pal = palettes[if (first) 0 else rnd.nextInt(palettes.size)]
        p.light = pal[0]; p.dark = pal[1]
        p.bodyShader = RadialGradient(
            -p.r * 0.38f, -p.r * 0.42f, p.r * 1.5f,
            intArrayOf(p.light, p.dark, darken(p.dark, 0.35f)), floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP,
        )
        p.glowShader = RadialGradient(
            0f, 0f, p.r * 2.4f,
            intArrayOf(withAlpha(p.light, 90), withAlpha(p.light, 0)), null, Shader.TileMode.CLAMP,
        )
        p.hasRing = !first && rnd.nextFloat() < 0.3f
        p.ringTilt = -25f + rnd.nextFloat() * 50f
        val n = 2 + rnd.nextInt(3)
        p.craters = FloatArray(n * 3)
        for (k in 0 until n) {
            val a = rnd.nextFloat() * TWO_PI
            val dd = rnd.nextFloat() * p.r * 0.55f
            p.craters[k * 3] = cos(a) * dd
            p.craters[k * 3 + 1] = sin(a) * dd
            p.craters[k * 3 + 2] = p.r * (0.1f + rnd.nextFloat() * 0.13f)
        }
        p.spin = rnd.nextFloat() * TWO_PI
        if (!first && p.id > 6 && rnd.nextFloat() < 0.12f + 0.4f * d) {
            val room = min(p.x - p.orbit - 20f, W - p.x - p.orbit - 20f)
            p.moveAmp = min(room, 70f + rnd.nextFloat() * 160f)
            if (p.moveAmp < 40f) p.moveAmp = 0f
            p.moveSpeed = 0.6f + rnd.nextFloat() * (0.5f + d)
            p.phase = rnd.nextFloat() * TWO_PI
        }
        if (!first && p.id > 10 && rnd.nextFloat() < 0.1f + 0.35f * d) {
            p.asteroids = 1 + rnd.nextInt(if (d > 0.5f) 3 else 2)
            p.astSpeed = (0.5f + rnd.nextFloat() * 0.5f + d * 0.3f) * if (rnd.nextBoolean()) 1f else -1f
            p.astAngle = rnd.nextFloat() * TWO_PI
            for (k in 0 until 3) p.astAlive[k] = k < p.asteroids
        }
        return p
    }

    private fun spawnNext() {
        val prev = planets.last()
        val idx = planetCounter
        val d = difficulty(idx)
        // probe a radius first so spacing can respect both orbits
        val p = makePlanet(0f, 0f)
        val minGap = prev.orbit + p.orbit + lerp(60f, 120f, d)
        var gap = minGap + rnd.nextFloat() * lerp(90f, 140f, d)
        gap = min(gap, max(minGap, H * 0.5f))
        val margin = p.orbit + 24f + p.moveAmp
        var x = margin + rnd.nextFloat() * (W - 2 * margin).coerceAtLeast(1f)
        if (abs(x - prev.baseX) < 150f) {
            x = if (prev.baseX < W / 2) prev.baseX + 150f + rnd.nextFloat() * 200f else prev.baseX - 150f - rnd.nextFloat() * 200f
            x = x.coerceIn(margin, W - margin)
        }
        p.x = x; p.baseX = x; p.y = prev.y - gap
        planets.add(p)

        // collectibles along the corridor between the two planets
        val count = 1 + rnd.nextInt(3)
        val ox = (rnd.nextFloat() - 0.5f) * 140f
        for (k in 1..count) {
            val t = k / (count + 1f)
            val pk = Pickup()
            pk.x = (lerp(prev.baseX, p.baseX, t) + ox * sin(t * PI.toFloat())).coerceIn(40f, W - 40f)
            pk.y = lerp(prev.y, p.y, t)
            pk.phase = rnd.nextFloat() * TWO_PI
            pickups.add(pk)
        }
        if (idx > 3 && rnd.nextFloat() < 0.11f) {
            val pk = Pickup()
            pk.kind = if (rnd.nextBoolean()) Pickup.SHIELD else Pickup.MAGNET
            pk.x = (p.baseX + prev.baseX) / 2f + (rnd.nextFloat() - 0.5f) * 200f
            pk.x = pk.x.coerceIn(60f, W - 60f)
            pk.y = (p.y + prev.y) / 2f - 40f
            pickups.add(pk)
        }
        // black holes appear in later space
        if (idx > 14 && rnd.nextFloat() < 0.08f + 0.22f * d) {
            val h = Hole()
            h.y = (p.y + prev.y) / 2f
            val mid = (p.baseX + prev.baseX) / 2f
            h.x = if (mid < W / 2) mid + 230f + rnd.nextFloat() * 120f else mid - 230f - rnd.nextFloat() * 120f
            h.x = h.x.coerceIn(60f, W - 60f)
            h.r = 22f + rnd.nextFloat() * 10f
            h.reach = 320f
            holes.add(h)
        }
    }

    private fun nextTarget(from: Planet): Planet? {
        var best: Planet? = null
        for (p in planets) if (p !== from && p.y < from.y && (best == null || p.y > best.y)) best = p
        return best
    }

    // ================================================================= simulation

    private fun placeOnOrbit(p: Planet) {
        px = p.x + cos(ang) * p.orbitCur
        py = p.y + sin(ang) * p.orbitCur
    }

    private fun orbitSpeed() = lerp(560f, 780f, difficulty(score))

    private fun launch() {
        val p = orbiting ?: return
        if (!alive) return
        val v = lerp(980f, 1150f, difficulty(score))
        vx = -sin(ang) * dir * v
        vy = cos(ang) * dir * v
        orbiting = null
        lastPlanet = p
        flyTime = 0f
        if (store.sound) sfx.play(Sfx.LAUNCH, 0.9f + rnd.nextFloat() * 0.2f)
        for (i in 0 until 14) {
            val a = atan2(-vy, -vx) + (rnd.nextFloat() - 0.5f) * 1.2f
            val sp = 150f + rnd.nextFloat() * 300f
            parts.emit(px, py, cos(a) * sp, sin(a) * sp, 0.5f, 4f + rnd.nextFloat() * 4f, currentSkinColor(rnd.nextFloat()), 3f)
        }
    }

    private fun ambientStep(dt: Float) {
        simTime += dt
        val p = orbiting ?: return
        ang += dir * (orbitSpeed() / p.orbitCur) * dt
        placeOnOrbit(p)
        recordTrail()
        if (rnd.nextFloat() < 0.25f) emitSpark()
        parts.update(dt)
        updateTexts(dt)
        planetsUpdate(dt)
    }

    private fun planetsUpdate(dt: Float) {
        for (p in planets) {
            if (p.moveAmp > 0f) p.x = p.baseX + sin(simTime * p.moveSpeed + p.phase) * p.moveAmp
            if (p.asteroids > 0) p.astAngle += p.astSpeed * dt
            p.spin += dt * 0.15f
        }
        for (h in holes) h.spin += dt * 2.2f
    }

    private fun step(dt: Float) {
        simTime += dt
        planetsUpdate(dt)
        parts.update(dt)
        updateTexts(dt)
        if (invuln > 0) invuln -= dt
        if (magnet > 0) magnet -= dt

        if (!alive) return

        val o = orbiting
        if (o != null) {
            // orbit around planet, decaying over time
            o.decay += dt
            val d = difficulty(score)
            val decayStart = lerp(6.5f, 3.4f, d)
            if (o.decay > decayStart - 1.3f && !o.warned) {
                o.warned = true
                if (store.sound) sfx.play(Sfx.WARN)
                if (!hintDecayShown && store.gamesPlayed <= 3) { hintDecayShown = true; hintDecay = 3f }
            }
            if (o.decay > decayStart) {
                o.orbitCur -= dt * lerp(40f, 70f, d)
                if (o.orbitCur < o.r + 12f) { die(DEATH_CRASH); return }
            }
            ang += dir * (orbitSpeed() / o.orbitCur) * dt
            placeOnOrbit(o)
            if (rnd.nextFloat() < 0.3f) emitSpark()
        } else {
            flyTime += dt
            // gravity
            var ax = 0f
            var ay = 0f
            for (p in planets) {
                if (p === lastPlanet) continue
                val dx = p.x - px
                val dy = p.y - py
                val d2 = dx * dx + dy * dy
                if (d2 > 520f * 520f) continue
                val a = min(1400f, 5200f * p.r * p.r / d2)
                val inv = 1f / sqrt(d2)
                ax += dx * inv * a; ay += dy * inv * a
            }
            for (h in holes) {
                val dx = h.x - px
                val dy = h.y - py
                val d2 = dx * dx + dy * dy
                if (d2 > h.reach * h.reach) continue
                val dist = sqrt(d2)
                if (dist < h.r + 10f) { die(DEATH_VOID); return }
                val a = min(3200f, 2.6e7f / d2)
                ax += dx / dist * a; ay += dy / dist * a
                if (rnd.nextFloat() < 0.5f) parts.emit(px, py, -vx * 0.1f, -vy * 0.1f, 0.4f, 3f, 0xFFB18CFF.toInt(), 2f)
            }
            vx += ax * dt; vy += ay * dt
            px += vx * dt; py += vy * dt
            emitSpark(); if (rnd.nextFloat() < 0.5f) emitSpark()

            // capture
            for (p in planets) {
                if (p === lastPlanet) continue
                val rx = px - p.x
                val ry = py - p.y
                val dist = sqrt(rx * rx + ry * ry)
                if (dist <= p.orbitCur) {
                    capture(p, rx, ry, dist)
                    break
                }
            }
            if (orbiting == null) {
                val outX = px < -40f || px > W + 40f
                val outY = py > camY + H + 60f || py < camY - 200f
                if (outX || outY || flyTime > 3.2f) { die(DEATH_LOST); return }
            }
        }

        if (!alive) return
        collideAsteroids()
        if (!alive) return
        collectPickups(dt)
        recordTrail()

        // camera
        val anchor = orbiting ?: lastPlanet
        if (anchor != null) {
            var target = anchor.y - H * 0.64f
            if (orbiting == null) target = min(target, py - H * 0.45f)
            if (target < camY) camY += (target - camY) * min(1f, dt * 3.2f)
            else camY += (target - camY) * min(1f, dt * 1.2f)
        }

        // keep the world populated and trimmed
        while (planets.last().y > camY - H * 1.2f) spawnNext()
        val cutoff = camY + H + 500f
        planets.removeAll { it.y > cutoff && it !== orbiting && it !== lastPlanet }
        pickups.removeAll { !it.alive || it.y > cutoff }
        holes.removeAll { it.y > cutoff + 300f }
    }

    private fun capture(p: Planet, rx: Float, ry: Float, dist: Float) {
        val speed = sqrt(vx * vx + vy * vy)
        val radial = abs((rx * vx + ry * vy) / (dist * speed))
        val cross = rx * vy - ry * vx
        dir = if (cross >= 0f) 1f else -1f
        ang = atan2(ry, rx)
        orbiting = p
        p.orbitCur = p.orbit.coerceAtMost(max(dist, p.r + 30f))
        placeOnOrbit(p)
        invuln = max(invuln, 0.25f)
        // clear asteroids sitting right where we landed
        for (k in 0 until p.asteroids) {
            if (!p.astAlive[k]) continue
            if (angleDist(p.asteroidAngle(k), ang) < 0.7f) {
                p.astAlive[k] = false
                val ax = p.x + cos(p.asteroidAngle(k)) * p.orbit
                val ay = p.y + sin(p.asteroidAngle(k)) * p.orbit
                burst(ax, ay, 16, 0xFFB0A090.toInt(), 300f)
            }
        }
        val perfect = radial < 0.5f
        if (!p.visited) {
            p.visited = true
            if (perfect) combo++ else combo = 0
            val gained = 1 + if (perfect) min(combo, 5) else 0
            score += gained
            scorePop = 1f
            if (perfect) {
                floatText(if (combo > 1) "${s.perfect} ×$combo" else s.perfect, px, py - 70f, 0xFFFFE066.toInt(), 64f + min(combo, 5) * 6f)
                if (store.sound) sfx.play(Sfx.PERFECT, 1f + min(combo, 8) * 0.07f)
                flash = 0.35f; flashColor = 0xFFFFE9A8.toInt()
                burst(px, py, 30, 0xFFFFE066.toInt(), 520f)
            } else {
                floatText("+$gained", px, py - 60f, Color.WHITE, 56f)
            }
            if (store.sound) sfx.play(Sfx.CAPTURE, 1f + min(score, 40) * 0.012f)
            haptic(HAPTIC_LIGHT)
            if (score >= 2 && !store.tutorialDone) store.tutorialDone = true
        } else if (store.sound) sfx.play(Sfx.CAPTURE, 0.8f, 0.6f)
        burst(px, py, 18, currentSkinColor(0.5f), 300f)
    }

    private fun collideAsteroids() {
        if (invuln > 0f) return
        for (p in planets) {
            if (p.asteroids == 0) continue
            val dyp = p.y - py
            if (abs(dyp) > p.orbit + 60f) continue
            for (k in 0 until p.asteroids) {
                if (!p.astAlive[k]) continue
                val a = p.asteroidAngle(k)
                val ax = p.x + cos(a) * p.orbit
                val ay = p.y + sin(a) * p.orbit
                val dx = ax - px
                val dy = ay - py
                if (dx * dx + dy * dy < 34f * 34f) {
                    p.astAlive[k] = false
                    burst(ax, ay, 20, 0xFFB0A090.toInt(), 350f)
                    die(DEATH_CRASH)
                    return
                }
            }
        }
    }

    private fun collectPickups(dt: Float) {
        for (pk in pickups) {
            if (!pk.alive) continue
            var dx = px - pk.x
            var dy = py - pk.y
            var d2 = dx * dx + dy * dy
            if (magnet > 0f && pk.kind == Pickup.STAR && d2 < 380f * 380f) {
                val d = sqrt(d2)
                val pull = min(d, 1300f * dt)
                pk.x += dx / d * pull; pk.y += dy / d * pull
                dx = px - pk.x; dy = py - pk.y; d2 = dx * dx + dy * dy
            }
            if (d2 < 46f * 46f) {
                pk.alive = false
                when (pk.kind) {
                    Pickup.STAR -> {
                        runStars++
                        store.stars = store.stars + 1
                        if (store.sound) sfx.play(Sfx.STAR, 1f + (runStars % 8) * 0.06f, 0.8f)
                        burst(pk.x, pk.y, 10, 0xFFFFD34D.toInt(), 260f)
                    }
                    Pickup.SHIELD -> {
                        shield = true
                        if (store.sound) sfx.play(Sfx.POWER)
                        floatText(s.shield, pk.x, pk.y - 50f, 0xFF7FF3FF.toInt(), 54f)
                        burst(pk.x, pk.y, 26, 0xFF7FF3FF.toInt(), 400f)
                    }
                    Pickup.MAGNET -> {
                        magnet = 10f
                        if (store.sound) sfx.play(Sfx.POWER)
                        floatText(s.magnet, pk.x, pk.y - 50f, 0xFFFF7EC8.toInt(), 54f)
                        burst(pk.x, pk.y, 26, 0xFFFF7EC8.toInt(), 400f)
                    }
                }
            }
        }
    }

    private fun die(reason: Int) {
        if (shield) {
            shield = false
            val p = orbiting ?: lastPlanet
            if (p != null) {
                if (store.sound) sfx.play(Sfx.SHIELD)
                burst(px, py, 30, 0xFF7FF3FF.toInt(), 500f)
                respawnAt(p)
                invuln = 1.5f
                floatText(s.shieldSaved, px, py - 80f, 0xFF7FF3FF.toInt(), 52f)
                haptic(HAPTIC_HEAVY)
                return
            }
        }
        alive = false
        lastPlanet = orbiting ?: lastPlanet
        deathReason = reason
        deathTimer = 0f
        orbiting = null
        combo = 0
        shake = 26f
        flash = 0.8f; flashColor = 0xFFFF6A4D.toInt()
        if (store.sound) sfx.play(Sfx.BOOM)
        haptic(HAPTIC_HEAVY)
        burst(px, py, 90, currentSkinColor(0.2f), 900f)
        burst(px, py, 40, Color.WHITE, 500f)
    }

    private fun recordTrail() {
        trailTick++
        if (trailTick % 2 != 0) return
        trailX[trailHead] = px
        trailY[trailHead] = py
        trailHead = (trailHead + 1) % TRAIL
        if (trailCount < TRAIL) trailCount++
    }

    private fun emitSpark() {
        val a = rnd.nextFloat() * TWO_PI
        val v = 20f + rnd.nextFloat() * 60f
        parts.emit(px, py, cos(a) * v, sin(a) * v, 0.35f + rnd.nextFloat() * 0.35f, 2.5f + rnd.nextFloat() * 3.5f, currentSkinColor(rnd.nextFloat()), 1f)
    }

    private fun burst(x: Float, y: Float, n: Int, color: Int, power: Float) {
        for (i in 0 until n) {
            val a = rnd.nextFloat() * TWO_PI
            val v = power * (0.2f + rnd.nextFloat() * 0.8f)
            parts.emit(x, y, cos(a) * v, sin(a) * v, 0.5f + rnd.nextFloat() * 0.6f, 3f + rnd.nextFloat() * 6f, color, 3f)
        }
    }

    private fun floatText(t: String, x: Float, y: Float, color: Int, size: Float) {
        var slot = texts[0]
        for (ft in texts) if (ft.life <= 0f) { slot = ft; break }
        slot.text = t; slot.x = x; slot.y = y; slot.life = 1.1f; slot.max = 1.1f; slot.color = color; slot.size = size
    }

    private fun updateTexts(dt: Float) {
        for (t in texts) if (t.life > 0f) { t.life -= dt; t.y -= 60f * dt }
    }

    private fun currentSkinColor(t: Float): Int {
        val sk = Skins.all[store.skin.coerceIn(0, Skins.all.size - 1)]
        if (sk.rainbow) return Color.HSVToColor(floatArrayOf(((uiTime * 120f + t * 200f) % 360f), 0.75f, 1f))
        return lerpColor(sk.trailB, sk.trailA, t)
    }

    // ================================================================= rendering

    fun render(c: Canvas) {
        c.save()
        c.translate(offX, 0f)
        c.scale(scale, scale)
        drawBackground(c)

        c.save()
        val sx = if (shake > 0) (rnd.nextFloat() - 0.5f) * shake else 0f
        val sy = if (shake > 0) (rnd.nextFloat() - 0.5f) * shake else 0f
        c.translate(sx, -camY + sy)
        drawWorld(c)
        c.restore()
        if (offX > 1f) {
            shaderPaint.shader = null
            shaderPaint.color = 0x14FFFFFF
            c.drawRect(-3f, 0f, 0f, H, shaderPaint)
            c.drawRect(W, 0f, W + 3f, H, shaderPaint)
        }

        if (flash > 0f) {
            paint.shader = null
            paint.color = withAlpha(flashColor, (flash * 110).toInt().coerceIn(0, 255))
            c.drawRect(vl, 0f, vr, H, paint)
        }

        for (b in allButtons) b.visible = false
        when (state) {
            MENU -> drawMenu(c)
            PLAY -> drawHud(c)
            PAUSE -> { drawHud(c); drawPause(c) }
            OVER -> drawOver(c)
            SHOP -> drawShop(c)
        }
        if (toastTime > 0f) {
            val a = min(1f, toastTime * 3f)
            drawPill(c, W / 2f, H * 0.5f, measure(textThin, toast, 46f) + 80f, 100f, withAlpha(0xFF000000.toInt(), (a * 190).toInt()))
            textThin.textSize = 46f
            textThin.color = withAlpha(Color.WHITE, (a * 255).toInt())
            c.drawText(toast, W / 2f, H * 0.5f + 16f, textThin)
        }
        c.restore()
    }

    private fun drawBackground(c: Canvas) {
        val phase = (max(0, score) / 20f).coerceAtMost(skyTop.size - 1.001f)
        val key = (phase * 32).toInt()
        if (key != bgKey || bgShader == null) {
            bgKey = key
            val i = phase.toInt()
            val f = phase - i
            val top = lerpColor(skyTop[i], skyTop[min(i + 1, skyTop.size - 1)], f)
            val bot = lerpColor(skyBot[i], skyBot[min(i + 1, skyBot.size - 1)], f)
            bgShader = LinearGradient(0f, 0f, 0f, H, top, bot, Shader.TileMode.CLAMP)
        }
        paint.shader = bgShader
        c.drawRect(vl, 0f, vr, H, paint)
        paint.shader = null

        // nebulae (very slow parallax)
        nebula?.let { neb ->
            val period = H * 2.2f
            for (i in 0 until 4) {
                val bmp = neb[i % neb.size]
                val baseY = i * period / 4f
                var y = (baseY - camY * 0.08f) % period
                if (y < 0) y += period
                y -= H * 0.4f
                val x = if (i % 2 == 0) W * 0.15f else W * 0.8f
                val size = 900f + (i % 3) * 200f
                rect.set(x - size / 2, y - size / 2, x + size / 2, y + size / 2)
                paint.alpha = 150
                c.drawBitmap(bmp, null, rect, paint)
                paint.alpha = 255
            }
        }

        // three-layer parallax starfield
        for (i in bgStarsX.indices) {
            val layer = i % 3
            val f = 0.1f + layer * 0.18f
            var y = (bgStarsY[i] * H - camY * f) % H
            if (y < 0) y += H
            val tw = 0.55f + 0.45f * sin(uiTime * (1.5f + layer) + bgStarsP[i])
            paint.color = withAlpha(Color.WHITE, (tw * (90 + layer * 60)).toInt())
            c.drawCircle(vl + bgStarsX[i] / W * (vr - vl), y, bgStarsS[i] * (0.6f + layer * 0.3f), paint)
        }
    }

    private fun drawWorld(c: Canvas) {
        val top = camY - 300f
        val bottom = camY + H + 300f
        val target = (orbiting ?: lastPlanet)?.let { nextTarget(it) }

        for (h in holes) if (h.y in top..bottom) drawHole(c, h)

        for (p in planets) {
            if (p.y + p.orbit < top || p.y - p.orbit > bottom) continue
            drawPlanet(c, p, p === target)
        }

        for (pk in pickups) {
            if (!pk.alive || pk.y < top || pk.y > bottom) continue
            drawPickup(c, pk)
        }

        // particles
        paint.shader = null
        for (i in 0 until parts.cap) {
            val l = parts.life[i]
            if (l <= 0f) continue
            val t = l / parts.max[i]
            paint.color = withAlpha(parts.color[i], (t * 255).toInt())
            c.drawCircle(parts.x[i], parts.y[i], parts.size[i] * (0.4f + 0.6f * t), paint)
        }

        if (alive) drawComet(c)

        for (ft in texts) {
            if (ft.life <= 0f) continue
            val t = ft.life / ft.max
            val pop = if (t > 0.85f) 1f + (t - 0.85f) * 3f else 1f
            text.textSize = ft.size * pop
            text.color = withAlpha(0xFF000000.toInt(), (t * 120).toInt())
            c.drawText(ft.text, ft.x + 3, ft.y + 4, text)
            text.color = withAlpha(ft.color, (min(1f, t * 2f) * 255).toInt())
            c.drawText(ft.text, ft.x, ft.y, text)
        }
    }

    private fun drawPlanet(c: Canvas, p: Planet, isTarget: Boolean) {
        val isCurrent = p === orbiting
        // orbit path made of drifting dots
        val r = p.orbitCur
        val n = (TWO_PI * r / 26f).toInt().coerceAtLeast(12)
        val decaying = isCurrent && p.warned
        val blink = if (decaying) (0.5f + 0.5f * sin(simTime * 18f)) else 1f
        val baseAlpha = when {
            isCurrent -> 150
            isTarget -> 120
            p.visited -> 40
            else -> 75
        }
        paint.shader = null
        paint.color = if (decaying) withAlpha(0xFFFF5A5A.toInt(), (baseAlpha * blink + 60).toInt().coerceAtMost(255))
        else withAlpha(if (isTarget) p.light else Color.WHITE, baseAlpha)
        val rot = simTime * 0.25f * (if (p.id % 2 == 0) 1 else -1)
        val dotR = if (isTarget) 3.4f else 2.6f
        for (k in 0 until n) {
            val a = rot + k * TWO_PI / n
            c.drawCircle(p.x + cos(a) * r, p.y + sin(a) * r, dotR, paint)
        }
        if (isTarget && !p.visited) {
            // gentle pulse ring on the next goal
            val pulse = (simTime * 0.8f) % 1f
            stroke.strokeWidth = 3f
            stroke.color = withAlpha(p.light, ((1f - pulse) * 90).toInt())
            c.drawCircle(p.x, p.y, r * (0.9f + pulse * 0.25f), stroke)
        }

        c.save()
        c.translate(p.x, p.y)
        paint.shader = p.glowShader
        c.drawCircle(0f, 0f, p.r * 2.4f, paint)
        paint.shader = null

        if (p.hasRing) {
            c.save(); c.rotate(p.ringTilt)
            rect.set(-p.r * 2f, -p.r * 0.5f, p.r * 2f, p.r * 0.5f)
            stroke.strokeWidth = p.r * 0.14f
            stroke.color = withAlpha(p.light, 120)
            c.drawArc(rect, 180f, 180f, false, stroke)
            c.restore()
        }
        paint.shader = p.bodyShader
        c.drawCircle(0f, 0f, p.r, paint)
        paint.shader = null
        c.save(); c.rotate(p.spin * 57.3f)
        paint.color = withAlpha(darken(p.dark, 0.6f), 110)
        for (k in 0 until p.craters.size / 3) {
            c.drawCircle(p.craters[k * 3], p.craters[k * 3 + 1], p.craters[k * 3 + 2], paint)
        }
        c.restore()
        // rim highlight
        stroke.strokeWidth = 2.5f
        stroke.color = withAlpha(p.light, 90)
        c.drawCircle(0f, 0f, p.r - 1f, stroke)
        if (p.hasRing) {
            c.save(); c.rotate(p.ringTilt)
            rect.set(-p.r * 2f, -p.r * 0.5f, p.r * 2f, p.r * 0.5f)
            stroke.strokeWidth = p.r * 0.14f
            stroke.color = withAlpha(p.light, 200)
            c.drawArc(rect, 0f, 180f, false, stroke)
            c.restore()
        }
        c.restore()

        // asteroids
        for (k in 0 until p.asteroids) {
            if (!p.astAlive[k]) continue
            val a = p.asteroidAngle(k)
            val ax = p.x + cos(a) * p.orbit
            val ay = p.y + sin(a) * p.orbit
            drawAsteroid(c, ax, ay, 22f, a * 2f + simTime)
        }
    }

    private fun drawAsteroid(c: Canvas, x: Float, y: Float, r: Float, rot: Float) {
        tmpPath.reset()
        for (i in 0 until 8) {
            val a = rot + i * TWO_PI / 8
            val rr = r * (0.78f + 0.22f * sin(i * 2.3f + 1.1f))
            if (i == 0) tmpPath.moveTo(x + cos(a) * rr, y + sin(a) * rr) else tmpPath.lineTo(x + cos(a) * rr, y + sin(a) * rr)
        }
        tmpPath.close()
        paint.shader = null
        paint.color = 0x55FF4D4D
        c.drawCircle(x, y, r * 1.5f, paint)
        paint.color = 0xFF8A7A6E.toInt()
        c.drawPath(tmpPath, paint)
        stroke.strokeWidth = 3f
        stroke.color = 0xFFC9B8A8.toInt()
        c.drawPath(tmpPath, stroke)
        paint.color = 0xFF5E5249.toInt()
        c.drawCircle(x + r * 0.25f, y - r * 0.15f, r * 0.22f, paint)
    }

    private fun drawHole(c: Canvas, h: Hole) {
        // swirling accretion disc
        for (i in 0 until 3) {
            stroke.strokeWidth = 5f - i
            stroke.color = withAlpha(if (i == 1) 0xFFFF7EC8.toInt() else 0xFFB18CFF.toInt(), 120 - i * 30)
            val rr = h.r * (1.7f + i * 0.7f)
            rect.set(h.x - rr, h.y - rr, h.x + rr, h.y + rr)
            val start = (h.spin * (60f + i * 30f)) % 360f
            c.drawArc(rect, start, 220f, false, stroke)
        }
        stroke.strokeWidth = 2f
        stroke.color = withAlpha(0xFFB18CFF.toInt(), 30)
        c.drawCircle(h.x, h.y, h.reach * 0.6f, stroke)
        paint.shader = null
        paint.color = 0xFF000000.toInt()
        c.drawCircle(h.x, h.y, h.r, paint)
        stroke.strokeWidth = 3f
        stroke.color = 0xFFE7D2FF.toInt()
        c.drawCircle(h.x, h.y, h.r, stroke)
    }

    private fun drawPickup(c: Canvas, pk: Pickup) {
        val bob = sin(simTime * 3f + pk.phase) * 6f
        when (pk.kind) {
            Pickup.STAR -> {
                val tw = 0.5f + 0.5f * sin(simTime * 4f + pk.phase)
                drawStar(c, pk.x, pk.y + bob, 21f + tw * 4f, simTime * 1.5f + pk.phase, 0xFFFFD34D.toInt())
                paint.color = withAlpha(Color.WHITE, (120 + tw * 120).toInt())
                c.drawCircle(pk.x - 5f, pk.y + bob - 6f, 3.5f, paint)
            }
            Pickup.SHIELD, Pickup.MAGNET -> {
                val col = if (pk.kind == Pickup.SHIELD) 0xFF7FF3FF.toInt() else 0xFFFF7EC8.toInt()
                val pulse = 1f + 0.08f * sin(simTime * 6f)
                paint.color = withAlpha(col, 60)
                c.drawCircle(pk.x, pk.y + bob, 46f * pulse, paint)
                stroke.strokeWidth = 4f
                stroke.color = col
                c.drawCircle(pk.x, pk.y + bob, 34f * pulse, stroke)
                if (pk.kind == Pickup.SHIELD) {
                    tmpPath.reset()
                    val x = pk.x; val y = pk.y + bob
                    tmpPath.moveTo(x, y - 20); tmpPath.lineTo(x + 16, y - 12); tmpPath.lineTo(x + 13, y + 6)
                    tmpPath.lineTo(x, y + 20); tmpPath.lineTo(x - 13, y + 6); tmpPath.lineTo(x - 16, y - 12); tmpPath.close()
                    paint.color = col
                    c.drawPath(tmpPath, paint)
                } else {
                    stroke.strokeWidth = 9f
                    stroke.strokeCap = Paint.Cap.BUTT
                    rect.set(pk.x - 14, pk.y + bob - 16, pk.x + 14, pk.y + bob + 12)
                    c.drawArc(rect, 0f, 180f, false, stroke)
                    stroke.color = Color.WHITE
                    c.drawLine(pk.x - 14 + 0f, pk.y + bob - 4, pk.x - 14 + 0f, pk.y + bob - 14, stroke)
                    c.drawLine(pk.x + 14, pk.y + bob - 4, pk.x + 14, pk.y + bob - 14, stroke)
                }
            }
        }
    }

    private fun drawComet(c: Canvas) {
        val sk = Skins.all[store.skin.coerceIn(0, Skins.all.size - 1)]
        paint.shader = null
        // trail: oldest -> newest
        for (i in 0 until trailCount) {
            val idx = (trailHead - trailCount + i + TRAIL * 2) % TRAIL
            val t = (i + 1f) / trailCount
            paint.color = withAlpha(currentSkinColor(t), (t * t * 230).toInt())
            c.drawCircle(trailX[idx], trailY[idx], 3f + 13f * t, paint)
        }
        if (headShaderSkin != store.skin || headShader == null) {
            headShaderSkin = store.skin
            headShader = RadialGradient(0f, 0f, 46f, intArrayOf(withAlpha(sk.trailA, 200), withAlpha(sk.trailA, 0)), null, Shader.TileMode.CLAMP)
        }
        c.save(); c.translate(px, py)
        paint.shader = headShader
        if (sk.rainbow) paint.shader = null.also { paint.color = withAlpha(currentSkinColor(1f), 110) }
        c.drawCircle(0f, 0f, 46f, paint)
        paint.shader = null
        paint.color = sk.head
        c.drawCircle(0f, 0f, 14f, paint)
        if (shield) {
            stroke.strokeWidth = 4f
            stroke.color = withAlpha(0xFF7FF3FF.toInt(), (170 + 60 * sin(simTime * 8f)).toInt())
            c.drawCircle(0f, 0f, 34f, stroke)
        }
        if (invuln > 0f && (simTime * 12f).toInt() % 2 == 0) {
            stroke.strokeWidth = 3f
            stroke.color = 0x99FFFFFF.toInt()
            c.drawCircle(0f, 0f, 26f, stroke)
        }
        if (magnet > 0f) {
            stroke.strokeWidth = 3f
            stroke.color = withAlpha(0xFFFF7EC8.toInt(), 120)
            val rr = 60f + 20f * ((simTime * 2f) % 1f)
            c.drawCircle(0f, 0f, rr, stroke)
        }
        c.restore()
    }

    // ---------------------------------------------------------------- HUD & screens

    private fun drawHud(c: Canvas) {
        if (debugHideHud) return
        val y = safeTop + 100f
        text.textSize = 120f * (1f + scorePop * 0.25f)
        val shown = (if (score - displayScore < 0.5f) score else displayScore.toInt()).toString()
        text.color = 0x66000000
        c.drawText(shown, W / 2 + 4, y + 6, text)
        text.color = Color.WHITE
        c.drawText(shown, W / 2, y, text)

        // stars
        drawStar(c, 64f, safeTop + 42f, 26f, 0f, 0xFFFFD34D.toInt())
        textThin.textAlign = Paint.Align.LEFT
        textThin.textSize = 46f
        textThin.color = Color.WHITE
        c.drawText(store.stars.toString(), 102f, safeTop + 58f, textThin)
        textThin.textAlign = Paint.Align.CENTER

        if (combo > 1) {
            text.textSize = 40f
            text.color = 0xFFFFE066.toInt()
            c.drawText("×$combo", W / 2, y + 56f, text)
        }

        // pause button
        bPause.set(W - 70f, safeTop + 40f, 90f, 90f)
        paint.color = withAlpha(Color.WHITE, 40)
        c.drawCircle(bPause.cx, bPause.cy, 44f, paint)
        paint.color = Color.WHITE
        c.drawRoundRect(bPause.cx - 15, bPause.cy - 18, bPause.cx - 5, bPause.cy + 18, 3f, 3f, paint)
        c.drawRoundRect(bPause.cx + 5, bPause.cy - 18, bPause.cx + 15, bPause.cy + 18, 3f, 3f, paint)

        // power-ups
        var iy = safeTop + 120f
        if (shield) {
            stroke.strokeWidth = 4f; stroke.color = 0xFF7FF3FF.toInt()
            c.drawCircle(64f, iy, 20f, stroke)
            iy += 56f
        }
        if (magnet > 0f) {
            stroke.strokeWidth = 6f; stroke.color = 0xFFFF7EC8.toInt()
            rect.set(44f, iy - 20f, 84f, iy + 20f)
            c.drawArc(rect, -90f, 360f * (magnet / 10f), false, stroke)
        }

        // tutorial
        val o = orbiting
        if (alive && o != null && score == 0 && o.id == 0 && (!store.tutorialDone || stateTime < 2.5f)) {
            val a = 0.6f + 0.4f * sin(uiTime * 5f)
            text.textSize = 64f
            text.color = withAlpha(Color.WHITE, (a * 255).toInt())
            c.drawText(s.tapToLaunch, W / 2, H - safeBottom - 160f, text)
            val ripple = (uiTime * 1.2f) % 1f
            stroke.strokeWidth = 5f
            stroke.color = withAlpha(Color.WHITE, ((1 - ripple) * 200).toInt())
            c.drawCircle(W / 2, H - safeBottom - 280f, 30f + ripple * 60f, stroke)
            paint.color = withAlpha(Color.WHITE, 200)
            c.drawCircle(W / 2, H - safeBottom - 280f, 22f, paint)
        }
        if (hintDecay > 0f) {
            textThin.textSize = 48f
            textThin.color = withAlpha(0xFFFF8A8A.toInt(), (min(1f, hintDecay) * 255).toInt())
            c.drawText(s.hintDecay, W / 2, H - safeBottom - 120f, textThin)
        }
    }

    private var menuShade: Shader? = null
    private var menuShadeH = 0f

    private fun drawMenu(c: Canvas) {
        val a = min(1f, stateTime * 2.5f)
        if (menuShade == null || menuShadeH != H) {
            menuShadeH = H
            menuShade = LinearGradient(0f, 0f, 0f, H * 0.62f, intArrayOf(0xE6060A1C.toInt(), 0xB3060A1C.toInt(), 0x00060A1C), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        }
        paint.shader = menuShade
        c.drawRect(vl, 0f, vr, H * 0.62f, paint)
        paint.shader = null
        // title
        val ty = max(safeTop + 220f, H * 0.17f)
        val wob = sin(uiTime * 1.6f) * 6f
        text.color = withAlpha(Color.WHITE, (a * 255).toInt())
        fitText(text, s.title1, 170f, 900f)
        drawGlowText(c, s.title1, W / 2, ty + wob, 0xFFFFB347.toInt(), a)
        fitText(text, s.title2, 170f, 900f)
        drawGlowText(c, s.title2, W / 2, ty + 160f + wob, 0xFF7FE7FF.toInt(), a)
        textThin.textSize = 42f
        textThin.color = withAlpha(0xFFCCD6FF.toInt(), (a * 220).toInt())
        fitText(textThin, s.tagline, 42f, 880f)
        c.drawText(s.tagline, W / 2, ty + 250f, textThin)

        // best + stars
        drawStar(c, 64f, safeTop + 42f, 26f, 0f, 0xFFFFD34D.toInt())
        textThin.textAlign = Paint.Align.LEFT
        textThin.textSize = 46f
        textThin.color = Color.WHITE
        c.drawText(store.stars.toString(), 102f, safeTop + 58f, textThin)
        textThin.textAlign = Paint.Align.CENTER
        if (store.best > 0) {
            textThin.textSize = 46f
            textThin.color = 0xFFFFE066.toInt()
            c.drawText("${s.best}  ${store.best}", W / 2, ty + 330f, textThin)
        }

        // gift
        if (giftAvailable()) {
            bGift.set(W - 80f, safeTop + 50f, 110f, 110f)
            drawGift(c, bGift.cx, bGift.cy, 1f + 0.08f * sin(uiTime * 6f))
            textThin.textSize = 28f
            textThin.color = 0xFFFFD34D.toInt()
            c.drawText(s.dailyGift, bGift.cx - 20f, bGift.cy + 80f, textThin)
        }

        // buttons
        val by = H - safeBottom - 330f
        bPlay.set(W / 2, by, 560f, 150f)
        val pulse = 1f + 0.03f * sin(uiTime * 4f)
        drawButton(c, bPlay, s.play, 0xFFFF8A3D.toInt(), 0xFFFF3D6E.toInt(), 76f, pulse)
        bShop.set(W / 2 - 110f, by + 175f, 340f, 110f)
        drawButton(c, bShop, s.shop, 0xFF4D7CFF.toInt(), 0xFF7A4DFF.toInt(), 50f, 1f)
        bSound.set(W / 2 + 130f, by + 175f, 110f, 110f)
        drawRoundToggle(c, bSound, store.sound, 0)
        bMusic.set(W / 2 + 255f, by + 175f, 110f, 110f)
        drawRoundToggle(c, bMusic, store.music, 1)
    }

    private fun drawPause(c: Canvas) {
        paint.shader = null
        paint.color = 0xB0050818.toInt()
        c.drawRect(vl, 0f, vr, H, paint)
        text.textSize = 120f
        text.color = Color.WHITE
        c.drawText(s.paused, W / 2, H * 0.32f, text)
        bResume.set(W / 2, H * 0.48f, 520f, 140f)
        drawButton(c, bResume, s.resume, 0xFFFF8A3D.toInt(), 0xFFFF3D6E.toInt(), 64f, 1f)
        bMenu.set(W / 2, H * 0.48f + 170f, 400f, 110f)
        drawButton(c, bMenu, s.menu, 0xFF4D7CFF.toInt(), 0xFF7A4DFF.toInt(), 50f, 1f)
        bSound.set(W / 2 - 70f, H * 0.48f + 320f, 110f, 110f)
        drawRoundToggle(c, bSound, store.sound, 0)
        bMusic.set(W / 2 + 70f, H * 0.48f + 320f, 110f, 110f)
        drawRoundToggle(c, bMusic, store.music, 1)
    }

    private fun drawOver(c: Canvas) {
        val a = min(1f, stateTime * 3f)
        paint.shader = null
        paint.color = withAlpha(0xFF050818.toInt(), (a * 175).toInt())
        c.drawRect(vl, 0f, vr, H, paint)
        val slide = (1f - easeOut(min(1f, stateTime * 2.2f))) * 200f
        val cy = H * 0.42f + slide

        textThin.textSize = 46f
        textThin.color = withAlpha(0xFFFF8A8A.toInt(), (a * 255).toInt())
        val reason = when (deathReason) { DEATH_VOID -> s.swallowed; DEATH_CRASH -> s.crashed; else -> s.lost }
        fitText(textThin, reason, 46f, 900f)
        c.drawText(reason, W / 2, cy - 330f, textThin)

        textThin.textSize = 44f
        textThin.color = withAlpha(0xFFCCD6FF.toInt(), (a * 255).toInt())
        c.drawText(s.score, W / 2, cy - 250f, textThin)
        text.textSize = 220f
        text.color = withAlpha(Color.WHITE, (a * 255).toInt())
        c.drawText(score.toString(), W / 2, cy - 60f, text)

        if (newBest) {
            val p = 1f + 0.06f * sin(uiTime * 8f)
            text.textSize = 70f * p
            drawGlowText(c, s.newBest, W / 2, cy + 40f, 0xFFFFD34D.toInt(), a)
            if ((uiTime * 10).toInt() % 3 == 0 && parts.life.count { it > 0 } < 300) {
                val x = rnd.nextFloat() * W
                parts.emit(x, camY - 20f, (rnd.nextFloat() - 0.5f) * 100f, 300f + rnd.nextFloat() * 300f, 2.5f, 6f + rnd.nextFloat() * 6f,
                    palettes[rnd.nextInt(palettes.size)][0], 0.2f)
            }
        } else {
            textThin.textSize = 48f
            textThin.color = withAlpha(0xFFFFE066.toInt(), (a * 255).toInt())
            c.drawText("${s.best}  ${store.best}", W / 2, cy + 30f, textThin)
        }
        if (runStars > 0) {
            textThin.textSize = 46f
            textThin.color = withAlpha(0xFFFFD34D.toInt(), (a * 255).toInt())
            c.drawText(String.format(s.starsEarned, runStars), W / 2, cy + 100f, textThin)
        }

        bRetry.set(W / 2, cy + 230f, 560f, 150f)
        drawButton(c, bRetry, s.retry, 0xFFFF8A3D.toInt(), 0xFFFF3D6E.toInt(), 72f, 1f + 0.03f * sin(uiTime * 4f))
        var ny = cy + 410f
        if (!continued) {
            bContinue.set(W / 2, ny, 480f, 120f)
            bContinue.enabled = store.stars >= CONTINUE_COST
            drawButton(c, bContinue, String.format(s.continueFor, CONTINUE_COST),
                if (bContinue.enabled) 0xFF2BD9A8.toInt() else 0xFF4A5068.toInt(),
                if (bContinue.enabled) 0xFF1A8CFF.toInt() else 0xFF343A50.toInt(), 50f, 1f)
            ny += 150f
        }
        bMenu.set(W / 2, ny, 360f, 105f)
        drawButton(c, bMenu, s.menu, 0xFF4D7CFF.toInt(), 0xFF7A4DFF.toInt(), 46f, 1f)
    }

    private fun drawShop(c: Canvas) {
        paint.shader = null
        paint.color = 0xC0050818.toInt()
        c.drawRect(vl, 0f, vr, H, paint)
        text.textSize = 96f
        text.color = Color.WHITE
        c.drawText(s.shop, W / 2, safeTop + 120f, text)
        drawStar(c, W / 2 - 60f, safeTop + 195f, 26f, uiTime, 0xFFFFD34D.toInt())
        textThin.textAlign = Paint.Align.LEFT
        textThin.textSize = 52f
        textThin.color = Color.WHITE
        c.drawText(store.stars.toString(), W / 2 - 22f, safeTop + 213f, textThin)
        textThin.textAlign = Paint.Align.CENTER

        val gridTop = safeTop + 260f
        val gridBottom = H - safeBottom - 170f
        val rows = 4
        val cardH = min(250f, (gridBottom - gridTop) / rows - 20f)
        val cardW = 440f
        for (i in Skins.all.indices) {
            val col = i % 2
            val row = i / 2
            val sk = Skins.all[i]
            val cx = W / 2 + (if (col == 0) -235f else 235f)
            val cy = gridTop + cardH / 2 + row * (cardH + 20f)
            val shakeX = if (shopShake == i && shopShakeT > 0) sin(shopShakeT * 60f) * 14f * shopShakeT / 0.4f else 0f
            val card = skinCards[i]
            card.set(cx + shakeX, cy, cardW, cardH)
            val owned = store.ownedSkins and (1 shl i) != 0
            val selected = store.skin == i
            val sc = 1f - card.press * 0.05f
            c.save(); c.scale(sc, sc, card.cx, card.cy)
            rect.set(card.x, card.y, card.x + card.w, card.y + card.h)
            paint.color = if (selected) 0x40FFFFFF else 0x22FFFFFF
            c.drawRoundRect(rect, 36f, 36f, paint)
            stroke.strokeWidth = if (selected) 5f else 2f
            stroke.color = if (selected) sk.trailA else 0x44FFFFFF
            c.drawRoundRect(rect, 36f, 36f, stroke)

            // preview: little comet with a curved trail
            val pvx = card.x + 110f
            val pvy = card.cy
            val r0 = min(60f, cardH * 0.3f)
            for (k in 0 until 14) {
                val t = (k + 1) / 14f
                val aa = uiTime * 2.2f + i - (1f - t) * 2.4f
                val col2 = if (sk.rainbow) Color.HSVToColor(floatArrayOf((uiTime * 120f + t * 200f) % 360f, 0.75f, 1f)) else lerpColor(sk.trailB, sk.trailA, t)
                paint.color = withAlpha(col2, (t * t * 230).toInt())
                c.drawCircle(pvx + cos(aa) * r0, pvy + sin(aa) * r0, 2f + 9f * t, paint)
            }
            val ha = uiTime * 2.2f + i
            paint.color = sk.head
            c.drawCircle(pvx + cos(ha) * r0, pvy + sin(ha) * r0, 10f, paint)

            textThin.textSize = 44f
            textThin.color = Color.WHITE
            val name = s.skinNames.getOrElse(i) { "" }
            val tx = card.x + 310f
            fitText(textThin, name, 44f, 240f)
            c.drawText(name, tx, card.cy - 6f, textThin)
            textThin.textSize = 34f
            when {
                selected -> { textThin.color = sk.trailA; c.drawText(s.selected, tx, card.cy + 46f, textThin) }
                owned -> { textThin.color = 0xFFAAB4D6.toInt(); c.drawText(s.owned, tx, card.cy + 46f, textThin) }
                else -> {
                    val afford = store.stars >= sk.price
                    textThin.color = if (afford) 0xFFFFD34D.toInt() else 0xFF8088A0.toInt()
                    drawStar(c, tx - 50f, card.cy + 34f, 16f, 0f, textThin.color)
                    textThin.textAlign = Paint.Align.LEFT
                    textThin.textSize = 40f
                    c.drawText(sk.price.toString(), tx - 26f, card.cy + 48f, textThin)
                    textThin.textAlign = Paint.Align.CENTER
                }
            }
            c.restore()
        }
        bBack.set(W / 2, H - safeBottom - 80f, 380f, 110f)
        drawButton(c, bBack, s.back, 0xFF4D7CFF.toInt(), 0xFF7A4DFF.toInt(), 48f, 1f)
    }

    // ---------------------------------------------------------------- drawing helpers

    private fun drawButton(c: Canvas, b: Btn, label: String, c1: Int, c2: Int, size: Float, pulse: Float) {
        val sc = pulse * (1f - b.press * 0.08f)
        c.save(); c.scale(sc, sc, b.cx, b.cy)
        rect.set(b.x, b.y + 10f, b.x + b.w, b.y + b.h + 10f)
        paint.shader = null
        paint.color = 0x55000000
        c.drawRoundRect(rect, b.h / 2, b.h / 2, paint)
        rect.set(b.x, b.y, b.x + b.w, b.y + b.h)
        shaderPaint.shader = LinearGradient(b.x, b.y, b.x + b.w, b.y + b.h, c1, c2, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, b.h / 2, b.h / 2, shaderPaint)
        paint.color = 0x33FFFFFF
        rect.set(b.x + 14f, b.y + 8f, b.x + b.w - 14f, b.y + b.h * 0.48f)
        c.drawRoundRect(rect, b.h / 3, b.h / 3, paint)
        text.color = Color.WHITE
        fitText(text, label, size, b.w - 60f)
        val fm = text.fontMetrics
        c.drawText(label, b.cx, b.cy - (fm.ascent + fm.descent) / 2, text)
        c.restore()
    }

    private fun drawRoundToggle(c: Canvas, b: Btn, on: Boolean, icon: Int) {
        val sc = 1f - b.press * 0.1f
        c.save(); c.scale(sc, sc, b.cx, b.cy)
        paint.shader = null
        paint.color = if (on) 0x44FFFFFF else 0x22FFFFFF
        c.drawCircle(b.cx, b.cy, b.w / 2, paint)
        val col = if (on) Color.WHITE else 0xFF707890.toInt()
        paint.color = col
        stroke.color = col
        stroke.strokeWidth = 5f
        stroke.strokeCap = Paint.Cap.ROUND
        val x = b.cx; val y = b.cy
        if (icon == 0) {
            tmpPath.reset()
            tmpPath.moveTo(x - 24, y - 9); tmpPath.lineTo(x - 12, y - 9); tmpPath.lineTo(x + 2, y - 22)
            tmpPath.lineTo(x + 2, y + 22); tmpPath.lineTo(x - 12, y + 9); tmpPath.lineTo(x - 24, y + 9); tmpPath.close()
            c.drawPath(tmpPath, paint)
            if (on) {
                rect.set(x - 4, y - 14, x + 16, y + 14); c.drawArc(rect, -50f, 100f, false, stroke)
                rect.set(x - 6, y - 24, x + 26, y + 24); c.drawArc(rect, -50f, 100f, false, stroke)
            }
        } else {
            c.drawCircle(x - 12, y + 14, 8f, paint)
            c.drawCircle(x + 14, y + 8, 8f, paint)
            c.drawLine(x - 5, y + 14, x - 5, y - 18, stroke)
            c.drawLine(x + 21, y + 8, x + 21, y - 24, stroke)
            c.drawLine(x - 5, y - 18, x + 21, y - 24, stroke)
        }
        if (!on) {
            stroke.color = 0xFFFF5A5A.toInt()
            c.drawLine(x - 26, y - 26, x + 26, y + 26, stroke)
        }
        stroke.strokeCap = Paint.Cap.BUTT
        c.restore()
    }

    private fun drawGift(c: Canvas, x: Float, y: Float, sc: Float) {
        c.save(); c.scale(sc, sc, x, y)
        paint.shader = null
        paint.color = 0x30FFFFFF
        c.drawCircle(x, y, 58f, paint)
        paint.color = 0xFFFF3D6E.toInt()
        c.drawRoundRect(x - 34, y - 12, x + 34, y + 34, 8f, 8f, paint)
        paint.color = 0xFFFF6E91.toInt()
        c.drawRoundRect(x - 40, y - 26, x + 40, y - 8, 6f, 6f, paint)
        paint.color = 0xFFFFD34D.toInt()
        c.drawRect(x - 7, y - 26, x + 7, y + 34, paint)
        c.drawCircle(x - 14, y - 34, 11f, paint)
        c.drawCircle(x + 14, y - 34, 11f, paint)
        c.restore()
    }

    private fun drawGlowText(c: Canvas, t: String, x: Float, y: Float, glow: Int, a: Float) {
        val base = text.textSize
        text.color = withAlpha(glow, (a * 60).toInt())
        c.drawText(t, x, y + 10f, text)
        text.color = withAlpha(darken(glow, 0.45f), (a * 255).toInt())
        c.drawText(t, x + 5f, y + 7f, text)
        text.color = withAlpha(Color.WHITE, (a * 255).toInt())
        c.drawText(t, x, y, text)
        text.textSize = base
    }

    private fun drawPill(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, color: Int) {
        paint.shader = null
        paint.color = color
        rect.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        c.drawRoundRect(rect, h / 2, h / 2, paint)
    }

    private fun buildStarPath() {
        starPath.reset()
        for (i in 0 until 10) {
            val a = -PI.toFloat() / 2 + i * PI.toFloat() / 5
            val r = if (i % 2 == 0) 1f else 0.45f
            if (i == 0) starPath.moveTo(cos(a) * r, sin(a) * r) else starPath.lineTo(cos(a) * r, sin(a) * r)
        }
        starPath.close()
    }

    private fun drawStar(c: Canvas, x: Float, y: Float, r: Float, rot: Float, color: Int) {
        c.save()
        c.translate(x, y)
        c.rotate(sin(rot) * 15f)
        c.scale(r, r)
        paint.shader = null
        paint.color = color
        c.drawPath(starPath, paint)
        c.restore()
    }

    private fun makeNebula(color: Int): Bitmap {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(size / 2f, size / 2f, size / 2f,
            intArrayOf(withAlpha(color, 70), withAlpha(color, 25), withAlpha(color, 0)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(size / 2f, size / 2f, size / 2f, p)
        return bmp
    }

    private fun fitText(p: Paint, t: String, size: Float, maxW: Float) {
        p.textSize = size
        val w = p.measureText(t)
        if (w > maxW) p.textSize = size * maxW / w
    }

    private fun measure(p: Paint, t: String, size: Float): Float {
        p.textSize = size
        return p.measureText(t)
    }

    private fun angleDist(a: Float, b: Float): Float {
        var d = (a - b) % TWO_PI
        if (d < -PI) d += TWO_PI
        if (d > PI) d -= TWO_PI
        return abs(d)
    }
}

internal fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
internal fun easeOut(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)
internal fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)
internal fun darken(c: Int, f: Float): Int {
    val r = ((c shr 16 and 0xFF) * f).toInt()
    val g = ((c shr 8 and 0xFF) * f).toInt()
    val b = ((c and 0xFF) * f).toInt()
    return (c and -0x1000000) or (r shl 16) or (g shl 8) or b
}
internal fun lerpColor(a: Int, b: Int, t: Float): Int {
    val tt = t.coerceIn(0f, 1f)
    val aa = ((a ushr 24) + (((b ushr 24) - (a ushr 24)) * tt)).toInt()
    val r = ((a shr 16 and 0xFF) + (((b shr 16 and 0xFF) - (a shr 16 and 0xFF)) * tt)).toInt()
    val g = ((a shr 8 and 0xFF) + (((b shr 8 and 0xFF) - (a shr 8 and 0xFF)) * tt)).toInt()
    val bl = ((a and 0xFF) + (((b and 0xFF) - (a and 0xFF)) * tt)).toInt()
    return (aa shl 24) or (r shl 16) or (g shl 8) or bl
}
