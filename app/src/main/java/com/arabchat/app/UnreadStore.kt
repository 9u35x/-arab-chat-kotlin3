package com.arabchat.app

import android.content.Context

/**
 * Local unread tracking — does not require Firestore write rules.
 * A chat is unread if lastMessageTime > lastReadAt and last message is not from me.
 */
object UnreadStore {
    private const val PREFS = "arab_chat_unread"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun markRead(ctx: Context, chatId: String, atMillis: Long = System.currentTimeMillis()) {
        prefs(ctx).edit().putLong("read_$chatId", atMillis).apply()
        prefs(ctx).edit().putInt("count_$chatId", 0).apply()
    }

    fun lastRead(ctx: Context, chatId: String): Long =
        prefs(ctx).getLong("read_$chatId", 0L)

    fun getCount(ctx: Context, chatId: String): Int =
        prefs(ctx).getInt("count_$chatId", 0)

    fun setCount(ctx: Context, chatId: String, count: Int) {
        prefs(ctx).edit().putInt("count_$chatId", count.coerceAtLeast(0)).apply()
    }

    fun increment(ctx: Context, chatId: String) {
        val c = getCount(ctx, chatId) + 1
        setCount(ctx, chatId, c)
    }

    /**
     * Update local counts from chat list snapshot.
     * If last message is newer than lastRead and from someone else → at least 1.
     */
    fun syncFromChats(ctx: Context, myUid: String, chats: List<Chat>) {
        for (chat in chats) {
            val msgTime = chat.lastMessageTime?.time ?: 0L
            val readAt = lastRead(ctx, chat.id)
            val sender = chat.lastMessageSenderId
            val fromOther = sender.isNotBlank() && sender != myUid
            // Fallback: if sender unknown, still mark unread when message is newer
            val isNew = msgTime > readAt && (fromOther || sender.isBlank())
            if (!isNew) {
                // keep count 0 if already read
                if (msgTime <= readAt) setCount(ctx, chat.id, 0)
            } else {
                // if we had 0, set to 1 (unknown exact count without firestore)
                if (getCount(ctx, chat.id) <= 0) setCount(ctx, chat.id, 1)
            }
        }
    }

    fun unreadFor(ctx: Context, chat: Chat, myUid: String): Int {
        val msgTime = chat.lastMessageTime?.time ?: 0L
        val readAt = lastRead(ctx, chat.id)
        val sender = chat.lastMessageSenderId
        val fromOther = sender.isNotBlank() && sender != myUid
        val isNew = msgTime > readAt && (fromOther || (sender.isBlank() && msgTime > readAt))
        if (!isNew) return 0
        val c = getCount(ctx, chat.id)
        return if (c > 0) c else 1
    }
}
