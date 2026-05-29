package com.example.tangible.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamingAvailabilityRepositoryTest {

    @Test
    fun `resolveService correctly parses subscription`() {
        val result = StreamingAvailabilityRepository.resolveService("nf", "s:12345")
        assertEquals("s", result?.typeCode)
        assertEquals("Netflix (Subscription)", result?.displayName)
        assertEquals("https://www.netflix.com/title/12345", result?.url)
    }

    @Test
    fun `resolveService correctly parses free`() {
        val result = StreamingAvailabilityRepository.resolveService("pv", "f:B012345678")
        assertEquals("f", result?.typeCode)
        assertEquals("Prime Video (Free)", result?.displayName)
        assertEquals("https://app.primevideo.com/detail/B012345678", result?.url)
    }

    @Test
    fun `resolveService correctly parses rent`() {
        val result = StreamingAvailabilityRepository.resolveService("dp", "r:abc-123")
        assertEquals("r", result?.typeCode)
        assertEquals("Disney+ (Rent)", result?.displayName)
        assertEquals("https://www.disneyplus.com/browse/entity-abc-123", result?.url)
    }

    @Test
    fun `resolveService correctly parses buy`() {
        val result = StreamingAvailabilityRepository.resolveService("mx", "b:xyz-789")
        assertEquals("b", result?.typeCode)
        assertEquals("HBO Max (Buy)", result?.displayName)
        assertEquals("https://play.max.com/movie/xyz-789", result?.url)
    }

    @Test
    fun `resolveService handles legacy tags without type prefix`() {
        val result = StreamingAvailabilityRepository.resolveService("at", "umc.cmc.12345")
        assertNull(result?.typeCode)
        assertEquals("Apple TV+", result?.displayName)
        assertEquals("https://tv.apple.com/movie/umc.cmc.12345", result?.url)
    }

    @Test
    fun `resolveService returns null for unknown short key`() {
        val result = StreamingAvailabilityRepository.resolveService("unknown", "s:123")
        assertNull(result)
    }

    @Test
    fun `stremio resolution works`() {
        val result = StreamingAvailabilityRepository.resolveService("sm", "tt1234567")
        assertNull(result?.typeCode)
        assertEquals("Stremio", result?.displayName)
        assertEquals("stremio://detail/movie/tt1234567", result?.url)
    }
}
