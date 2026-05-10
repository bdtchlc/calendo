package com.calendo.app.ui.components

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
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val TimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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
        is EventEditorSheet.Create -> EditorFormState(
            title = "",
            startText = state.start.format(TimeFmt),
            endText = state.start.plusHours(1).format(TimeFmt),
            isTodo = false,
            participantsText = "",
        )

        is EventEditorSheet.Edit -> EditorFormState(
            title = state.item.title,
            startText = state.item.start.format(TimeFmt),
            endText = state.item.end.format(TimeFmt),
            isTodo = state.item.isTodo,
            participantsText = state.item.participants.joinToString("，"),
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = form.startText,
                    onValueChange = { form = form.copy(startText = it) },
                    modifier = Modifier.weight(1f),
                    label = { Text("开始 (HH:mm)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = form.endText,
                    onValueChange = { form = form.copy(endText = it) },
                    modifier = Modifier.weight(1f),
                    label = { Text("结束 (HH:mm)") },
                    singleLine = true,
                )
            }

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
                            errorText = "请检查时间格式，并确保结束时间晚于开始时间。"
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
    val startText: String,
    val endText: String,
    val isTodo: Boolean,
    val participantsText: String,
)

private fun parseAndBuild(
    sheet: EventEditorSheet,
    form: EditorFormState,
): CalendarItem? {
    val title = form.title.trim()
    if (title.isEmpty()) return null

    val start = try {
        LocalTime.parse(form.startText.trim(), TimeFmt)
    } catch (_: DateTimeParseException) {
        return null
    }
    val end = try {
        LocalTime.parse(form.endText.trim(), TimeFmt)
    } catch (_: DateTimeParseException) {
        return null
    }
    if (!end.isAfter(start)) return null

    val participants = form.participantsText
        .split(",", "，")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    return when (sheet) {
        is EventEditorSheet.Create -> CalendarItem(
            title = title,
            date = sheet.date,
            start = start,
            end = end,
            isTodo = form.isTodo,
            completed = false,
            participants = participants,
        )

        is EventEditorSheet.Edit -> sheet.item.copy(
            title = title,
            start = start,
            end = end,
            isTodo = form.isTodo,
            completed = if (form.isTodo) sheet.item.completed else false,
            participants = participants,
        )

        EventEditorSheet.Hidden -> null
    }
}
