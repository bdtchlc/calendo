package com.calendo.app.sync

import com.calendo.app.data.CalendarItem
import com.calendo.app.ui.theme.BlockPalette
import com.google.api.client.util.DateTime as ApiDateTime
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventAttendee
import com.google.api.services.calendar.model.EventDateTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

internal object GoogleCalendarMapper {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /**
     * 参与人分隔符（须为可打印字符；不可用控制字符，否则 Google API 返回 400 invalid）。
     * 若姓名本身含此片段，请改用「详情描述」备注参与人。
     */
    private const val PARTICIPANT_DELIM = "|||"

    fun fromGoogleEvent(event: Event): CalendarItem? {
        val start = event.start ?: return null
        val end = event.end ?: return null
        val times = parseEventTimes(start, end) ?: return null

        val priv = event.extendedProperties?.`private` ?: emptyMap()
        val isTodo = priv["calendo_isTodo"]?.toBooleanStrictOrNull() ?: false
        val completed = priv["calendo_completed"]?.toBooleanStrictOrNull() ?: false
        val priority = priv["calendo_priority"]?.takeIf { it.isNotBlank() }
        val paletteIndex = priv["calendo_palette"]?.toIntOrNull()?.mod(BlockPalette.size) ?: 0

        val rawParticipants = priv["calendo_participants"]
        val participantsFromPriv = rawParticipants?.let { splitStoredParticipants(it) }
        val participants = if (!participantsFromPriv.isNullOrEmpty()) {
            participantsFromPriv
        } else {
            event.attendees.orEmpty().mapNotNull { a ->
                a.displayName?.takeIf { it.isNotBlank() } ?: a.email?.takeIf { it.isNotBlank() }
            }
        }

        return CalendarItem(
            id = UUID.randomUUID().toString(),
            title = event.summary?.takeIf { it.isNotBlank() } ?: "(无标题)",
            date = times.date,
            start = times.start,
            end = times.end,
            isTodo = isTodo,
            completed = completed,
            participants = participants,
            paletteIndex = paletteIndex,
            priority = priority,
            description = event.description.orEmpty(),
            googleEventId = event.id,
        )
    }

    private data class ParsedTimes(val date: LocalDate, val start: LocalTime, val end: LocalTime)

    private fun parseEventTimes(start: EventDateTime, end: EventDateTime): ParsedTimes? {
        return when {
            start.dateTime != null && end.dateTime != null -> {
                val zs = ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(start.dateTime.value),
                    zone,
                )
                val ze = ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(end.dateTime.value),
                    zone,
                )
                val date = zs.toLocalDate()
                var st = zs.toLocalTime()
                var et = ze.toLocalTime()
                if (ze.toLocalDate() != date) {
                    et = LocalTime.of(23, 59)
                }
                if (!et.isAfter(st)) {
                    et = st.plusMinutes(15)
                }
                ParsedTimes(date, st, et)
            }
            start.date != null && end.date != null -> {
                val sd = parseApiDate(start.date)
                val endExclusive = parseApiDate(end.date)
                val endInclusive = endExclusive.minusDays(1)
                if (endInclusive.isBefore(sd)) return null
                if (!sd.isEqual(endInclusive)) {
                    return null
                }
                ParsedTimes(sd, LocalTime.of(0, 0), LocalTime.of(23, 59))
            }
            else -> null
        }
    }

    private fun parseApiDate(dt: ApiDateTime): LocalDate {
        val s = dt.toStringRfc3339()
        return LocalDate.parse(s.take(10))
    }

    fun toGoogleEvent(item: CalendarItem): Event {
        val event = Event()
        event.summary = item.title
        if (item.description.isNotBlank()) {
            event.description = item.description
        }

        val zStart = ZonedDateTime.of(item.date, item.start, zone)
        var zEnd = ZonedDateTime.of(item.date, item.end, zone)
        if (!zEnd.isAfter(zStart)) {
            zEnd = zStart.plusMinutes(15)
        }

        val tz = zone.id
        event.start = EventDateTime()
            .setDateTime(ApiDateTime(zStart.toInstant().toEpochMilli()))
            .setTimeZone(tz)
        event.end = EventDateTime()
            .setDateTime(ApiDateTime(zEnd.toInstant().toEpochMilli()))
            .setTimeZone(tz)

        val priv = mutableMapOf(
            "calendo_isTodo" to item.isTodo.toString(),
            "calendo_completed" to item.completed.toString(),
            "calendo_palette" to item.paletteIndex.toString(),
        )
        item.priority?.let { priv["calendo_priority"] = it }

        if (item.participants.isNotEmpty()) {
            priv["calendo_participants"] = item.participants.joinToString(PARTICIPANT_DELIM)
        }

        event.extendedProperties = Event.ExtendedProperties().setPrivate(priv)

        // Calendar API：每个 attendee 必须有 email；仅有昵称的名字写入 extendedProperties，不能填 attendees。
        val emailAttendees = item.participants.filter { looksLikeEmail(it) }.distinct()
        if (emailAttendees.isNotEmpty()) {
            event.attendees = emailAttendees.map { mail ->
                EventAttendee().setEmail(mail.trim())
            }
        }

        return event
    }

    private fun looksLikeEmail(s: String): Boolean {
        val t = s.trim()
        return t.contains('@') && t.contains('.')
    }

    /** 兼容旧版控制字符分隔与逗号。 */
    private fun splitStoredParticipants(raw: String): List<String> {
        val parts = when {
            raw.contains(PARTICIPANT_DELIM) -> raw.split(PARTICIPANT_DELIM)
            raw.contains("\u001f") -> raw.split("\u001f")
            else -> raw.split(",", "，")
        }
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }
}
