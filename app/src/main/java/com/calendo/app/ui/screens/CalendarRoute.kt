package com.calendo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calendo.app.data.CalendarItem
import com.calendo.app.ui.CalendarSurfaceMode
import com.calendo.app.ui.CalendoViewModel
import com.calendo.app.ui.theme.colorsForPaletteIndex
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val WeekdayChars = listOf("一", "二", "三", "四", "五", "六", "日")
private val MonthTitleFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarRoute(
    vm: CalendoViewModel,
    onOpenDayView: (LocalDate) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var expandedDate by remember { mutableStateOf<LocalDate?>(null) }

    LaunchedEffect(state.calendarSurfaceMode) {
        expandedDate = null
    }

    fun handleDayClick(day: LocalDate) {
        val hasTasks = state.items.any { it.date == day }
        if (hasTasks) {
            if (expandedDate == day) {
                expandedDate = null
                onOpenDayView(day)
            } else {
                expandedDate = day
            }
        } else {
            expandedDate = null
            onOpenDayView(day)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            title = {
                Text("日历", fontWeight = FontWeight.SemiBold)
            },
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SegmentedButton(
                selected = state.calendarSurfaceMode == CalendarSurfaceMode.WEEK,
                onClick = { vm.setCalendarSurfaceMode(CalendarSurfaceMode.WEEK) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("周") }
            SegmentedButton(
                selected = state.calendarSurfaceMode == CalendarSurfaceMode.MONTH,
                onClick = { vm.setCalendarSurfaceMode(CalendarSurfaceMode.MONTH) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("月") }
        }

        when (state.calendarSurfaceMode) {
            CalendarSurfaceMode.WEEK -> WeekStripView(
                anchor = state.selectedDate,
                expandedDate = expandedDate,
                items = state.items,
                onDayClick = { handleDayClick(it) },
                onPrevWeek = { vm.setSelectedDate(state.selectedDate.minusWeeks(1)) },
                onNextWeek = { vm.setSelectedDate(state.selectedDate.plusWeeks(1)) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )

            CalendarSurfaceMode.MONTH -> MonthGridView(
                focusMonth = YearMonth.from(state.selectedDate),
                selectedDate = state.selectedDate,
                expandedDate = expandedDate,
                items = state.items,
                onDayClick = { handleDayClick(it) },
                onPrevMonth = {
                    val d = state.selectedDate.withDayOfMonth(1).minusMonths(1)
                    vm.setSelectedDate(d)
                },
                onNextMonth = {
                    val d = state.selectedDate.withDayOfMonth(1).plusMonths(1)
                    vm.setSelectedDate(d)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }

        ExpandedDayTasksPanel(
            expandedDate = expandedDate,
            items = state.items,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun ExpandedDayTasksPanel(
    expandedDate: LocalDate?,
    items: List<CalendarItem>,
    modifier: Modifier = Modifier,
) {
    val date = expandedDate ?: return
    val dayItems = remember(items, date) {
        items.filter { it.date == date }.sortedWith(compareBy({ it.start }, { it.end }))
    }
    if (dayItems.isEmpty()) return

    Surface(
        tonalElevation = 2.dp,
        shadowElevation = 0.dp,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${date.monthValue}月${date.dayOfMonth}日 · ${dayItems.size} 项（再点此日在周/月视图内进入「今天」详情）",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            dayItems.take(32).forEach { item ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val c = colorsForPaletteIndex(item.paletteIndex)
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(c.border),
                    )
                    Text(
                        text = "${item.start}–${item.end} · ${item.title}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekStripView(
    anchor: LocalDate,
    expandedDate: LocalDate?,
    items: List<CalendarItem>,
    onDayClick: (LocalDate) -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val weekStart = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekEnd = weekStart.plusDays(6)

    Column(modifier = modifier.padding(horizontal = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = onPrevWeek) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "上一周")
            }
            Text(
                text = "${weekStart.monthValue}月${weekStart.dayOfMonth}日 – ${weekEnd.monthValue}月${weekEnd.dayOfMonth}日",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            IconButton(onClick = onNextWeek) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "下一周")
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (i in 0..6) {
                val day = weekStart.plusDays(i.toLong())
                val dayItems = items.filter { it.date == day }
                val selected = day == anchor
                val expandedHere = expandedDate == day
                Column(
                    modifier = Modifier
                        .width(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onDayClick(day) }
                        .background(
                            when {
                                expandedHere -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                                selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
                        )
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = WeekdayChars[i],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${day.dayOfMonth}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.height(120.dp),
                    ) {
                        dayItems.take(8).forEach { ev ->
                            val c = colorsForPaletteIndex(ev.paletteIndex)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(c.border.copy(alpha = 0.85f)),
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = "有任务的日期：首次点击展开当日列表；再次点击跳到「今天」该日。无任务日期直接进入当日时间轴。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun MonthGridView(
    focusMonth: YearMonth,
    selectedDate: LocalDate,
    expandedDate: LocalDate?,
    items: List<CalendarItem>,
    onDayClick: (LocalDate) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = focusMonth.atDay(1)
    val offset = first.dayOfWeek.value - 1
    val daysInMonth = focusMonth.lengthOfMonth()
    val cells = (1..42).map { idx ->
        val dayNum = idx - offset
        if (dayNum in 1..daysInMonth) focusMonth.atDay(dayNum) else null
    }

    Column(modifier = modifier.padding(horizontal = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = onPrevMonth) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "上一月")
            }
            Text(
                text = first.format(MonthTitleFmt),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onNextMonth) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "下一月")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            WeekdayChars.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        Column {
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        day != null && expandedDate == day ->
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                                        day != null && day == selectedDate ->
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                        else -> Color.Transparent
                                    },
                                )
                                .clickable(enabled = day != null) {
                                    if (day != null) onDayClick(day)
                                },
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            if (day == null) return@Box
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${day.dayOfMonth}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (day == selectedDate) FontWeight.Bold else FontWeight.Normal,
                                )
                                val dots = items.filter { it.date == day }.take(3)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.padding(top = 4.dp),
                                ) {
                                    dots.forEach { ev ->
                                        val c = colorsForPaletteIndex(ev.paletteIndex)
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(c.border),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(
            text = "月视图：圆点表示当日事项数量；有任务的日期可展开列表，再次点击该日进入「今天」。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
        )
    }
}
