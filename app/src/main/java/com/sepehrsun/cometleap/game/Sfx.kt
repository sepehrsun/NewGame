package com.sepehrsun.cometleap.game

/** Sound output abstraction. */
interface Sfx {
    fun play(id: Int, rate: Float = 1f, volume: Float = 1f)
    fun setMusic(on: Boolean)
    companion object {
        const val LAUNCH = 0
        const val CAPTURE = 1
        const val PERFECT = 2
        const val STAR = 3
        const val BOOM = 4
        const val CLICK = 5
        const val POWER = 6
        const val SHIELD = 7
        const val WARN = 8
        const val COUNT = 9
    }
}

object NoSfx : Sfx {
    override fun play(id: Int, rate: Float, volume: Float) {}
    override fun setMusic(on: Boolean) {}
}
