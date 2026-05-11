package com.calendo.app.sync

import android.content.Context
import com.calendo.app.data.CalendarItem
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime as ApiDateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.tasks.TasksScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Google Calendar API v3：先推送本地变更，再拉取合并（适配单机日历模型）。
 */
class GoogleCalendarSyncEngine {

    private val transport: HttpTransport by lazy { NetHttpTransport() }
    private val jsonFactory by lazy { GsonFactory.getDefaultInstance() }

    suspend fun upsertRemote(context: Context, item: CalendarItem): Result<CalendarItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(!item.isTodo) { "待办请使用 Google Tasks 同步" }
                val account = GoogleSignIn.getLastSignedInAccount(context)
                    ?: error("未登录 Google")
                val calendar = buildCalendarClient(context, account.email!!)
                if (item.googleEventId.isNullOrBlank()) {
                    val body = GoogleCalendarMapper.toGoogleEvent(item)
                    val created = calendar.events().insert(PRIMARY, body).execute()
                    item.copy(googleEventId = created.id)
                } else {
                    val body = GoogleCalendarMapper.toGoogleEvent(item)
                    calendar.events().patch(PRIMARY, item.googleEventId, body).execute()
                    item
                }
            }
        }

    suspend fun deleteRemoteEvent(context: Context, googleEventId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                    ?: error("未登录 Google")
                val calendar = buildCalendarClient(context, account.email!!)
                calendar.events().delete(PRIMARY, googleEventId).execute()
                Unit
            }
        }

    suspend fun performFullSync(context: Context, localItems: List<CalendarItem>): Result<Pair<List<CalendarItem>, String>> =
        withContext(Dispatchers.IO) {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                    ?: return@withContext Result.failure(IllegalStateException("未登录 Google"))
                val email = account.email ?: return@withContext Result.failure(
                    IllegalStateException("Google 账号缺少邮箱信息"),
                )
                val calendar = buildCalendarClient(context, email)

                val working = localItems
                    .filterNot { it.isTodo }
                    .map { it.copy(googleEventId = it.googleEventId?.trim()?.takeIf { id -> id.isNotEmpty() }) }
                    .toMutableList()

                val pushErrors = mutableListOf<String>()
                for (item in working.toList()) {
                    val result = runCatching {
                        when {
                            item.googleEventId.isNullOrBlank() -> {
                                val created = calendar.events().insert(
                                    PRIMARY,
                                    GoogleCalendarMapper.toGoogleEvent(item),
                                ).execute()
                                item.copy(googleEventId = created.id)
                            }
                            else -> {
                                try {
                                    calendar.events().patch(
                                        PRIMARY,
                                        item.googleEventId,
                                        GoogleCalendarMapper.toGoogleEvent(item),
                                    ).execute()
                                    item
                                } catch (e: GoogleJsonResponseException) {
                                    if (e.statusCode == 404) {
                                        val created = calendar.events().insert(
                                            PRIMARY,
                                            GoogleCalendarMapper.toGoogleEvent(
                                                item.copy(googleEventId = null),
                                            ),
                                        ).execute()
                                        item.copy(googleEventId = created.id)
                                    } else {
                                        throw e
                                    }
                                }
                            }
                        }
                    }
                    result.onSuccess { pushed ->
                        val idx = working.indexOfFirst { it.id == item.id }
                        if (idx >= 0) working[idx] = pushed
                    }.onFailure { e ->
                        val title = item.title.trim().ifBlank { "(无标题)" }.take(40)
                        val reason = (e as? GoogleJsonResponseException)
                            ?.let { g -> g.details?.errors?.firstOrNull()?.reason ?: g.content }
                            ?: e.message
                        pushErrors += "$title：$reason"
                    }
                }

                val remoteEvents = listEventsInWindow(calendar)
                val remoteById = remoteEvents.associateBy { it.id }
                val localByGoogle = working.filter { !it.googleEventId.isNullOrBlank() }
                    .associateBy { it.googleEventId!! }

                working.removeAll { local ->
                    local.googleEventId != null && local.googleEventId !in remoteById
                }

                val merged = mutableListOf<CalendarItem>()

                for (ev in remoteEvents) {
                    val todoFlag = ev.extendedProperties?.`private`?.get("calendo_isTodo")
                    if (todoFlag == "true") continue
                    val mapped = GoogleCalendarMapper.fromGoogleEvent(ev) ?: continue
                    val existing = localByGoogle[ev.id]
                    merged.add(
                        mapped.copy(
                            id = existing?.id ?: mapped.id,
                            paletteIndex = existing?.paletteIndex ?: mapped.paletteIndex,
                        ),
                    )
                }

                val localsWithoutGoogle = working.filter { it.googleEventId.isNullOrBlank() }
                merged.addAll(localsWithoutGoogle)

                val sorted = merged.sortedWith(
                    compareBy({ it.date }, { it.start }, { it.end }),
                )
                val msg = buildString {
                    append("日历事件 ${remoteEvents.size} 条（不含待办）；合并 ${sorted.size} 条。")
                    if (pushErrors.isNotEmpty()) {
                        append(" 上传警告（")
                        append(pushErrors.take(5).joinToString("；"))
                        append(if (pushErrors.size > 5) "…" else "")
                        append("）")
                    }
                }
                Result.success(Pair(sorted, msg))
            } catch (e: Throwable) {
                Result.failure(mapSyncException(e))
            }
        }

    private fun mapSyncException(e: Throwable): Throwable {
        val root = e.cause ?: e
        val message = when (root) {
            is UserRecoverableAuthIOException -> "需要重新授权日历权限（请在系统账号授权界面完成操作）。"
            is UserRecoverableAuthException -> "需要重新授权日历权限。"
            is GoogleJsonResponseException -> {
                val err = root.details?.errors?.firstOrNull()
                val detail = listOfNotNull(err?.reason, err?.location, err?.domain).joinToString(" · ")
                val snippet = root.content?.trim()?.take(120)?.let { " — $it" } ?: ""
                "Calendar API HTTP ${root.statusCode}${if (detail.isNotBlank()) "：$detail" else ""}$snippet"
            }
            is ApiException -> "Google Play 服务：${root.statusCode} ${root.message}"
            is GooglePlayServicesNotAvailableException -> "设备不支持或未安装 Google Play 服务。"
            else -> root.message ?: root.javaClass.simpleName
        }
        return IllegalStateException(message, e)
    }

    private fun listEventsInWindow(calendar: Calendar): List<Event> {
        val zone = ZoneId.systemDefault()
        val min = LocalDate.now().minusDays(SYNC_PAST_DAYS).atStartOfDay(zone)
        val max = LocalDate.now().plusDays(SYNC_FUTURE_DAYS).atStartOfDay(zone).plusHours(23).plusMinutes(59)
        return try {
            listEventsPages(calendar, min, max, zone, orderByStartTime = true, includeTimeZone = true)
        } catch (e: GoogleJsonResponseException) {
            if (e.statusCode != 400) throw e
            try {
                listEventsPages(calendar, min, max, zone, orderByStartTime = true, includeTimeZone = false)
            } catch (e2: GoogleJsonResponseException) {
                if (e2.statusCode != 400) throw e2
                listEventsPages(calendar, min, max, zone, orderByStartTime = false, includeTimeZone = true)
            }
        }
    }

    private fun listEventsPages(
        calendar: Calendar,
        min: ZonedDateTime,
        max: ZonedDateTime,
        zone: ZoneId,
        orderByStartTime: Boolean,
        includeTimeZone: Boolean,
    ): List<Event> {
        val out = mutableListOf<Event>()
        var pageToken: String? = null
        do {
            val req = calendar.events().list(PRIMARY)
                .setTimeMin(ApiDateTime(min.toInstant().toEpochMilli()))
                .setTimeMax(ApiDateTime(max.toInstant().toEpochMilli()))
                .setSingleEvents(true)
                .setMaxResults(250)
            if (includeTimeZone) req.setTimeZone(zone.id)
            if (orderByStartTime) req.setOrderBy("startTime")
            if (pageToken != null) req.setPageToken(pageToken)
            val resp = req.execute()
            out += resp.items.orEmpty()
            pageToken = resp.nextPageToken
        } while (pageToken != null)
        return out
    }

    private fun buildCalendarClient(context: Context, email: String): Calendar {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(CalendarScopes.CALENDAR, TasksScopes.TASKS),
        ).setSelectedAccount(
            android.accounts.Account(email, "com.google"),
        )
        return Calendar.Builder(transport, jsonFactory, credential)
            .setApplicationName(APPLICATION_NAME)
            .build()
    }

    companion object {
        private const val PRIMARY = "primary"
        private const val APPLICATION_NAME = "Calendo"
        private const val SYNC_PAST_DAYS = 90L
        private const val SYNC_FUTURE_DAYS = 365L
    }
}
