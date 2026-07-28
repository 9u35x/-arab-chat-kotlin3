package com.arabchat.app

data class UserProfile(
    var uid: String = "",
    val displayName: String = "",
    val username: String = "",
    val bio: String = "",
    val gender: String = "", // "male", "female", or ""
    val email: String? = null,
    val avatarUrl: String? = null
) {
    fun bestName(): String {
        if (displayName.isNotBlank()) return displayName
        if (username.isNotBlank()) return username
        return email?.substringBefore("@") ?: "مستخدم"
    }
}
