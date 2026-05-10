package com.calendo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calendo.app.data.CalendarItem
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DetailDateFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailBottomSheet(
    item: CalendarItem?,
    onDismiss: () -> Unit,
    onEdit: (CalendarItem) -> Unit,
    onToggleCompleted: (CalendarItem) -> Unit,
) {
    if (item == null) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                text = item.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = buildString {
                    append(item.date.format(DetailDateFmt))
                    append(" · ")
                    append(item.start)
                    append(" – ")
                    append(item.end)
                    if (item.priority != null) {
                        append(" · ")
                        append(item.priority)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (item.participants.isNotEmpty()) {
                Text(
                    text = "参与人：" + item.participants.joinToString("，"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (item.description.isNotBlank()) {
                Text(
                    text = "详情",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (item.isTodo) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = item.completed,
                        onCheckedChange = { onToggleCompleted(item) },
                    )
                    Text(
                        text = if (item.completed) "已完成" else "标记为已完成",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        onEdit(item)
                        onDismiss()
                    },
                ) {
                    Text("编辑")
                }
            }
        }
    }
}
