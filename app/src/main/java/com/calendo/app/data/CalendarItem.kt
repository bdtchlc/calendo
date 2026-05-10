package com.calendo.app.data

import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * 单日时间轴上的一条行程。
 *
 * [paletteIndex] 用于 Time Blocks 风格的多彩色块（取模调色盘）。
 * [priority] 可选：如 P0 / P1（与设计稿一致）。
 */
data class CalendarItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val isTodo: Boolean,
    val completed: Boolean = false,
    val participants: List<String> = emptyList(),
    val paletteIndex: Int = 0,
    val priority: String? = null,
)
