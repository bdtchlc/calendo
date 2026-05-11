package com.calendo.app.sync

import android.content.Context
import com.calendo.app.data.CalendarItem
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.TasksScopes
import com.google.api.services.tasks.model.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Google Tasks（待办）双向同步；与 Calendar 事件分离。
 */
class GoogleTasksSyncEngine {

    private val transport by lazy { NetHttpTransport() }
    private val jsonFactory by lazy { GsonFactory.getDefaultInstance() }

    suspend fun upsertRemote(context: Context, item: CalendarItem): Result<CalendarItem> =
        withContext(Dispatchers.IO) {
            require(item.isTodo)
            runCatching {
                val email = GoogleSignIn.getLastSignedInAccount(context)?.email
                    ?: error("未登录 Google")
                val tasks = buildTasksClient(context, email)
                val listId = defaultTaskListId(tasks)

                if (item.googleTaskId.isNullOrBlank()) {
                    val created = tasks.tasks().insert(listId, GoogleTasksMapper.toGoogleTask(item)).execute()
                    item.copy(googleTaskId = created.id)
                } else {
                    tasks.tasks().patch(listId, item.googleTaskId, GoogleTasksMapper.toGoogleTask(item)).execute()
                    item
                }
            }
        }

    suspend fun deleteRemoteTask(context: Context, googleTaskId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val email = GoogleSignIn.getLastSignedInAccount(context)?.email
                    ?: error("未登录 Google")
                val tasks = buildTasksClient(context, email)
                val listId = defaultTaskListId(tasks)
                tasks.tasks().delete(listId, googleTaskId).execute()
                Unit
            }
        }

    suspend fun performFullSync(context: Context, localTodos: List<CalendarItem>): Result<Pair<List<CalendarItem>, String>> =
        withContext(Dispatchers.IO) {
            try {
                val email = GoogleSignIn.getLastSignedInAccount(context)?.email
                    ?: return@withContext Result.failure(IllegalStateException("未登录 Google"))
                val tasks = buildTasksClient(context, email)
                val listId = defaultTaskListId(tasks)

                val working = localTodos.toMutableList()

                for (item in working.toList()) {
                    val pushed = when {
                        item.googleTaskId.isNullOrBlank() -> {
                            val created = tasks.tasks().insert(
                                listId,
                                GoogleTasksMapper.toGoogleTask(item),
                            ).execute()
                            item.copy(googleTaskId = created.id)
                        }
                        else -> {
                            tasks.tasks().patch(
                                listId,
                                item.googleTaskId,
                                GoogleTasksMapper.toGoogleTask(item),
                            ).execute()
                            item
                        }
                    }
                    val idx = working.indexOfFirst { it.id == item.id }
                    if (idx >= 0) working[idx] = pushed
                }

                val remoteTasks = listAllTasks(tasks, listId)
                val remoteById = remoteTasks.associateBy { it.id!! }
                val localByGoogle = working.filter { !it.googleTaskId.isNullOrBlank() }
                    .associateBy { it.googleTaskId!! }

                working.removeAll { local ->
                    local.googleTaskId != null && local.googleTaskId !in remoteById
                }

                val merged = mutableListOf<CalendarItem>()
                for (t in remoteTasks) {
                    if (t.id == null) continue
                    val existing = localByGoogle[t.id]
                    merged.add(
                        GoogleTasksMapper.fromGoogleTask(t, existing),
                    )
                }

                val localsWithoutGoogle = working.filter { it.googleTaskId.isNullOrBlank() }
                merged.addAll(localsWithoutGoogle)

                val sorted = merged.sortedWith(
                    compareBy({ it.date }, { it.start }, { it.end }),
                )
                Result.success(Pair(sorted, "Tasks ${remoteTasks.size} 条"))
            } catch (e: Throwable) {
                Result.failure(mapTasksException(e))
            }
        }

    private fun listAllTasks(tasks: Tasks, listId: String): List<Task> {
        val out = mutableListOf<Task>()
        var pageToken: String? = null
        do {
            val req = tasks.tasks().list(listId)
                .setShowCompleted(true)
                .setShowHidden(false)
                .setMaxResults(100)
            if (pageToken != null) req.setPageToken(pageToken)
            val resp = req.execute()
            out += resp.items.orEmpty()
            pageToken = resp.nextPageToken
        } while (pageToken != null)
        return out
    }

    private fun defaultTaskListId(tasks: Tasks): String {
        val resp = tasks.tasklists().list().setMaxResults(20).execute()
        return resp.items?.firstOrNull()?.id ?: error("找不到 Google Tasks 列表")
    }

    private fun buildTasksClient(context: Context, email: String): Tasks {
        val credential = com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
            .usingOAuth2(
                context,
                listOf(CalendarScopes.CALENDAR, TasksScopes.TASKS),
            ).setSelectedAccount(android.accounts.Account(email, "com.google"))
        return Tasks.Builder(transport, jsonFactory, credential)
            .setApplicationName(APPLICATION_NAME)
            .build()
    }

    private fun mapTasksException(e: Throwable): Throwable {
        val root = e.cause ?: e
        val message = when (root) {
            is GoogleJsonResponseException -> {
                val err = root.details?.errors?.firstOrNull()
                val detail = listOfNotNull(err?.reason, err?.location, err?.domain).joinToString(" · ")
                "Tasks API HTTP ${root.statusCode}${if (detail.isNotBlank()) "：$detail" else ""}"
            }
            else -> root.message ?: root.javaClass.simpleName
        }
        return IllegalStateException(message, e)
    }

    companion object {
        private const val APPLICATION_NAME = "Calendo"
    }
}
