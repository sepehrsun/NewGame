package com.sepehrsun.cometleap.game

/** All user-visible text, resolved from Android resources by the host. */
class Strings(
    val title1: String, val title2: String, val tagline: String,
    val play: String, val shop: String, val retry: String, val menu: String,
    val resume: String, val paused: String, val best: String, val score: String,
    val newBest: String, val perfect: String, val tapToLaunch: String, val hintDecay: String,
    val continueFor: String, val owned: String, val selected: String, val dailyGift: String,
    val giftReceived: String, val sound: String, val music: String, val on: String, val off: String,
    val lost: String, val crashed: String, val swallowed: String, val shieldSaved: String,
    val shield: String, val magnet: String, val notEnough: String, val back: String,
    val starsEarned: String, val gamesPlayed: String,
    val skinNames: List<String>,
) {
    companion object {
        fun english() = Strings(
            "COMET", "LEAP", "Leap between worlds. Don't look down.",
            "PLAY", "SKINS", "RETRY", "MENU", "RESUME", "PAUSED", "BEST", "SCORE",
            "NEW BEST!", "PERFECT", "TAP to leap", "Orbits decay — don't linger!",
            "CONTINUE ★%d", "OWNED", "EQUIPPED", "DAILY GIFT", "+%d ★", "SOUND", "MUSIC", "ON", "OFF",
            "LOST IN SPACE", "CRASHED", "SWALLOWED BY THE VOID", "SHIELD SAVED YOU!",
            "SHIELD", "MAGNET", "Not enough ★", "BACK", "★ +%d", "Games: %d",
            listOf("Ember", "Glacier", "Toxin", "Rosebud", "Solar", "Void", "Prism", "Aurora"),
        )
    }
}
