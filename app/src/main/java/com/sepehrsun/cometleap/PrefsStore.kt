package com.sepehrsun.cometleap

import android.content.Context
import com.sepehrsun.cometleap.game.Store

class PrefsStore(context: Context) : Store {
    private val p = context.getSharedPreferences("cometleap", Context.MODE_PRIVATE)

    override var best: Int
        get() = p.getInt("best", 0)
        set(v) = p.edit().putInt("best", v).apply()
    override var stars: Int
        get() = p.getInt("stars", 0)
        set(v) = p.edit().putInt("stars", v).apply()
    override var ownedSkins: Int
        get() = p.getInt("owned", 1) or 1
        set(v) = p.edit().putInt("owned", v).apply()
    override var skin: Int
        get() = p.getInt("skin", 0)
        set(v) = p.edit().putInt("skin", v).apply()
    override var sound: Boolean
        get() = p.getBoolean("sound", true)
        set(v) = p.edit().putBoolean("sound", v).apply()
    override var music: Boolean
        get() = p.getBoolean("music", true)
        set(v) = p.edit().putBoolean("music", v).apply()
    override var lastGiftDay: Long
        get() = p.getLong("gift", -1L)
        set(v) = p.edit().putLong("gift", v).apply()
    override var gamesPlayed: Int
        get() = p.getInt("games", 0)
        set(v) = p.edit().putInt("games", v).apply()
    override var tutorialDone: Boolean
        get() = p.getBoolean("tut", false)
        set(v) = p.edit().putBoolean("tut", v).apply()
}
