package com.calendo.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calendo.app.data.CalendarItem
import com.calendo.app.ui.CalendoUiState
import com.calendo.app.ui.CalendoViewModel
import com.calendo.app.ui.components.EventEditorBottomSheet
import com.calendo.app.ui.components.EventEditorSheet
import com.calendo.app.ui.components.TimelineDayView
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HeaderDateFormatter =
    DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

@Composable
fun DayRoute(
    vm: CalendoViewModel,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    DayScreen(
        state = state,
        modifier = modifier,
        onToggleTodoFilter = vm::toggleTodoFilter,
        onToggleTodoCompleted = { item -> vm.toggleTodoCompleted(item.id) },
        onSaveItem = vm::addOrUpdateItem,
        onDeleteItem = vm::deleteItem,
    )
}

@Composable
private fun DayScreen(
    state: CalendoUiState,
    onToggleTodoFilter: () -> Unit,
    onToggleTodoCompleted: (CalendarItem) -> Unit,
    onSaveItem: (CalendarItem) -> Unit,
    onDeleteItem: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheet by remember { mutableStateOf<EventEditorSheet>(EventEditorSheet.Hidden) }

    val dayItems = state.items.filter { it.date == state.selectedDate }
    val visible = if (state.todoFilterActive) dayItems.filter { it.isTodo } else dayItems

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "单日视图",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = state.selectedDate.format(HeaderDateFormatter),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        FilterChip(
                            selected = state.todoFilterActive,
                            onClick = onToggleTodoFilter,
                            label = { Text("待办") },
                            leadingIcon = if (state.todoFilterActive) {
                                {
                                    Icon(
                                        imageVector = Icons.Outlined.TaskAlt,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 4.dp),
                                    )
                                }
                            } else {
                                null
                            },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    },
                )
                Text(
                    text = "点击空白时间格新建日程 · 点击日程卡片编辑 · 待办日程可通过勾选完成（标题删除线）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        },
    ) { padding ->
        TimelineDayView(
            items = visible,
            startHour = 7,
            endHour = 23,
            hourHeight = 72.dp,
            onBackgroundHourClick = { start ->
                sheet = EventEditorSheet.Create(
                    date = state.selectedDate,
                    start = start,
                )
            },
            onEventClick = { item ->
                sheet = EventEditorSheet.Edit(item)
            },
            onTodoToggle = onToggleTodoCompleted,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        )
    }

    EventEditorBottomSheet(
        state = sheet,
        onDismiss = { sheet = EventEditorSheet.Hidden },
        onSave = onSaveItem,
        onDelete = onDeleteItem,
    )
}
