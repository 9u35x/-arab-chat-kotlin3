package com.arabchat.app

import android.app.Activity
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging

object FcmTokenSaver {
    fun save(activity: Activity) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                activity.requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        } catch (_: Exception) {
        }

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Toast.makeText(
                    activity,
                    "FCM فشل: " + (task.exception?.message ?: "?"),
                    Toast.LENGTH_LONG
                ).show()
                return@addOnCompleteListener
            }
            val token = task.result
            if (token.isNullOrBlank()) {
                Toast.makeText(activity, "FCM توكن فاضي", Toast.LENGTH_LONG).show()
                return@addOnCompleteListener
            }
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid.isNullOrBlank()) {
                Toast.makeText(activity, "FCM: لا يوجد مستخدم", Toast.LENGTH_LONG).show()
                return@addOnCompleteListener
            }
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .set(mapOf("fcmToken" to token), SetOptions.merge())
                .addOnSuccessListener {
                    Toast.makeText(activity, "تم حفظ توكن الإشعار", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(activity, "حفظ التوكن فشل: " + e.message, Toast.LENGTH_LONG).show()
                }
        }
    }
}
