package com.sepehrsun.cometleap

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowInsets
import com.sepehrsun.cometleap.game.Game
import java.util.concurrent.ConcurrentLinkedQueue

/** Hosts the game on a SurfaceView with a dedicated render thread. */
@SuppressLint("ViewConstructor")
class GameView(context: Context, val game: Game) : SurfaceView(context), SurfaceHolder.Callback {
    private var thread: Thread? = null
    @Volatile private var running = false
    private val touches = ConcurrentLinkedQueue<FloatArray>()
    private var safeTop = 0
    private var safeBottom = 0
    private var surfaceW = 0
    private var surfaceH = 0
    private val lock = Any()

    init {
        holder.addCallback(this)
        isFocusable = true
        keepScreenOn = true
    }

    fun haptic(kind: Int) {
        post {
            val c = if (kind == Game.HAPTIC_HEAVY) {
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
            } else {
                if (Build.VERSION.SDK_INT >= 27) HapticFeedbackConstants.KEYBOARD_PRESS else HapticFeedbackConstants.VIRTUAL_KEY
            }
            performHapticFeedback(c)
        }
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= 30) {
            val i = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            safeTop = i.top
            safeBottom = i.bottom
        } else if (Build.VERSION.SDK_INT >= 28) {
            val cut = insets.displayCutout
            @Suppress("DEPRECATION")
            safeTop = maxOf(cut?.safeInsetTop ?: 0, insets.systemWindowInsetTop)
            @Suppress("DEPRECATION")
            safeBottom = insets.systemWindowInsetBottom
        }
        synchronized(lock) { if (surfaceW > 0) game.resize(surfaceW, surfaceH, safeTop, safeBottom) }
        return super.onApplyWindowInsets(insets)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                touches.add(floatArrayOf(e.getX(i), e.getY(i)))
            }
        }
        return true
    }

    fun backPressed(): Boolean = synchronized(lock) { game.onBack() }

    fun pauseGame() = synchronized(lock) { game.onAppPaused() }

    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        synchronized(lock) {
            surfaceW = width; surfaceH = height
            game.resize(width, height, safeTop, safeBottom)
        }
        start()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) = stop()

    private fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "game-loop").apply { start() }
    }

    private fun stop() {
        running = false
        thread?.let { t ->
            try { t.join(500) } catch (_: InterruptedException) { }
        }
        thread = null
    }

    private fun loop() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = (now - last) / 1e9f
            last = now
            var canvas: Canvas? = null
            try {
                synchronized(lock) {
                    while (true) {
                        val t = touches.poll() ?: break
                        game.touchDown(t[0], t[1])
                    }
                    game.update(dt)
                }
                canvas = if (Build.VERSION.SDK_INT >= 26) holder.lockHardwareCanvas() else holder.lockCanvas()
                if (canvas != null) synchronized(lock) { game.render(canvas) }
            } catch (_: IllegalStateException) {
                // surface went away mid-frame
            } finally {
                if (canvas != null) try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) { }
            }
            if (canvas == null) try { Thread.sleep(16) } catch (_: InterruptedException) { }
        }
    }
}
