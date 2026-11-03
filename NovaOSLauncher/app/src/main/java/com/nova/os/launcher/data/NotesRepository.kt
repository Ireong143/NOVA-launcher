package com.nova.os.launcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Stores notes as a JSON file in the app's private storage. */
class NotesRepository(context: Context) {
    private val file = File(context.applicationContext.filesDir, "notes.json")

    fun load(): List<Note> = try {
        if (!file.exists()) {
            emptyList()
        } else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Note(
                    id = o.getLong("id"),
                    text = o.optString("text"),
                    updatedAt = o.optLong("updatedAt"),
                    pinned = o.optBoolean("pinned"),
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(notes: List<Note>) {
        try {
            val arr = JSONArray()
            notes.forEach {
                arr.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("text", it.text)
                        .put("updatedAt", it.updatedAt)
                        .put("pinned", it.pinned)
                )
            }
            val tmp = File(file.parentFile, "notes.json.tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(arr.toString())
                tmp.delete()
            }
        } catch (e: Exception) {
            // Ignore: the next change will retry.
        }
    }
}
