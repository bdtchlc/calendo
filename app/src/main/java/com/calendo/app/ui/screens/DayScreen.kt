package com.calendo.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calendo.app.data.CalendarItem
import com.calendo.app.ui.CalendoPagerDefaults
import com.calendo.app.ui.CalendoUiState
import com.calendo.app.ui.CalendoViewModel
import com.calendo.app.ui.components.EventEditorSheet
import com.calendo.app.ui.components.TimelineDayView
import com.calendo.app.ui.dateForPage
import com.calendo.app.ui.pageForDate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
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
        onOpenEventEditor = vm::openEventEditor,
        onSyncSelectedDate = vm::setSelectedDate,
        onEventDragEnd = { item, deltaMinutes ->
            vm.rescheduleItemByDrag(item.id, deltaMinutes)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun DayScreen(
    state: CalendoUiState,
    onToggleTodoFilter: () -> Unit,
    onToggleTodoCompleted: (CalendarItem) -> Unit,
    onOpenEventEditor: (EventEditorSheet) -> Unit,
    onSyncSelectedDate: (LocalDate) -> Unit,
    onEventDragEnd: (CalendarItem, deltaMinutes: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val anchorDay = LocalDate.now()
    val initialPage = remember(state.selectedDate, anchorDay) {
        pageForDate(anchorDay, state.selectedDate).coerceIn(0, CalendoPagerDefaults.PageCount - 1)
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { CalendoPagerDefaults.PageCount },
    )

    val scope = rememberCoroutineScope()

    LaunchedEffect(state.selectedDate, anchorDay) {
        val target = pageForDate(anchorDay, state.selectedDate).coerceIn(0, CalendoPagerDefaults.PageCount - 1)
        if (pagerState.currentPage != target) {
            scope.launch { pagerState.animateScrollToPage(target) }
        }
    }

    LaunchedEffect(pagerState, anchorDay) {
        snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
            .filter { !it.second }
            .map { it.first }
            .distinctUntilChanged()
            .collect { page ->
                onSyncSelectedDate(dateForPage(anchorDay, page))
            }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "今天",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Row {
                                Text(
                                    text = state.selectedDate.format(HeaderDateFormatter),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (state.selectedDate == anchorDay) {
                                    Text(
                                        text = " · 今天",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                Text(
                    text = "左右滑动切换日期 · 空白格新建 · 长按卡片拖动调整时间 · 轻点文字编辑",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) { page ->
            val pageDate = dateForPage(anchorDay, page)
            val snowball = remember(state.items, pageDate, anchorDay) {
                if (pageDate == anchorDay) {
                    state.items.filter { it.isTodo && !it.completed && it.date.isBefore(anchorDay) }
                        .sortedWith(compareBy({ it.date }, { it.start }))
                } else {
                    emptyList()
                }
            }

            val dayItems = state.items.filter { it.date == pageDate }
            val visible =
                if (state.todoFilterActive) dayItems.filter { it.isTodo } else dayItems

            Column(modifier = Modifier.fillMaxSize()) {
                if (snowball.isNotEmpty()) {
                    Surface(
                        tonalElevation = 1.dp,
                        shadowElevation = 0.dp,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                text = "逾期未办 · 滚雪球（${snowball.size}）",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            snowball.forEach { item ->
                                Text(
                                    text = "· ${item.date} ${item.title}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }

                TimelineDayView(
                    items = visible,
                    startHour = 7,
                    endHour = 23,
                    hourHeight = 80.dp,
                    onBackgroundHourClick = { start ->
                        onOpenEventEditor(
                            EventEditorSheet.Create(
                                date = pageDate,
                                start = start,
                            ),
                        )
                    },
                    onEventClick = { item ->
                        onOpenEventEditor(EventEditorSheet.Edit(item))
                    },
                    onTodoToggle = onToggleTodoCompleted,
                    onEventDragEnd = onEventDragEnd,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }
    }
}
