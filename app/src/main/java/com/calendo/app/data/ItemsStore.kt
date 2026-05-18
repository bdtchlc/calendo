package com.calendo.app.data

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/**
 * 本地 JSON 文件持久化。
 * 使用已有 Gson 依赖，不引入额外库。
 * 写入流程：先写 .tmp，再原子 rename，防止写入中途崩溃损坏数据。
 */
internal object ItemsStore {

    private const val FILE_NAME = "calendo_items.json"

    private val gson = GsonBuilder()
        .registerTypeAdapter(
            LocalDate::class.java,
            JsonSerializer<LocalDate> { src, _, _ ->
                com.google.gson.JsonPrimitive(src.toString())
            },
        )
        .registerTypeAdapter(
            LocalDate::class.java,
            JsonDeserializer<LocalDate> { json, _, _ ->
                LocalDate.parse(json.asString)
            },
        )
        .registerTypeAdapter(
            LocalTime::class.java,
            JsonSerializer<LocalTime> { src, _, _ ->
                com.google.gson.JsonPrimitive(src.toString())
            },
        )
        .registerTypeAdapter(
            LocalTime::class.java,
            JsonDeserializer<LocalTime> { json, _, _ ->
                LocalTime.parse(json.asString)
            },
        )
        .create()

    private val listType = object : TypeToken<List<CalendarItem>>() {}.type

    suspend fun load(context: Context): List<CalendarItem> = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return@withContext emptyList()
            val raw: List<CalendarItem>? = gson.fromJson(file.readText(), listType)
            (raw ?: emptyList()).map { item ->
                item.copy(participants = item.participants ?: emptyList())
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun save(context: Context, items: List<CalendarItem>) = withContext(Dispatchers.IO) {
        try {
            val dir = context.filesDir
            val tmp = File(dir, "$FILE_NAME.tmp")
            tmp.writeText(gson.toJson(items, listType))
            tmp.renameTo(File(dir, FILE_NAME))
        } catch (_: Exception) {}
    }
}
