package com.calendo.app.ui.theme

import androidx.compose.ui.graphics.Color

val Primary = Color(0xFF2D6CDF)
val SurfaceTint = Color(0xFFF4F6FB)
val TimelineGrid = Color(0xFFE8ECF3)

/** Time Blocks 风格事件块调色（边框略深、填充略浅） */
data class BlockColors(val fill: Color, val border: Color, val onBlock: Color)

val BlockPalette: List<BlockColors> = listOf(
    BlockColors(fill = Color(0xFFE8F0FE), border = Color(0xFF6B9CF7), onBlock = Color(0xFF1B3F91)),
    BlockColors(fill = Color(0xFFFFF3E6), border = Color(0xFFFFA94D), onBlock = Color(0xFF8A4B00)),
    BlockColors(fill = Color(0xFFE8F8F2), border = Color(0xFF4CD4A0), onBlock = Color(0xFF0F5C3A)),
    BlockColors(fill = Color(0xFFF3E8FF), border = Color(0xFFB87AFF), onBlock = Color(0xFF4B1D8C)),
    BlockColors(fill = Color(0xFFFFE8EE), border = Color(0xFFFF6B8A), onBlock = Color(0xFF8C1536)),
    BlockColors(fill = Color(0xFFEFF7FF), border = Color(0xFF5CC8FF), onBlock = Color(0xFF085A7A)),
)

fun colorsForPaletteIndex(index: Int): BlockColors =
    BlockPalette[index.mod(BlockPalette.size)]

val TodoAccent = Color(0xFF2563EB)
