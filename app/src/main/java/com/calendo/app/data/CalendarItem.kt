package com.calendo.app.data

import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * 单日时间轴上的一条行程。
 *
 * [isTodo] 为 true 时表示「待办日程」：展示勾选框，完成后标题显示删除线。
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
)
