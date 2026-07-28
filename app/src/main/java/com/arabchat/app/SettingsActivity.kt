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

class SettingsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

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
        findViewById<TextView>(R.id.tvBlocked).setOnClickListener {
            Toast.makeText(this, R.string.blocked_soon, Toast.LENGTH_SHORT).show()
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

    companion object {
        const val PREFS_NAME = "arab_chat_settings"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
        const val KEY_LAST_SEEN = "last_seen_enabled"
        const val KEY_READ_RECEIPTS = "read_receipts_enabled"
        const val KEY_ENTER_SEND = "enter_to_send"
    }
}
