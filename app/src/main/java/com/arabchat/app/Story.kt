package com.arabchat.app

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Story(
    var id: String = "",
    val userId: String = "",
    val userName: String = "",
    val avatarUrl: String = "",
    val mediaUrl: String = "",
    val mediaType: String = "image",
    val text: String = "",
    val createdAtMs: Long = 0L,
    val expiresAtMs: Long = 0L,
    /** active | expired | posted */
    val status: String = "active",
    @ServerTimestamp
    val createdAt: Date? = null
) {
    fun isActive(now: Long = System.currentTimeMillis()): Boolean =
        status == "active" && expiresAtMs > now
}

data class Post(
    var id: String = "",
    val userId: String = "",
    val userName: String = "",
    val avatarUrl: String = "",
    val mediaUrl: String = "",
    val text: String = "",
    val fromStoryId: String = "",
    val createdAtMs: Long = 0L,
    @ServerTimestamp
    val createdAt: Date? = null
)
