package com.calendo.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calendo.app.data.CalendarItem
import com.calendo.app.sync.GoogleCalendarSyncEngine
import com.calendo.app.sync.GoogleTasksSyncEngine
import com.calendo.app.ui.components.EventEditorSheet
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

enum class CalendarSurfaceMode {
    WEEK,
    MONTH,
}

data class CalendoUiState(
    /** 全局新建/编辑日程底栏（任意 Tab 可打开）。 */
    val eventEditor: EventEditorSheet = EventEditorSheet.Hidden,
    /** 当前聚焦日期（日视图滑动、日历点选会更新）。 */
    val selectedDate: LocalDate = LocalDate.now(),
    /** 「日历」Tab 内周 / 月切换。 */
    val calendarSurfaceMode: CalendarSurfaceMode = CalendarSurfaceMode.WEEK,
    val todoFilterActive: Boolean = false,
    val items: List<CalendarItem> = sampleItems(),
    /** Google 登录邮箱（来自 Sign-In；持久会话由 Google Play 服务维护）。 */
    val googleAccountEmail: String? = null,
    /** 最近一次同步提示或错误说明。 */
    val lastSyncHint: String? = null,
    /** 正在与 Google Calendar 全量同步。 */
    val googleSyncInProgress: Boolean = false,
)

class CalendoViewModel(application: Application) : AndroidViewModel(application) {

    private val syncEngine = GoogleCalendarSyncEngine()
    private val tasksSyncEngine = GoogleTasksSyncEngine()
    private val syncMutex = Mutex()

    private val _state = MutableStateFlow(CalendoUiState())
    val state: StateFlow<CalendoUiState> = _state.asStateFlow()

    init {
        restoreGoogleAccountFromSession()
    }

    fun restoreGoogleAccountFromSession() {
        val account = GoogleSignIn.getLastSignedInAccount(getApplication())
        val email = account?.email
        if (email != null) {
            _state.update { it.copy(googleAccountEmail = email) }
        }
    }

    fun setSelectedDate(date: LocalDate) {
        _state.update { it.copy(selectedDate = date) }
    }

    fun openEventEditor(sheet: EventEditorSheet) {
        _state.update { it.copy(eventEditor = sheet) }
    }

    fun dismissEventEditor() {
        _state.update { it.copy(eventEditor = EventEditorSheet.Hidden) }
    }

    fun setCalendarSurfaceMode(mode: CalendarSurfaceMode) {
        _state.update { it.copy(calendarSurfaceMode = mode) }
    }

    fun toggleTodoFilter() {
        _state.update { it.copy(todoFilterActive = !it.todoFilterActive) }
    }

    fun toggleTodoCompleted(id: String) {
        _state.update { s ->
            s.copy(
                items = s.items.map { item ->
                    if (item.id == id && item.isTodo) item.copy(completed = !item.completed) else item
                },
            )
        }
        if (_state.value.googleAccountEmail != null) {
            _state.value.items.find { it.id == id }
                ?.takeIf { it.isTodo }
                ?.let { pushItemToGoogleAsync(it) }
        }
    }

    fun addOrUpdateItem(item: CalendarItem) {
        _state.update { s ->
            val next = s.items.filterNot { it.id == item.id } + item
            s.copy(items = next.sortedWith(compareBy({ it.date }, { it.start }, { it.end })))
        }
        if (_state.value.googleAccountEmail != null) {
            pushItemToGoogleAsync(item)
        }
    }

    private fun pushItemToGoogleAsync(item: CalendarItem) {
        viewModelScope.launch {
            syncMutex.withLock {
                val ctx = getApplication<Application>().applicationContext
                val latest = _state.value.items.find { it.id == item.id } ?: return@withLock
                val result = if (latest.isTodo) {
                    tasksSyncEngine.upsertRemote(ctx, latest)
                } else {
                    syncEngine.upsertRemote(ctx, latest)
                }
                result.fold(
                    onSuccess = { remote ->
                        _state.update { s ->
                            val next = s.items.map { if (it.id == remote.id) remote else it }
                            s.copy(
                                items = next.sortedWith(
                                    compareBy({ it.date }, { it.start }, { it.end }),
                                ),
                                lastSyncHint = if (latest.isTodo) {
                                    "已同步到 Google Tasks。"
                                } else {
                                    "已同步到 Google 日历。"
                                },
                            )
                        }
                    },
                    onFailure = { e ->
                        _state.update { s ->
                            s.copy(lastSyncHint = "已保存本地；云端失败：${e.message}")
                        }
                    },
                )
            }
        }
    }

    fun deleteItem(id: String) {
        val item = _state.value.items.find { it.id == id }
        val googleEventId = item?.googleEventId
        val googleTaskId = item?.googleTaskId
        val linkedGoogle = _state.value.googleAccountEmail != null
        _state.update { s -> s.copy(items = s.items.filterNot { it.id == id }) }
        if (!linkedGoogle || item == null) return
        viewModelScope.launch {
            syncMutex.withLock {
                val ctx = getApplication<Application>().applicationContext
                when {
                    item.isTodo && googleTaskId != null ->
                        tasksSyncEngine.deleteRemoteTask(ctx, googleTaskId).onFailure { e ->
                            _state.update { s ->
                                s.copy(lastSyncHint = "已从本地删除；Google Tasks 删除失败：${e.message}")
                            }
                        }
                    item.isTodo && googleTaskId == null && googleEventId != null ->
                        syncEngine.deleteRemoteEvent(ctx, googleEventId).onFailure { e ->
                            _state.update { s ->
                                s.copy(lastSyncHint = "已从本地删除；云端日历条目删除失败：${e.message}")
                            }
                        }
                    item.isTodo -> { /* 仅本地待办 */ }
                    googleEventId != null ->
                        syncEngine.deleteRemoteEvent(ctx, googleEventId).onFailure { e ->
                            _state.update { s ->
                                s.copy(lastSyncHint = "已从本地删除；Google 日历删除失败：${e.message}")
                            }
                        }
                }
            }
        }
    }

    /**
     * 长按拖动时间轴块后，整体平移开始/结束时间（保持时长；限制在 7:00–23:59）。
     * [deltaMinutes] 为 15 分钟对齐后的增量。
     */
    fun rescheduleItemByDrag(id: String, deltaMinutes: Int) {
        if (deltaMinutes == 0) return
        var pushed: CalendarItem? = null
        _state.update { s ->
            val item = s.items.find { it.id == id } ?: return@update s
            val dur = ChronoUnit.MINUTES.between(item.start, item.end).coerceAtLeast(15)
            val minS = LocalTime.of(7, 0)
            val maxE = LocalTime.of(23, 59)
            var ns = item.start.plusMinutes(deltaMinutes.toLong())
            var ne = ns.plusMinutes(dur)
            if (ns.isBefore(minS)) {
                ns = minS
                ne = ns.plusMinutes(dur)
            }
            if (ne.isAfter(maxE)) {
                ne = maxE
                ns = ne.minusMinutes(dur)
                if (ns.isBefore(minS)) ns = minS
            }
            if (!ne.isAfter(ns)) return@update s
            val updated = item.copy(start = ns, end = ne)
            pushed = updated
            val next = s.items.filterNot { it.id == id } + updated
            s.copy(items = next.sortedWith(compareBy({ it.date }, { it.start }, { it.end })))
        }
        if (pushed != null && _state.value.googleAccountEmail != null) {
            pushItemToGoogleAsync(pushed!!)
        }
    }

    fun setGoogleAccount(email: String?) {
        _state.update {
            it.copy(
                googleAccountEmail = email,
                lastSyncHint = email?.let { e -> "已连接：$e" }
                    ?: "已断开 Google。",
            )
        }
    }

    fun setSyncHint(text: String?) {
        _state.update { it.copy(lastSyncHint = text) }
    }

    /** 与 Google 全量同步：日历事件（非待办）+ Google Tasks（待办）。 */
    fun syncWithGoogleCalendar() {
        viewModelScope.launch {
            syncMutex.withLock {
                _state.update {
                    it.copy(
                        googleSyncInProgress = true,
                        lastSyncHint = "正在同步 Google 日历与 Tasks…",
                    )
                }
                val ctx = getApplication<Application>().applicationContext
                val snapshot = _state.value.items
                val nonTodos = snapshot.filterNot { it.isTodo }
                val todos = snapshot.filter { it.isTodo }
                val rCal = syncEngine.performFullSync(ctx, nonTodos)
                val rTask = tasksSyncEngine.performFullSync(ctx, todos)
                when {
                    rCal.isSuccess && rTask.isSuccess -> {
                        val merged = rCal.getOrThrow().first + rTask.getOrThrow().first
                        val sorted = merged.sortedWith(
                            compareBy({ it.date }, { it.start }, { it.end }),
                        )
                        val msg =
                            rCal.getOrThrow().second + " " + rTask.getOrThrow().second
                        _state.update {
                            it.copy(
                                items = sorted,
                                lastSyncHint = msg.trim(),
                                googleSyncInProgress = false,
                            )
                        }
                    }
                    rCal.isFailure && rTask.isFailure ->
                        _state.update {
                            it.copy(
                                lastSyncHint = listOfNotNull(
                                    rCal.exceptionOrNull()?.message,
                                    rTask.exceptionOrNull()?.message,
                                ).joinToString("；"),
                                googleSyncInProgress = false,
                            )
                        }
                    rCal.isFailure ->
                        _state.update {
                            it.copy(
                                lastSyncHint = rCal.exceptionOrNull()?.message ?: "日历同步失败",
                                googleSyncInProgress = false,
                            )
                        }
                    else ->
                        _state.update {
                            it.copy(
                                lastSyncHint = rTask.exceptionOrNull()?.message ?: "Tasks 同步失败",
                                googleSyncInProgress = false,
                            )
                        }
                }
            }
        }
    }
}

private fun sampleItems(): List<CalendarItem> {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    return listOf(
        CalendarItem(
            title = "昨日遗留待办（滚雪球示例）",
            date = yesterday,
            start = LocalTime.of(9, 0),
            end = LocalTime.of(9, 30),
            isTodo = true,
            completed = false,
            paletteIndex = 3,
            priority = "P1",
        ),
        CalendarItem(
            title = "采购流程讨论会",
            date = today,
            start = LocalTime.of(13, 30),
            end = LocalTime.of(14, 45),
            isTodo = false,
            participants = listOf("何叔", "银姐"),
            paletteIndex = 0,
            priority = "P1",
        ),
        CalendarItem(
            title = "管理周会",
            date = today,
            start = LocalTime.of(15, 0),
            end = LocalTime.of(16, 0),
            isTodo = true,
            completed = false,
            participants = listOf("何叔", "银姐"),
            paletteIndex = 2,
            priority = "P0",
            description = "同步本周项目进度与风险项。",
        ),
    )
}

/** Pager 页码与日期换算（中心页 = AnchorPage 表示 [anchorDate]，通常为今天）。 */
fun pageForDate(anchorDate: LocalDate, target: LocalDate, anchorPage: Int = CalendoPagerDefaults.AnchorPage): Int =
    anchorPage + ChronoUnit.DAYS.between(anchorDate, target).toInt()

fun dateForPage(anchorDate: LocalDate, page: Int, anchorPage: Int = CalendoPagerDefaults.AnchorPage): LocalDate =
    anchorDate.plusDays((page - anchorPage).toLong())

object CalendoPagerDefaults {
    const val AnchorPage = 1000
    const val PageCount = 2000
}
