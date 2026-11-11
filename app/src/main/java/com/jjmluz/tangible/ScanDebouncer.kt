package com.jjmluz.tangible

/**
 * Tells a new scan from the same tag being read again. A phone held on a tag loses and finds it
 * again every second or so, and each read arrives as a scan; playing on each one restarts the
 * album a few seconds after it started.
 */
class ScanDebouncer(
    private val windowMs: Long = 5000,
    private val clock: () -> Long = android.os.SystemClock::elapsedRealtime
) {
    private var lastKey: String? = null
    private var lastReadAtMs = 0L

    /**
     * True if [key] was read less than [windowMs] ago. Every read counts, so a phone left on a tag
     * plays it once however long it stays there.
     */
    @Synchronized
    fun isRepeat(key: String): Boolean {
        val now = clock()
        val repeat = key == lastKey && now - lastReadAtMs < windowMs
        lastKey = key
        lastReadAtMs = now
        return repeat
    }
}
