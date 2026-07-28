package com.arabchat.app

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

object UserRepo {

    fun ensureProfile(user: FirebaseUser, onDone: (() -> Unit)? = null) {
        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("users").document(user.uid)

        docRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                val name = if (user.isAnonymous) {
                    "ضيف-${user.uid.takeLast(4)}"
                } else {
                    user.email?.substringBefore("@") ?: "مستخدم"
                }
                val data = hashMapOf(
                    "displayName" to name,
                    "username" to name.lowercase().replace(" ", ""),
                    "bio" to "",
                    "gender" to "",
                    "email" to user.email
                )
                docRef.set(data).addOnCompleteListener { onDone?.invoke() }
            } else {
                onDone?.invoke()
            }
        }.addOnFailureListener {
            onDone?.invoke()
        }
    }
}
