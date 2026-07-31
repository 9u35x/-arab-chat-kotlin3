package com.arabchat.app

import com.google.firebase.firestore.FirebaseFirestore

object UsernameRules {
    fun normalize(username: String): String {
        return username.trim().replace(" ", "").lowercase()
    }

    fun isValid(username: String): Boolean {
        val u = normalize(username)
        if (u.isEmpty()) return true
        return u.matches(Regex("^[a-z0-9_]{3,20}$"))
    }

    fun checkUnique(username: String, myUid: String, onResult: (Boolean) -> Unit) {
        val u = normalize(username)
        if (u.isEmpty()) {
            onResult(true)
            return
        }
        FirebaseFirestore.getInstance()
            .collection("users")
            .whereEqualTo("username", u)
            .limit(5)
            .get()
            .addOnSuccessListener { snap ->
                val taken = snap.documents.any { it.id != myUid }
                onResult(!taken)
            }
            .addOnFailureListener {
                // إذا فشل البحث: ممنوع (أحوط)
                onResult(false)
            }
    }
}
