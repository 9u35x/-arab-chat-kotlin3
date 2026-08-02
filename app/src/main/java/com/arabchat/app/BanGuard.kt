package com.arabchat.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object BanGuard {
    fun checkBanned(onResult: (banned: Boolean, reason: String?) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            onResult(false, null)
            return
        }
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val banned = doc.getBoolean("banned") == true
                val reason = doc.getString("bannedReason")
                onResult(banned, reason)
            }
            .addOnFailureListener { onResult(false, null) }
    }
}
