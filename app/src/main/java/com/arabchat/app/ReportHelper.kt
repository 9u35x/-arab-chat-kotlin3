package com.arabchat.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

object ReportHelper {
    fun submitReport(
        reportedUserId: String,
        reason: String,
        details: String = "",
        chatId: String = "",
        messageId: String = "",
        onDone: (Boolean, String?) -> Unit
    ) {
        val me = FirebaseAuth.getInstance().currentUser?.uid
        if (me.isNullOrBlank()) {
            onDone(false, "يجب تسجيل الدخول")
            return
        }
        if (reportedUserId.isBlank() || reportedUserId == me) {
            onDone(false, "لا يمكن الإبلاغ")
            return
        }
        val data = hashMapOf(
            "reporterId" to me,
            "reportedUserId" to reportedUserId,
            "reason" to reason,
            "details" to details,
            "chatId" to chatId,
            "messageId" to messageId,
            "status" to "new",
            "createdAt" to FieldValue.serverTimestamp()
        )
        FirebaseFirestore.getInstance().collection("reports").add(data)
            .addOnSuccessListener { onDone(true, null) }
            .addOnFailureListener { e -> onDone(false, e.message) }
    }
}
