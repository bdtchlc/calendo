package com.calendo.app.ui

import androidx.lifecycle.ViewModel
import com.calendo.app.data.CalendarItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalTime

data class CalendoUiState(
    val selectedDate: LocalDate = LocalDate.of(2026, 3, 21),
    val todoFilterActive: Boolean = false,
    /** 所有日期的行程；界面按 [selectedDate] 过滤。 */
    val items: List<CalendarItem> = sampleItems(),
)

class CalendoViewModel : ViewModel() {

    private val _state = MutableStateFlow(CalendoUiState())
    val state: StateFlow<CalendoUiState> = _state.asStateFlow()

    fun setSelectedDate(date: LocalDate) {
        _state.update { it.copy(selectedDate = date) }
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
}

private fun sampleItems(): List<CalendarItem> {
    val day = LocalDate.of(2026, 3, 21)
    return listOf(
        CalendarItem(
            title = "采购流程讨论会（示例文案较长用于验证省略号截断）",
            date = day,
            start = LocalTime.of(14, 0),
            end = LocalTime.of(14, 45),
            isTodo = false,
        ),
        CalendarItem(
            title = "管理会议",
            date = day,
            start = LocalTime.of(15, 0),
            end = LocalTime.of(16, 0),
            isTodo = true,
            completed = false,
            participants = listOf("何叔", "银姐"),
        ),
    )
}
