package com.arabchat.app

/**
 * حد إرسال محلي: 20 رسالة كل 60 ثانية.
 */
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

    fun blockedMessage(): String = "تم إرسال رسائل كثيرة. انتظر دقيقة ثم أعد المحاولة."
}
