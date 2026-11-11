package com.jjmluz.tangible

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanDebouncerTest {
    private var now = 1_000L
    private val debouncer = ScanDebouncer(windowMs = 5000, clock = { now })

    @Test
    fun `the same tag read again a moment later is a repeat`() {
        assertFalse(debouncer.isRepeat("album/a"))
        now += 900
        assertTrue(debouncer.isRepeat("album/a"))
    }

    @Test
    fun `another tag is never a repeat`() {
        assertFalse(debouncer.isRepeat("album/a"))
        now += 900
        assertFalse(debouncer.isRepeat("album/b"))
        now += 900
        assertFalse(debouncer.isRepeat("album/a"))
    }

    @Test
    fun `a phone left on the tag keeps counting as a repeat`() {
        assertFalse(debouncer.isRepeat("album/a"))
        repeat(10) {
            now += 3000
            assertTrue(debouncer.isRepeat("album/a"))
        }
    }

    @Test
    fun `scanning the same tag again later plays it again`() {
        assertFalse(debouncer.isRepeat("album/a"))
        now += 6000
        assertFalse(debouncer.isRepeat("album/a"))
    }
}
