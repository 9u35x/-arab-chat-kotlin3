package com.arabchat.app

data class UserProfile(
    var uid: String = "",
    val displayName: String = "",
    val username: String = "",
    val bio: String = "",
    val gender: String = "", // "male", "female", or ""
    val email: String? = null, // stored privately — never show in UI
    val avatarUrl: String? = null
) {
    /** Public display name only — never returns email. */
    fun bestName(): String {
        if (displayName.isNotBlank()) return displayName
        if (username.isNotBlank()) return username
        return "مستخدم"
    }
}
