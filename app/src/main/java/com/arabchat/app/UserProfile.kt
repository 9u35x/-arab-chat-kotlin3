package com.arabchat.app

data class UserProfile(
    var uid: String = "",
    val username: String = "",
    val email: String? = null,
    val avatarUrl: String? = null
)
