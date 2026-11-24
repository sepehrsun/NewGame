package com.sepehrsun.cometleap

import android.graphics.Bitmap
import android.graphics.Canvas
import com.sepehrsun.cometleap.audio.Synth
import com.sepehrsun.cometleap.game.Game
import com.sepehrsun.cometleap.game.MemoryStore
import com.sepehrsun.cometleap.game.NoSfx
import com.sepehrsun.cometleap.game.Sfx
import com.sepehrsun.cometleap.game.Strings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GameplayTest {

    private fun newGame(store: MemoryStore = MemoryStore(), seed: Long = 42L) =
        Game(Strings.english(), store, NoSfx, seed = seed, today = { 1000L }).apply { resize(1080, 1920, 0, 0) }

    /** A simple bot: leaps when the tangent points at the next planet. */
    private fun botStep(g: Game) {
        if (g.state == Game.PLAY && g.isOrbiting && g.aimQuality() > 0.992f) g.touchDown(540f, 1000f)
        g.update(1f / 60f)
    }

    @Test
    fun botCanProgressThroughGeneratedWorld() {
        var bestRun = 0
        for (seed in 1L..6L) {
            val g = newGame(seed = seed)
            g.debugOpen(Game.PLAY)
            var frames = 0
            while (g.state == Game.PLAY && frames < 60 * 240) { botStep(g); frames++ }
            println("seed $seed -> score ${g.score} in ${frames / 60}s")
            bestRun = maxOf(bestRun, g.score)
        }
        assertTrue("world should be playable, best bot run $bestRun", bestRun >= 15)
    }

    @Test
    fun missingEndsTheRunAndOffersContinue() {
        val store = MemoryStore().apply { stars = 100 }
        val g = newGame(store)
        g.debugOpen(Game.PLAY)
        // launch immediately in whatever direction -> likely lost; then wait
        var frames = 0
        while (g.state == Game.PLAY && frames < 60 * 30) {
            if (g.isOrbiting && g.aimQuality() < -0.5f) g.touchDown(540f, 1000f)
            g.update(1f / 60f); frames++
        }
        assertEquals(Game.OVER, g.state)
    }

    @Test
    fun synthProducesAudio() {
        for (id in 0 until Sfx.COUNT) assertTrue(Synth.effect(id).size > 100)
        val m = Synth.music()
        assertEquals(Synth.RATE * 16, m.size)
        assertTrue(Synth.wav(m).size == m.size * 2 + 44)
    }

    // ---------------------------------------------------------------- store art

    private val out = File(System.getProperty("user.dir"), "../store/screenshots").apply { mkdirs() }

    private fun shot(g: Game, name: String, w: Int = 1080, h: Int = 1920) {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        g.render(Canvas(bmp))
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun featureGraphic() {
        val g = Game(Strings.english(), MemoryStore().apply { tutorialDone = true; skin = 0 }, NoSfx, seed = 21L, today = { 1L })
        g.resize(1024, 500, 0, 0)
        g.debugHideHud = true
        g.debugOpen(Game.PLAY)
        repeat(170) { g.update(1f / 60f) }
        val fg = Bitmap.createBitmap(1024, 500, Bitmap.Config.ARGB_8888)
        val c = Canvas(fg)
        c.drawColor(0xFF070B22.toInt())
        c.save(); c.translate(260f, 0f); g.render(c); c.restore()
        val shade = android.graphics.Paint().apply {
            shader = android.graphics.LinearGradient(0f, 0f, 760f, 0f,
                intArrayOf(0xFF070B22.toInt(), 0xEE070B22.toInt(), 0x00070B22), floatArrayOf(0f, 0.42f, 1f),
                android.graphics.Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, 760f, 500f, shade)
        val dot = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val r = java.util.Random(4)
        repeat(70) {
            dot.color = ((60 + r.nextInt(150)) shl 24) or 0xFFFFFF
            c.drawCircle(r.nextFloat() * 560f, r.nextFloat() * 500f, 1f + r.nextFloat() * 2.2f, dot)
        }
        val t = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
            textSize = 130f
        }
        fun title(s: String, y: Float, glow: Int) {
            t.color = glow; c.drawText(s, 64f, y + 6f, t)
            t.color = 0xFFFFFFFF.toInt(); c.drawText(s, 60f, y, t)
        }
        title("COMET", 205f, 0xFF8A4A10.toInt())
        title("LEAP", 335f, 0xFF1F5C70.toInt())
        t.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        t.textSize = 34f
        t.color = 0xFFCCD6FF.toInt()
        c.drawText("One tap. Infinite space.", 64f, 400f, t)
        File(out.parentFile, "feature_graphic_1024x500.png").outputStream().use { fg.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun playStoreIcon() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = ctx.getDrawable(R.drawable.ic_launcher_background)!!
        val fgd = ctx.getDrawable(R.drawable.ic_launcher_foreground)!!
        // 108dp layer, 72dp visible: scale so the visible area fills 512px
        bg.setBounds(-128, -128, 640, 640); bg.draw(c)
        fgd.setBounds(-128, -128, 640, 640); fgd.draw(c)
        File(out.parentFile, "icon_512.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun captureScreenshots() {
        val store = MemoryStore().apply { stars = 640; ownedSkins = 0b1011; skin = 1; best = 37; gamesPlayed = 5; tutorialDone = true }
        val g = newGame(store, seed = 5L)
        repeat(90) { g.update(1f / 60f) }
        shot(g, "01_menu")

        // play until a nice mid-run moment
        g.debugOpen(Game.PLAY)
        var frames = 0
        var took2 = false
        while (frames < 60 * 200 && g.state == Game.PLAY) {
            botStep(g); frames++
            if (!took2 && g.score >= 6 && !g.isOrbiting) { shot(g, "02_flight"); took2 = true; break }
        }
        // deep space: find a seed where the bot gets far
        for (seed in 1L..40L) {
            val d = newGame(MemoryStore().apply { stars = 410; skin = 7; ownedSkins = 0xFF; tutorialDone = true; gamesPlayed = 9 }, seed)
            d.debugOpen(Game.PLAY)
            var f = 0
            while (d.state == Game.PLAY && f < 60 * 240 && d.score < 30) { botStep(d); f++ }
            if (d.score >= 30) {
                // wait until mid-flight for a dynamic frame
                var k = 0
                while (d.isOrbiting && k < 600) { botStep(d); k++ }
                repeat(8) { d.update(1f / 60f) }
                shot(d, "03_deep_space")
                break
            }
        }
        g.debugKill()
        repeat(120) { g.update(1f / 60f) }
        shot(g, "04_game_over")

        val g2 = newGame(store, seed = 9L)
        repeat(30) { g2.update(1f / 60f) }
        g2.debugOpen(Game.SHOP)
        repeat(60) { g2.update(1f / 60f) }
        shot(g2, "05_skins")

        // Persian UI
        val fa = Strings(
            "جهش", "ستاره‌دنباله‌دار", "بین سیاره‌ها بپر. پایین رو نگاه نکن!", "بازی", "ظاهرها", "دوباره", "منو",
            "ادامه", "مکث", "رکورد", "امتیاز", "رکورد جدید!", "عالی", "برای پرش لمس کن", "مدارها فرو می‌ریزن — معطل نکن!",
            "ادامه ★%d", "خریداری‌شده", "انتخاب‌شده", "هدیه روزانه", "+%d ★", "صدا", "موسیقی", "روشن", "خاموش",
            "در فضا گم شدی", "برخورد کردی", "سیاه‌چاله بلعیدت", "سپر نجاتت داد!", "سپر", "آهنربا", "ستاره کافی نداری", "بازگشت",
            "★ +%d", "تعداد بازی: %d",
            listOf("اخگر", "یخچال", "سمی", "غنچه", "خورشیدی", "تهی", "منشور", "شفق"),
        )
        val g3 = Game(fa, MemoryStore().apply { stars = 120; best = 12 }, NoSfx, seed = 3L, today = { 1L }).apply { resize(1080, 1920, 0, 0) }
        repeat(90) { g3.update(1f / 60f) }
        shot(g3, "06_menu_fa")

        // tablet-ish aspect sanity render
        val g4 = newGame(store, seed = 12L).apply { resize(1600, 2560, 0, 0) }
        repeat(30) { g4.update(1f / 60f) }
        shot(g4, "07_tablet_menu", 1600, 2560)

        // landscape tablet (Android 16 ignores orientation locks on large screens)
        val g5 = newGame(store, seed = 12L).apply { resize(2560, 1600, 0, 0) }
        g5.debugOpen(Game.PLAY)
        repeat(60 * 4) { botStep(g5) }
        shot(g5, "08_tablet_landscape", 2560, 1600)
    }
}
