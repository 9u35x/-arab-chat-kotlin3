package com.arabchat.app

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Chat(
    var id: String = "",
    val type: String = "direct",
    val name: String? = null,
    val description: String? = null,
    val avatarUrl: String? = null,
    val participants: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val admins: List<String> = emptyList(),
    val lastMessage: String = "",
    /** Firestore may store numbers as Long — keep as Any */
    val unreadCounts: Map<String, Any>? = null,
    @ServerTimestamp
    val lastMessageTime: Date? = null
) {
    fun titleFor(currentUid: String): String {
        return when (type) {
            "group" -> name ?: "مجموعة"
            "channel" -> name ?: "قناة"
            else -> {
                val otherUid = participants.firstOrNull { it != currentUid }
                participantNames[otherUid] ?: "محادثة"
            }
        }
    }

    fun unreadFor(uid: String): Int {
        val v = unreadCounts?.get(uid) ?: return 0
        return when (v) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: 0
            else -> 0
        }
    }

    fun isChannel(): Boolean = type == "channel"
    fun isGroup(): Boolean = type == "group"
}
