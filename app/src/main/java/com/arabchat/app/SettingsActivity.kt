package com.arabchat.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SettingsActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }


    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<TextView?>(R.id.tvLanguage)?.setOnClickListener {
            val langs = arrayOf(
                "العربية" to "ar",
                "English" to "en",
                "Français" to "fr",
                "Türkçe" to "tr",
                "اردو" to "ur",
                "فارسی" to "fa",
                "Deutsch" to "de",
                "Español" to "es",
                "हिन्दी" to "hi",
                "Indonesia" to "in"
            )
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.language))
                .setItems(langs.map { it.first }.toTypedArray()) { _, which ->
                    LocaleHelper.apply(this, langs[which].second)
                    recreate()
                }
                .show()
        }

        
        findViewById<TextView?>(R.id.tvAboutApp)?.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("عن التطبيق")
                .setMessage("دردشة العرب\nتطبيق محادثة عربية\nالإصدار 1.0")
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        findViewById<TextView?>(R.id.tvPrivacyPolicy)?.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("سياسة الخصوصية")
                .setMessage("نحترم خصوصيتك.\nلا نبيع بياناتك.\nالرسائل تُخزَّن لتقديم خدمة الدردشة فقط.\nيمكنك طلب حذف حسابك عبر الإبلاغ للإدارة.")
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        findViewById<TextView?>(R.id.tvChangePassword)?.setOnClickListener { changePassword() }

        auth = FirebaseAuth.getInstance()
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        findViewById<TextView>(R.id.tvBack).setOnClickListener { finish() }

        val switchNotifications: Switch = findViewById(R.id.switchNotifications)
        val switchLastSeen: Switch = findViewById(R.id.switchLastSeen)
        val switchReadReceipts: Switch = findViewById(R.id.switchReadReceipts)
        val switchEnterToSend: Switch = findViewById(R.id.switchEnterToSend)

        switchNotifications.isChecked = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        switchLastSeen.isChecked = prefs.getBoolean(KEY_LAST_SEEN, true)
        switchReadReceipts.isChecked = prefs.getBoolean(KEY_READ_RECEIPTS, true)
        switchEnterToSend.isChecked = prefs.getBoolean(KEY_ENTER_SEND, false)

        switchNotifications.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_NOTIFICATIONS, checked).apply()
            Toast.makeText(
                this,
                if (checked) R.string.notifications_enabled else R.string.notifications_disabled,
                Toast.LENGTH_SHORT
            ).show()
        }
        switchLastSeen.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_LAST_SEEN, checked).apply()
        }
        switchReadReceipts.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_READ_RECEIPTS, checked).apply()
        }
        switchEnterToSend.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_ENTER_SEND, checked).apply()
        }

        findViewById<TextView>(R.id.tvOpenProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        findViewById<TextView>(R.id.tvBlocked).setOnClickListener {
            showBlockedList()
        }

        findViewById<TextView>(R.id.tvChannels).setOnClickListener {
            startActivity(Intent(this, ChannelsActivity::class.java))
        }
        findViewById<TextView>(R.id.tvAbout).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setMessage(R.string.about_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        findViewById<TextView>(R.id.tvPrivacy).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.privacy_title)
                .setMessage(R.string.privacy_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        
        findViewById<TextView>(R.id.tvClearCache).setOnClickListener {
            try {
                cacheDir?.listFiles()?.forEach { it.deleteRecursively() }
                externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
                Toast.makeText(this, R.string.cache_cleared, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, e.message ?: "Error", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<TextView>(R.id.tvLogoutSettings).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.logout)
                .setMessage(R.string.logout_confirm)
                .setPositiveButton(R.string.logout) { _, _ ->
                    auth.signOut()
                    startActivity(Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        val tvVersion: TextView = findViewById(R.id.tvVersion)
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            tvVersion.text = getString(R.string.version_format, pInfo.versionName ?: "1.0")
        } catch (_: Exception) {
            tvVersion.text = getString(R.string.version_format, "1.0")
        }
    }


    private fun showBlockedList() {
        val me = auth.currentUser?.uid
        if (me == null) {
            Toast.makeText(this, "يجب تسجيل الدخول", Toast.LENGTH_SHORT).show()
            return
        }
        BlockManager.loadBlocked(me) { ids ->
            if (ids.isEmpty()) {
                Toast.makeText(this, R.string.no_blocked_users, Toast.LENGTH_SHORT).show()
                return@loadBlocked
            }
            // Load names
            val db = FirebaseFirestore.getInstance()
            val names = mutableMapOf<String, String>()
            var pending = ids.size
            for (id in ids) {
                db.collection("users").document(id).get()
                    .addOnSuccessListener { snap ->
                        val p = snap.toObject(UserProfile::class.java)
                        names[id] = p?.bestName() ?: id.take(6)
                        pending--
                        if (pending <= 0) presentBlockedDialog(me, ids, names)
                    }
                    .addOnFailureListener {
                        names[id] = id.take(6)
                        pending--
                        if (pending <= 0) presentBlockedDialog(me, ids, names)
                    }
            }
        }
    }

    private fun presentBlockedDialog(me: String, ids: List<String>, names: Map<String, String>) {
        val labels = ids.map { names[it] ?: it }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.blocked_users)
            .setItems(labels) { _, which ->
                val target = ids[which]
                val name = names[target] ?: target
                AlertDialog.Builder(this)
                    .setTitle(R.string.unblock_user)
                    .setMessage(getString(R.string.unblock_confirm, name))
                    .setPositiveButton(R.string.unblock_user) { _, _ ->
                        BlockManager.unblockUser(me, target) { ok, err ->
                            if (ok) Toast.makeText(this, R.string.user_unblocked, Toast.LENGTH_SHORT).show()
                            else Toast.makeText(this, err ?: "خطأ", Toast.LENGTH_LONG).show()
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        const val PREFS_NAME = "arab_chat_settings"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
        const val KEY_LAST_SEEN = "last_seen_enabled"
        const val KEY_READ_RECEIPTS = "read_receipts_enabled"
        const val KEY_ENTER_SEND = "enter_to_send"
    }
    private fun changePassword() {
        val input = android.widget.EditText(this).apply {
            hint = "كلمة المرور الجديدة"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val input2 = android.widget.EditText(this).apply {
            hint = "تأكيد كلمة المرور"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
            addView(input)
            addView(input2)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("تغيير كلمة المرور")
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val p1 = input.text.toString()
                val p2 = input2.text.toString()
                if (p1.length < 6) {
                    Toast.makeText(this, "6 أحرف على الأقل", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (p1 != p2) {
                    Toast.makeText(this, "غير متطابقتين", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    ?.updatePassword(p1)
                    ?.addOnSuccessListener {
                        Toast.makeText(this, "تم تغيير كلمة المرور", Toast.LENGTH_SHORT).show()
                    }
                    ?.addOnFailureListener { e ->
                        Toast.makeText(this, e.message ?: "فشل", Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
