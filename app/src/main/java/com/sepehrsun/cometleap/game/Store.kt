package com.sepehrsun.cometleap.game

/** Persistence abstraction so the game core is testable off-device. */
interface Store {
    var best: Int
    var stars: Int
    var ownedSkins: Int      // bitmask
    var skin: Int
    var sound: Boolean
    var music: Boolean
    var lastGiftDay: Long
    var gamesPlayed: Int
    var tutorialDone: Boolean
}

class MemoryStore : Store {
    override var best = 0
    override var stars = 0
    override var ownedSkins = 1
    override var skin = 0
    override var sound = true
    override var music = true
    override var lastGiftDay = -1L
    override var gamesPlayed = 0
    override var tutorialDone = false
}
