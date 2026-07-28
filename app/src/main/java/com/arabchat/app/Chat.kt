package com.arabchat.app

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Chat(
    var id: String = "",
    val type: String = "direct", // "direct" or "group"
    val name: String? = null, // group name only; direct chats resolve name client-side
    val participants: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    @ServerTimestamp
    val lastMessageTime: Date? = null
) {
    fun titleFor(currentUid: String): String {
        if (type == "group") return name ?: "مجموعة"
        val otherUid = participants.firstOrNull { it != currentUid }
        return participantNames[otherUid] ?: "محادثة"
    }
}
