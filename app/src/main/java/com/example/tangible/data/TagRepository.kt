package com.example.tangible.data

import android.content.SharedPreferences
import com.example.tangible.SavedTag
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TagRepository(private val prefs: SharedPreferences) {
    private val gson = Gson()
    private val key = "saved_tags_v1"

    fun loadAll(): List<SavedTag> {
        val json = prefs.getString(key, null) ?: return emptyList()
        val type = object : TypeToken<List<SavedTag>>() {}.type
        return try { gson.fromJson(json, type) ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    fun save(tag: SavedTag) {
        val tags = loadAll().toMutableList()
        tags.add(0, tag)
        prefs.edit().putString(key, gson.toJson(tags)).apply()
    }

    fun exportJson(): String = gson.toJson(loadAll())
}
