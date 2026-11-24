package com.sepehrsun.cometleap

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import com.sepehrsun.cometleap.audio.AndroidSfx
import com.sepehrsun.cometleap.game.Game
import com.sepehrsun.cometleap.game.Strings

class MainActivity : Activity() {
    private lateinit var view: GameView
    private lateinit var sfx: AndroidSfx
    private var backCallback: OnBackInvokedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = PrefsStore(this)
        sfx = AndroidSfx(this) { store.music }
        var gv: GameView? = null
        val game = Game(loadStrings(), store, sfx, { k -> gv?.haptic(k) })
        view = GameView(this, game)
        gv = view
        setContentView(view)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            val cb = OnBackInvokedCallback { if (!view.backPressed()) finish() }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb)
            backCallback = cb
        }
    }

    private fun loadStrings() = Strings(
        getString(R.string.title_line1), getString(R.string.title_line2), getString(R.string.tagline),
        getString(R.string.play), getString(R.string.shop), getString(R.string.retry), getString(R.string.menu),
        getString(R.string.resume), getString(R.string.paused), getString(R.string.best), getString(R.string.score),
        getString(R.string.new_best), getString(R.string.perfect), getString(R.string.tap_to_launch), getString(R.string.hint_decay),
        getString(R.string.continue_for), getString(R.string.owned), getString(R.string.selected), getString(R.string.daily_gift),
        getString(R.string.gift_received), getString(R.string.sound), getString(R.string.music), getString(R.string.on), getString(R.string.off),
        getString(R.string.lost), getString(R.string.crashed), getString(R.string.swallowed), getString(R.string.shield_saved),
        getString(R.string.shield), getString(R.string.magnet), getString(R.string.not_enough), getString(R.string.back),
        getString(R.string.stars_earned), getString(R.string.games_played),
        resources.getStringArray(R.array.skin_names).toList(),
    )

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }
    }

    override fun onResume() {
        super.onResume()
        sfx.onResume()
    }

    override fun onPause() {
        view.pauseGame()
        sfx.onPause()
        super.onPause()
    }

    // Below Android 13 the back key arrives as a key event.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (Build.VERSION.SDK_INT < 33 && keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0) {
            if (view.backPressed()) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 33) backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        sfx.release()
        super.onDestroy()
    }
}
