package com.calendo.app.sync

import com.calendo.app.data.CalendarItem
import com.calendo.app.ui.theme.BlockPalette
import com.google.api.services.tasks.model.Task
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Google Tasks ↔ CalendarItem（待办）。
 * Google 侧为准：标题与 notes 以服务端为准；扩展字段写在 notes 的 [Calendo Meta] 区块（纯文本）。
 */
internal object GoogleTasksMapper {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val markerLine = "---"
    private val metaHeader = "[Calendo Meta]"

    fun toGoogleTask(item: CalendarItem): Task {
        require(item.isTodo)
        val t = Task()
        t.title = item.title.ifBlank { "(无标题)" }
        t.notes = buildNotes(item)
        t.status = if (item.completed) "completed" else "needsAction"
        val zdt = ZonedDateTime.of(item.date, item.start, zone)
        t.due = zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        return t
    }

    private fun buildNotes(item: CalendarItem): String {
        val body = item.description.trim()
        val meta = buildString {
            append(markerLine).append('\n')
            append(metaHeader).append('\n')
            append("palette=").append(item.paletteIndex).append('\n')
            item.priority?.let { append("priority=").append(it).append('\n') }
            if (item.participants.isNotEmpty()) {
                append("participants=").append(item.participants.joinToString("|||")).append('\n')
            }
            append("start=").append(item.start.toString()).append('\n')
            append("end=").append(item.end.toString()).append('\n')
        }
        return if (body.isEmpty()) meta.trim()
        else body + "\n\n" + meta.trim()
    }

    /**
     * 从 Google Task 映射到本地待办；以 Google 的 title/status/due 为准。
     */
    fun fromGoogleTask(task: Task, existing: CalendarItem?): CalendarItem {
        val dueStr = task.due
        val date: LocalDate
        val start: LocalTime
        val end: LocalTime
        if (!dueStr.isNullOrBlank()) {
            val z = try {
                ZonedDateTime.parse(dueStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            } catch (_: Exception) {
                val ld = java.time.LocalDate.parse(dueStr.take(10))
                ZonedDateTime.of(ld, LocalTime.of(9, 0), zone)
            }
            date = z.toLocalDate()
            start = z.toLocalTime()
            end = start.plusMinutes(30)
        } else {
            date = LocalDate.now()
            start = LocalTime.of(9, 0)
            end = LocalTime.of(9, 30)
        }

        val parsed = parseMetaNotes(task.notes ?: "")
        val titleGoogle = task.title?.trim()?.ifEmpty { "(无标题)" } ?: "(无标题)"
        val completed = task.status == "completed"

        val palette = parsed.palette
            ?: existing?.paletteIndex
            ?: kotlin.math.abs(titleGoogle.hashCode()).mod(BlockPalette.size)
        val priority = parsed.priority ?: existing?.priority
        val participants = if (parsed.participants.isNotEmpty()) {
            parsed.participants
        } else {
            existing?.participants ?: emptyList()
        }
        val descBody = parsed.userDescription

        var st = parsed.start ?: start
        var en = parsed.end ?: end
        if (!en.isAfter(st)) en = st.plusMinutes(15)

        return CalendarItem(
            id = existing?.id ?: UUID.randomUUID().toString(),
            title = titleGoogle,
            date = date,
            start = st,
            end = en,
            isTodo = true,
            completed = completed,
            participants = participants,
            paletteIndex = palette,
            priority = priority,
            description = descBody,
            googleTaskId = task.id,
            googleEventId = existing?.googleEventId,
        )
    }

    private data class ParsedMeta(
        val userDescription: String,
        val palette: Int?,
        val priority: String?,
        val participants: List<String>,
        val start: LocalTime?,
        val end: LocalTime?,
    )

    private fun parseMetaNotes(notes: String): ParsedMeta {
        val idx = notes.indexOf(markerLine)
        val userPart = if (idx >= 0) notes.substring(0, idx).trim() else notes.trim()
        val metaPart = if (idx >= 0) notes.substring(idx) else ""

        var palette: Int? = null
        var priority: String? = null
        var participants: List<String> = emptyList()
        var start: LocalTime? = null
        var end: LocalTime? = null

        metaPart.lines().forEach { line ->
            when {
                line.startsWith("palette=") -> palette = line.removePrefix("palette=").trim().toIntOrNull()
                line.startsWith("priority=") -> priority = line.removePrefix("priority=").trim().takeIf { it.isNotEmpty() }
                line.startsWith("participants=") ->
                    participants = line.removePrefix("participants=")
                        .split("|||")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                line.startsWith("start=") -> start = LocalTime.parse(line.removePrefix("start=").trim())
                line.startsWith("end=") -> end = LocalTime.parse(line.removePrefix("end=").trim())
            }
        }

        return ParsedMeta(
            userDescription = userPart,
            palette = palette,
            priority = priority,
            participants = participants,
            start = start,
            end = end,
        )
    }
}
