package com.calendo.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calendo.app.data.CalendarItem
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

private fun minuteOfDay(t: LocalTime): Int = t.hour * 60 + t.minute

private fun timeFromMinuteOfDay(m: Int): LocalTime {
    val h = (m / 60).coerceIn(0, 23)
    val min = (m % 60).coerceIn(0, 59)
    return LocalTime.of(h, min)
}

sealed interface EventEditorSheet {
    data object Hidden : EventEditorSheet
    data class Create(
        val date: LocalDate,
        val start: LocalTime,
    ) : EventEditorSheet

    data class Edit(
        val item: CalendarItem,
    ) : EventEditorSheet
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorBottomSheet(
    state: EventEditorSheet,
    onDismiss: () -> Unit,
    onSave: (CalendarItem) -> Unit,
    onDelete: (String) -> Unit,
) {
    if (state is EventEditorSheet.Hidden) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val initial = when (state) {
        is EventEditorSheet.Create -> {
            val end = state.start.plusHours(1)
            EditorFormState(
                title = "",
                startMin = minuteOfDay(state.start),
                endMin = minuteOfDay(end),
                isTodo = false,
                participantsText = "",
                priority = null,
            )
        }

        is EventEditorSheet.Edit -> EditorFormState(
            title = state.item.title,
            startMin = minuteOfDay(state.item.start),
            endMin = minuteOfDay(state.item.end),
            isTodo = state.item.isTodo,
            participantsText = state.item.participants.joinToString("，"),
            priority = state.item.priority,
        )

        EventEditorSheet.Hidden -> error("unreachable")
    }

    var form by remember(state) { mutableStateOf(initial) }
    var errorText by remember(state) { mutableStateOf<String?>(null) }

    LaunchedEffect(state) {
        form = initial
        errorText = null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = when (state) {
                    is EventEditorSheet.Create -> "新建日程"
                    is EventEditorSheet.Edit -> "编辑日程"
                    EventEditorSheet.Hidden -> ""
                },
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value = form.title,
                onValueChange = { form = form.copy(title = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("标题") },
                singleLine = true,
            )

            Text(text = "优先级", style = MaterialTheme.typography.labelMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                listOf(null, "P0", "P1", "P2").forEach { p ->
                    FilterChip(
                        selected = form.priority == p,
                        onClick = { form = form.copy(priority = p) },
                        label = { Text(p ?: "无") },
                    )
                }
            }

            ScheduleTimeRangeSlider(
                startMinuteOfDay = form.startMin,
                endMinuteOfDay = form.endMin,
                onRangeChange = { s, e ->
                    form = form.copy(startMin = s, endMin = e)
                },
                dayStartHour = 7,
                dayEndHour = 23,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = form.isTodo,
                    onCheckedChange = { form = form.copy(isTodo = it) },
                )
                Text(
                    text = "待办日程（可勾选完成并显示删除线）",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            OutlinedTextField(
                value = form.participantsText,
                onValueChange = { form = form.copy(participantsText = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("参与人（可选，逗号或中文逗号分隔）") },
                minLines = 2,
            )

            if (errorText != null) {
                Text(
                    text = errorText!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state is EventEditorSheet.Edit) {
                    TextButton(
                        onClick = {
                            onDelete(state.item.id)
                            onDismiss()
                        },
                    ) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        val parsed = parseAndBuild(state, form)
                        if (parsed == null) {
                            errorText = "请确保结束时间晚于开始时间，且间隔至少 15 分钟。"
                        } else {
                            errorText = null
                            onSave(parsed)
                            onDismiss()
                        }
                    },
                ) {
                    Text("保存")
                }
            }
        }
    }
}

private data class EditorFormState(
    val title: String,
    val startMin: Int,
    val endMin: Int,
    val isTodo: Boolean,
    val participantsText: String,
    val priority: String?,
)

private fun parseAndBuild(
    sheet: EventEditorSheet,
    form: EditorFormState,
): CalendarItem? {
    val title = form.title.trim()
    if (title.isEmpty()) return null

    val start = timeFromMinuteOfDay(form.startMin)
    val end = timeFromMinuteOfDay(form.endMin)
    if (!end.isAfter(start)) return null
    if (ChronoUnit.MINUTES.between(start, end) < 15) return null

    val participants = form.participantsText
        .split(",", "，")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    val palette = kotlin.math.abs(title.hashCode()) % com.calendo.app.ui.theme.BlockPalette.size

    return when (sheet) {
        is EventEditorSheet.Create -> CalendarItem(
            title = title,
            date = sheet.date,
            start = start,
            end = end,
            isTodo = form.isTodo,
            completed = false,
            participants = participants,
            paletteIndex = palette,
            priority = form.priority,
        )

        is EventEditorSheet.Edit -> sheet.item.copy(
            title = title,
            start = start,
            end = end,
            isTodo = form.isTodo,
            completed = if (form.isTodo) sheet.item.completed else false,
            participants = participants,
            priority = form.priority,
        )

        EventEditorSheet.Hidden -> null
    }
}
