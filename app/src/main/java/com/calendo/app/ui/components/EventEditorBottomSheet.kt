package com.calendo.app.ui.components

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.calendo.app.data.CalendarItem
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private fun minuteOfDay(t: LocalTime): Int = t.hour * 60 + t.minute

private fun timeFromMinuteOfDay(m: Int): LocalTime {
    val h = (m / 60).coerceIn(0, 23)
    val min = (m % 60).coerceIn(0, 59)
    return LocalTime.of(h, min)
}

private val EditorDateFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

private fun localDateToMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun millisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

private fun readContactDisplayName(context: Context, contactUri: Uri): String? =
    context.contentResolver.query(
        contactUri,
        arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val idx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
        if (idx < 0) return@use null
        cursor.getString(idx)?.takeIf { it.isNotBlank() }
    }

private fun appendParticipant(current: String, name: String): String {
    val trimmedName = name.trim()
    if (trimmedName.isEmpty()) return current
    val parts = current.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
    if (parts.none { it.equals(trimmedName, ignoreCase = true) }) {
        parts.add(trimmedName)
    }
    return parts.joinToString("，")
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
    val context = LocalContext.current

    val initial = when (state) {
        is EventEditorSheet.Create -> {
            val end = state.start.plusHours(1)
            EditorFormState(
                eventDate = state.date,
                title = "",
                startMin = minuteOfDay(state.start),
                endMin = minuteOfDay(end),
                isTodo = false,
                participantsText = "",
                priority = null,
                description = "",
            )
        }

        is EventEditorSheet.Edit -> EditorFormState(
            eventDate = state.item.date,
            title = state.item.title,
            startMin = minuteOfDay(state.item.start),
            endMin = minuteOfDay(state.item.end),
            isTodo = state.item.isTodo,
            participantsText = state.item.participants.joinToString("，"),
            priority = state.item.priority,
            description = state.item.description,
        )

        EventEditorSheet.Hidden -> error("unreachable")
    }

    var form by remember(state) { mutableStateOf(initial) }
    var errorText by remember(state) { mutableStateOf<String?>(null) }
    var datePickerVisible by remember(state) { mutableStateOf(false) }

    val pickContact = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = readContactDisplayName(context, uri)
        if (name != null) {
            form = form.copy(participantsText = appendParticipant(form.participantsText, name))
        } else {
            errorText = "未能读取联系人姓名，请手动输入。"
        }
    }

    LaunchedEffect(state) {
        form = initial
        errorText = null
        datePickerVisible = false
    }

    if (datePickerVisible) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = localDateToMillis(form.eventDate),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerVisible = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { ms ->
                            form = form.copy(eventDate = millisToLocalDate(ms))
                        }
                        datePickerVisible = false
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { datePickerVisible = false }) {
                    Text("取消")
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
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

            Text(text = "日期", style = MaterialTheme.typography.labelMedium)
            TextButton(
                onClick = { datePickerVisible = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(form.eventDate.format(EditorDateFmt))
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

            Text(text = "参与人", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = form.participantsText,
                    onValueChange = { form = form.copy(participantsText = it) },
                    modifier = Modifier.weight(1f),
                    label = { Text("可选，逗号或中文逗号分隔") },
                    minLines = 2,
                )
                IconButton(
                    onClick = { pickContact.launch(null) },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "从通讯录添加参与人")
                }
            }

            OutlinedTextField(
                value = form.description,
                onValueChange = { form = form.copy(description = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("详情描述") },
                minLines = 3,
                maxLines = 8,
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
    val eventDate: LocalDate,
    val title: String,
    val startMin: Int,
    val endMin: Int,
    val isTodo: Boolean,
    val participantsText: String,
    val priority: String?,
    val description: String,
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

    val description = form.description.trim()
    val palette = kotlin.math.abs(title.hashCode()) % com.calendo.app.ui.theme.BlockPalette.size

    return when (sheet) {
        is EventEditorSheet.Create -> CalendarItem(
            title = title,
            date = form.eventDate,
            start = start,
            end = end,
            isTodo = form.isTodo,
            completed = false,
            participants = participants,
            paletteIndex = palette,
            priority = form.priority,
            description = description,
        )

        is EventEditorSheet.Edit -> sheet.item.copy(
            title = title,
            date = form.eventDate,
            start = start,
            end = end,
            isTodo = form.isTodo,
            completed = if (form.isTodo) sheet.item.completed else false,
            participants = participants,
            priority = form.priority,
            description = description,
        )

        EventEditorSheet.Hidden -> null
    }
}
