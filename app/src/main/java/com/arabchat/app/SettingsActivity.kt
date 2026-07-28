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

/**
 * App settings: notification preference (local), about, profile shortcut, logout.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        auth = FirebaseAuth.getInstance()

        val tvBack: TextView = findViewById(R.id.tvBack)
        val switchNotifications: Switch = findViewById(R.id.switchNotifications)
        val tvOpenProfile: TextView = findViewById(R.id.tvOpenProfile)
        val tvAbout: TextView = findViewById(R.id.tvAbout)
        val tvPrivacy: TextView = findViewById(R.id.tvPrivacy)
        val tvClearCache: TextView = findViewById(R.id.tvClearCache)
        val tvLogout: TextView = findViewById(R.id.tvLogoutSettings)
        val tvVersion: TextView = findViewById(R.id.tvVersion)

        tvBack.setOnClickListener { finish() }

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        switchNotifications.isChecked = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_NOTIFICATIONS, isChecked).apply()
            val msg = if (isChecked) R.string.notifications_enabled else R.string.notifications_disabled
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        tvOpenProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        tvAbout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setMessage(R.string.about_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        tvPrivacy.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.privacy_title)
                .setMessage(R.string.privacy_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        tvClearCache.setOnClickListener {
            try {
                cacheDir?.listFiles()?.forEach { it.deleteRecursively() }
                externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
                Toast.makeText(this, R.string.cache_cleared, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, e.message ?: "Error", Toast.LENGTH_SHORT).show()
            }
        }

        tvLogout.setOnClickListener {
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
    }
}
