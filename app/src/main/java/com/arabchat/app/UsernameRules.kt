package com.arabchat.app

import com.google.firebase.firestore.FirebaseFirestore

object UsernameRules {
    fun isValid(username: String): Boolean {
        if (username.isEmpty()) return true
        return username.matches(Regex("^[a-zA-Z0-9_]{3,20}$"))
    }

    fun checkUnique(username: String, myUid: String, onResult: (Boolean) -> Unit) {
        if (username.isEmpty()) {
            onResult(true)
            return
        }
        FirebaseFirestore.getInstance()
            .collection("users")
            .whereEqualTo("username", username)
            .limit(5)
            .get()
            .addOnSuccessListener { snap ->
                val taken = snap.documents.any { it.id != myUid }
                onResult(!taken)
            }
            .addOnFailureListener { onResult(true) }
    }
}
