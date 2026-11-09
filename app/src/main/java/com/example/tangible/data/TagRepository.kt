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

    fun delete(tagIds: Set<String>) {
        val tags = loadAll().filter { it.id !in tagIds }
        prefs.edit().putString(key, gson.toJson(tags)).apply()
    }

    fun exportJson(): String = gson.toJson(loadAll())

    fun importJson(json: String): Boolean {
        val type = object : TypeToken<List<SavedTag>>() {}.type
        return try {
            val importedTags: List<SavedTag> = gson.fromJson(json, type) ?: return false
            val currentTags = loadAll()
            
            // Merge tags by ID, keeping the latest one if duplicates exist (optional)
            // Or just replace everything. Usually for Restore, replacing or appending is fine.
            // Let's go with merging: new tags + existing tags that don't exist in new.
            val currentIds = currentTags.map { it.id }.toSet()
            val newTags = importedTags.filter { it.id !in currentIds }
            val merged = importedTags + currentTags.filter { it.id !in importedTags.map { it.id }.toSet() }
            
            prefs.edit().putString(key, gson.toJson(merged)).apply()
            true
        } catch (e: Exception) {
            false
        }
    }
}
