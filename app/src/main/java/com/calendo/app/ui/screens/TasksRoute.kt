package com.calendo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calendo.app.data.CalendarItem
import com.calendo.app.ui.CalendoViewModel
import com.calendo.app.ui.components.EventEditorSheet
import com.calendo.app.ui.components.TaskDetailBottomSheet
import com.calendo.app.ui.theme.colorsForPaletteIndex
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksRoute(
    vm: CalendoViewModel,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var detailItem by remember { mutableStateOf<CalendarItem?>(null) }
    var showCompletedTasks by remember { mutableStateOf(false) }
    var snowballExpanded by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val pending = remember(state.items) {
        state.items.filter { it.isTodo && !it.completed }
            .sortedWith(compareBy({ it.date }, { it.start }))
    }
    val rolled = remember(state.items, today) {
        state.items.filter { it.isTodo && !it.completed && it.date.isBefore(today) }
            .sortedWith(compareBy({ it.date }, { it.start }))
    }
    val todayPending = pending.filter { it.date == today }
    val upcoming = pending.filter { it.date.isAfter(today) }
    val completedTodos = remember(state.items) {
        state.items.filter { it.isTodo && it.completed }
            .sortedWith(
                compareByDescending<CalendarItem> { it.date }.thenByDescending { it.start },
            )
    }
    val resolvedDetail = remember(detailItem, state.items) {
        detailItem?.let { d -> state.items.find { it.id == d.id } ?: d }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text("任务", fontWeight = FontWeight.SemiBold)
                },
                actions = {
                    if (completedTodos.isNotEmpty()) {
                        TextButton(onClick = { showCompletedTasks = !showCompletedTasks }) {
                            Text(
                                if (showCompletedTasks) "隐藏已完成" else "显示已完成任务",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            if (rolled.isNotEmpty()) {
                item {
                    SectionTitle("逾期未办 · 滚雪球")
                }
                val rolledShown =
                    if (rolled.size <= 2 || snowballExpanded) rolled else rolled.take(2)
                items(rolledShown, key = { it.id }) { item ->
                    TaskRow(
                        item = item,
                        accentSnowball = true,
                        onToggle = { vm.toggleTodoCompleted(item.id) },
                        onOpenDetail = { detailItem = item },
                    )
                }
                if (rolled.size > 2) {
                    item {
                        TextButton(
                            onClick = { snowballExpanded = !snowballExpanded },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) {
                            Text(
                                if (snowballExpanded) {
                                    "收起"
                                } else {
                                    "还有 ${rolled.size - 2} 条逾期未办，展开查看"
                                },
                            )
                        }
                    }
                }
                item { HorizontalDivider() }
            }
            if (todayPending.isNotEmpty()) {
                item { SectionTitle("今天") }
                items(todayPending, key = { it.id }) { item ->
                    TaskRow(
                        item = item,
                        accentSnowball = false,
                        onToggle = { vm.toggleTodoCompleted(item.id) },
                        onOpenDetail = { detailItem = item },
                    )
                }
                item { HorizontalDivider() }
            }
            if (upcoming.isNotEmpty()) {
                item { SectionTitle("即将到来") }
                items(upcoming, key = { it.id }) { item ->
                    TaskRow(
                        item = item,
                        accentSnowball = false,
                        onToggle = { vm.toggleTodoCompleted(item.id) },
                        onOpenDetail = { detailItem = item },
                    )
                }
            }
            if (showCompletedTasks && completedTodos.isNotEmpty()) {
                item { HorizontalDivider() }
                item { SectionTitle("已完成") }
                items(completedTodos, key = { it.id }) { item ->
                    TaskRow(
                        item = item,
                        accentSnowball = false,
                        onToggle = { vm.toggleTodoCompleted(item.id) },
                        onOpenDetail = { detailItem = item },
                    )
                }
            }
            if (pending.isEmpty()) {
                item {
                    Text(
                        text = "暂无未完成待办",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            }
        }

        TaskDetailBottomSheet(
            item = resolvedDetail,
            onDismiss = { detailItem = null },
            onEdit = { item ->
                vm.openEventEditor(EventEditorSheet.Edit(item))
            },
            onToggleCompleted = { item ->
                vm.toggleTodoCompleted(item.id)
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun TaskRow(
    item: CalendarItem,
    accentSnowball: Boolean,
    onToggle: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    val c = colorsForPaletteIndex(item.paletteIndex)
    val strike = item.completed
    Surface(
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        shape = RoundedCornerShape(12.dp),
        color = if (accentSnowball) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clickable { onOpenDetail() },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = item.completed,
                onCheckedChange = { onToggle() },
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircleOutline,
                        contentDescription = null,
                        tint = c.border,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        textDecoration = if (strike) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (strike) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                Text(
                    text = "${item.date.format(DayFmt)} · ${item.start} – ${item.end}" +
                        if (item.priority != null) " · ${item.priority}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
