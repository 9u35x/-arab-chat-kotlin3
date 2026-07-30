package com.arabchat.app

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

object BlockManager {
    private val db get() = FirebaseFirestore.getInstance()

    fun blockUser(myUid: String, targetUid: String, onDone: (Boolean, String?) -> Unit) {
        if (myUid == targetUid) {
            onDone(false, "لا يمكن حظر نفسك")
            return
        }
        db.collection("users").document(myUid)
            .update("blockedUsers", FieldValue.arrayUnion(targetUid))
            .addOnSuccessListener { onDone(true, null) }
            .addOnFailureListener { e ->
                // create field if missing
                db.collection("users").document(myUid)
                    .set(mapOf("blockedUsers" to listOf(targetUid)), com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener { onDone(true, null) }
                    .addOnFailureListener { onDone(false, e.message) }
            }
    }

    fun unblockUser(myUid: String, targetUid: String, onDone: (Boolean, String?) -> Unit) {
        db.collection("users").document(myUid)
            .update("blockedUsers", FieldValue.arrayRemove(targetUid))
            .addOnSuccessListener { onDone(true, null) }
            .addOnFailureListener { e -> onDone(false, e.message) }
    }

    fun loadBlocked(myUid: String, onResult: (List<String>) -> Unit) {
        db.collection("users").document(myUid).get()
            .addOnSuccessListener { snap ->
                val list = (snap.get("blockedUsers") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                onResult(list)
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    fun isBlocked(myUid: String, targetUid: String, onResult: (Boolean) -> Unit) {
        loadBlocked(myUid) { list -> onResult(targetUid in list) }
    }
}
