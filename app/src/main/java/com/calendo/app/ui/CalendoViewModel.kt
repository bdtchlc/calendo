package com.calendo.app.ui

import androidx.lifecycle.ViewModel
import com.calendo.app.data.CalendarItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

enum class CalendarSurfaceMode {
    WEEK,
    MONTH,
}

data class CalendoUiState(
    /** 当前聚焦日期（日视图滑动、日历点选会更新）。 */
    val selectedDate: LocalDate = LocalDate.now(),
    /** 「日历」Tab 内周 / 月切换。 */
    val calendarSurfaceMode: CalendarSurfaceMode = CalendarSurfaceMode.WEEK,
    val todoFilterActive: Boolean = false,
    val items: List<CalendarItem> = sampleItems(),
    /** Google 登录邮箱（仅本地状态；持久化与双向同步见后续）。 */
    val googleAccountEmail: String? = null,
    /** 最近一次同步提示（占位）。 */
    val lastSyncHint: String? = null,
)

class CalendoViewModel : ViewModel() {

    private val _state = MutableStateFlow(CalendoUiState())
    val state: StateFlow<CalendoUiState> = _state.asStateFlow()

    fun setSelectedDate(date: LocalDate) {
        _state.update { it.copy(selectedDate = date) }
    }

    fun setCalendarSurfaceMode(mode: CalendarSurfaceMode) {
        _state.update { it.copy(calendarSurfaceMode = mode) }
    }

    fun toggleTodoFilter() {
        _state.update { it.copy(todoFilterActive = !it.todoFilterActive) }
    }

    fun toggleTodoCompleted(id: String) {
        _state.update { s ->
            s.copy(
                items = s.items.map { item ->
                    if (item.id == id && item.isTodo) item.copy(completed = !item.completed)
                    else item
                },
            )
        }
    }

    fun addOrUpdateItem(item: CalendarItem) {
        _state.update { s ->
            val next = s.items.filterNot { it.id == item.id } + item
            s.copy(items = next.sortedWith(compareBy({ it.date }, { it.start }, { it.end })))
        }
    }

    fun deleteItem(id: String) {
        _state.update { s -> s.copy(items = s.items.filterNot { it.id == id }) }
    }

    /**
     * 长按拖动时间轴块后，整体平移开始/结束时间（保持时长；限制在 7:00–23:59）。
     * [deltaMinutes] 为 15 分钟对齐后的增量。
     */
    fun rescheduleItemByDrag(id: String, deltaMinutes: Int) {
        if (deltaMinutes == 0) return
        _state.update { s ->
            val item = s.items.find { it.id == id } ?: return@update s
            val dur = ChronoUnit.MINUTES.between(item.start, item.end).coerceAtLeast(15)
            val minS = LocalTime.of(7, 0)
            val maxE = LocalTime.of(23, 59)
            var ns = item.start.plusMinutes(deltaMinutes.toLong())
            var ne = ns.plusMinutes(dur)
            if (ns.isBefore(minS)) {
                ns = minS
                ne = ns.plusMinutes(dur)
            }
            if (ne.isAfter(maxE)) {
                ne = maxE
                ns = ne.minusMinutes(dur)
                if (ns.isBefore(minS)) ns = minS
            }
            if (!ne.isAfter(ns)) return@update s
            val updated = item.copy(start = ns, end = ne)
            val next = s.items.filterNot { it.id == id } + updated
            s.copy(items = next.sortedWith(compareBy({ it.date }, { it.start }, { it.end })))
        }
    }

    fun setGoogleAccount(email: String?) {
        _state.update { it.copy(googleAccountEmail = email, lastSyncHint = email?.let { "已连接：$it（日历双向同步需完成云端配置）" }) }
    }

    fun setSyncHint(text: String?) {
        _state.update { it.copy(lastSyncHint = text) }
    }

}

private fun sampleItems(): List<CalendarItem> {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    return listOf(
        CalendarItem(
            title = "昨日遗留待办（滚雪球示例）",
            date = yesterday,
            start = LocalTime.of(9, 0),
            end = LocalTime.of(9, 30),
            isTodo = true,
            completed = false,
            paletteIndex = 3,
            priority = "P1",
        ),
        CalendarItem(
            title = "采购流程讨论会",
            date = today,
            start = LocalTime.of(13, 30),
            end = LocalTime.of(14, 45),
            isTodo = false,
            participants = listOf("何叔", "银姐"),
            paletteIndex = 0,
            priority = "P1",
        ),
        CalendarItem(
            title = "管理周会",
            date = today,
            start = LocalTime.of(15, 0),
            end = LocalTime.of(16, 0),
            isTodo = true,
            completed = false,
            participants = listOf("何叔", "银姐"),
            paletteIndex = 2,
            priority = "P0",
        ),
    )
}

/** Pager 页码与日期换算（中心页 = AnchorPage 表示 [anchorDate]，通常为今天）。 */
fun pageForDate(anchorDate: LocalDate, target: LocalDate, anchorPage: Int = CalendoPagerDefaults.AnchorPage): Int =
    anchorPage + ChronoUnit.DAYS.between(anchorDate, target).toInt()

fun dateForPage(anchorDate: LocalDate, page: Int, anchorPage: Int = CalendoPagerDefaults.AnchorPage): LocalDate =
    anchorDate.plusDays((page - anchorPage).toLong())

object CalendoPagerDefaults {
    const val AnchorPage = 1000
    const val PageCount = 2000
}
