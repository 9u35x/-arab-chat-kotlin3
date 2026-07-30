package com.arabchat.app

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Message(
    var id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val type: String = "text", // "text", "image", "voice"
    val mediaUrl: String = "",
    val isTemporary: Boolean = false,
    val viewed: Boolean = false,
    val durationMs: Long = 0,
    /** "sent" | "delivered" | "read" */
    val status: String = "sent",
    @ServerTimestamp
    val timestamp: Date? = null
)
