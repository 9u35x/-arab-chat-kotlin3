package com.arabchat.app

object SpamGuard {
    private const val MAX = 20
    private const val WINDOW_MS = 60_000L
    private val times = ArrayDeque<Long>()

    @Synchronized
    fun canSend(): Boolean {
        val now = System.currentTimeMillis()
        while (times.isNotEmpty() && now - times.first() > WINDOW_MS) {
            times.removeFirst()
        }
        if (times.size >= MAX) return false
        times.addLast(now)
        return true
    }
}
