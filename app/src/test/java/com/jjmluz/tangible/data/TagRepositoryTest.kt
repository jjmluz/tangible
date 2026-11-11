package com.jjmluz.tangible.data

import android.content.SharedPreferences
import com.jjmluz.tangible.SavedTag
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TagRepositoryTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var repository: TagRepository

    @Before
    fun setup() {
        prefs = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        every { prefs.edit() } returns editor
        repository = TagRepository(prefs)
    }

    @Test
    fun `loadAll returns empty list when no tags are saved`() {
        every { prefs.getString("saved_tags_v1", null) } returns null
        val tags = repository.loadAll()
        assertTrue(tags.isEmpty())
    }

    @Test
    fun `save adds tag to the beginning of the list`() {
        // Given an existing tag
        val existingJson = "[{\"id\":\"1\",\"title\":\"Title 1\",\"subtitle\":\"Sub 1\",\"type\":\"MUSIC\",\"url\":\"url1\",\"createdAt\":0}]"
        every { prefs.getString("saved_tags_v1", null) } returns existingJson

        // Mock fluent API
        every { editor.putString(any(), any()) } returns editor

        // When saving a new tag
        val newTag = SavedTag("2", "Title 2", "Sub 2", "MOVIE", "url2")
        repository.save(newTag)

        // Then it should be added at index 0
        verify {
            editor.putString("saved_tags_v1", match { json ->
                json.contains("\"id\":\"2\"") && json.contains("\"id\":\"1\"") &&
                        json.indexOf("\"id\":\"2\"") < json.indexOf("\"id\":\"1\"")
            })
            editor.apply()
        }
    }

    @Test
    fun `loadAll correctly deserializes tags`() {
        val json = "[{\"id\":\"123\",\"title\":\"Test Tag\",\"subtitle\":\"Test Sub\",\"type\":\"MUSIC\",\"url\":\"tangible://play\",\"createdAt\":123456789}]"
        every { prefs.getString("saved_tags_v1", null) } returns json

        val tags = repository.loadAll()
        assertEquals(1, tags.size)
        assertEquals("123", tags[0].id)
        assertEquals("Test Tag", tags[0].title)
        assertEquals("MUSIC", tags[0].type)
        assertEquals(123456789L, tags[0].createdAt)
    }
}
