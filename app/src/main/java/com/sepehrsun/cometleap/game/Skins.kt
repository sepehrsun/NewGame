package com.sepehrsun.cometleap.game

class Skin(
    val head: Int,
    val trailA: Int,
    val trailB: Int,
    val price: Int,
    val rainbow: Boolean = false,
)

object Skins {
    val all = listOf(
        Skin(0xFFFFF1D0.toInt(), 0xFFFFB347.toInt(), 0xFFFF3D2E.toInt(), 0),
        Skin(0xFFEFFFFF.toInt(), 0xFF7FF3FF.toInt(), 0xFF2B6BFF.toInt(), 60),
        Skin(0xFFF4FFE0.toInt(), 0xFFB6FF3B.toInt(), 0xFF0FAF5A.toInt(), 120),
        Skin(0xFFFFE8F6.toInt(), 0xFFFF7EC8.toInt(), 0xFF9B3BFF.toInt(), 200),
        Skin(0xFFFFFFFF.toInt(), 0xFFFFF06A.toInt(), 0xFFFF8A00.toInt(), 300),
        Skin(0xFFFFFFFF.toInt(), 0xFFB18CFF.toInt(), 0xFF1A0638.toInt(), 450),
        Skin(0xFFFFFFFF.toInt(), 0xFFFF4D4D.toInt(), 0xFF4DA6FF.toInt(), 700, rainbow = true),
        Skin(0xFFE9FFFB.toInt(), 0xFF3BFFD0.toInt(), 0xFFFF3BD5.toInt(), 1000),
    )
}
