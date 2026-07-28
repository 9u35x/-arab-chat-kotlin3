package com.arabchat.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * ProfileActivity works in two modes:
 *
 * 1) Contact / group info mode — launched from ChatActivity with a "name"
 *    (and optional "isGroup") extra. Shows a read-only summary of the chat.
 *
 * 2) My profile mode — launched without extras (e.g. from HomeActivity).
 *    Shows the signed-in user's own profile: avatar (uploaded to Supabase
 *    Storage), editable username (saved to Firestore "users/{uid}") and a
 *    logout action.
 */
class ProfileActivity : AppCompatActivity() {

    private var isOwnProfile = false
    private var currentAvatarUrl: String? = null

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var tvBack: TextView
    private lateinit var tvProfileTitle: TextView
    private lateinit var ivProfileAvatar: ImageView
    private lateinit var tvProfileAvatar: TextView
    private lateinit var tvChangePhoto: TextView
    private lateinit var progressPhoto: ProgressBar
    private lateinit var tvProfileName: TextView
    private lateinit var etProfileName: EditText
    private lateinit var tvProfileSubtitle: TextView
    private lateinit var tvSaveProfile: TextView
    private lateinit var progressSave: ProgressBar
    private lateinit var tvLogoutProfile: TextView

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) uploadAvatar(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        tvBack = findViewById(R.id.tvBack)
        tvProfileTitle = findViewById(R.id.tvProfileTitle)
        ivProfileAvatar = findViewById(R.id.ivProfileAvatar)
        tvProfileAvatar = findViewById(R.id.tvProfileAvatar)
        tvChangePhoto = findViewById(R.id.tvChangePhoto)
        progressPhoto = findViewById(R.id.progressPhoto)
        tvProfileName = findViewById(R.id.tvProfileName)
        etProfileName = findViewById(R.id.etProfileName)
        tvProfileSubtitle = findViewById(R.id.tvProfileSubtitle)
        tvSaveProfile = findViewById(R.id.tvSaveProfile)
        progressSave = findViewById(R.id.progressSave)
        tvLogoutProfile = findViewById(R.id.tvLogoutProfile)

        tvBack.setOnClickListener { finish() }

        val name = intent.getStringExtra("name")
        if (name != null) {
            setupContactMode(name, intent.getBooleanExtra("isGroup", false))
        } else {
            setupOwnProfileMode()
        }
    }

    private fun setupContactMode(name: String, isGroup: Boolean) {
        isOwnProfile = false
        tvProfileAvatar.text = if (name.isNotEmpty()) name.take(1).uppercase() else "?"
        tvProfileName.text = name
        tvProfileSubtitle.text = if (isGroup) "مجموعة" else "محادثة فردية"
    }

    private fun setupOwnProfileMode() {
        isOwnProfile = true
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val user = auth.currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        tvProfileTitle.text = getString(R.string.my_profile)
        tvProfileName.visibility = View.GONE
        etProfileName.visibility = View.VISIBLE
        tvChangePhoto.visibility = View.VISIBLE
        tvSaveProfile.visibility = View.VISIBLE
        tvLogoutProfile.visibility = View.VISIBLE

        val tvOpenSettings: TextView = findViewById(R.id.tvOpenSettings)
        tvOpenSettings.visibility = View.VISIBLE
        tvOpenSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        tvProfileSubtitle.text = user.email ?: getString(R.string.guest_label)
        tvProfileAvatar.text = "?"

        loadOwnProfile(user.uid)

        val changePhotoAction = View.OnClickListener {
            pickImageLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        tvChangePhoto.setOnClickListener(changePhotoAction)
        ivProfileAvatar.setOnClickListener(changePhotoAction)
        tvProfileAvatar.setOnClickListener(changePhotoAction)

        tvSaveProfile.setOnClickListener { saveUsername(user.uid) }

        tvLogoutProfile.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun loadOwnProfile(uid: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { snapshot ->
                val profile = snapshot.toObject(UserProfile::class.java)
                val username = profile?.username?.takeIf { it.isNotBlank() }
                    ?: auth.currentUser?.email?.substringBefore("@")
                    ?: getString(R.string.guest_label)

                etProfileName.setText(username)
                tvProfileAvatar.text = if (username.isNotEmpty()) username.take(1).uppercase() else "?"

                currentAvatarUrl = profile?.avatarUrl
                showAvatar(currentAvatarUrl)
            }
            .addOnFailureListener {
                val fallback = auth.currentUser?.email?.substringBefore("@") ?: getString(R.string.guest_label)
                etProfileName.setText(fallback)
                tvProfileAvatar.text = fallback.take(1).uppercase()
            }
    }

    private fun showAvatar(url: String?) {
        if (!url.isNullOrEmpty()) {
            ivProfileAvatar.visibility = View.VISIBLE
            Glide.with(this).load(url).circleCrop().into(ivProfileAvatar)
        } else {
            ivProfileAvatar.visibility = View.GONE
        }
    }

    private fun saveUsername(uid: String) {
        val newName = etProfileName.text.toString().trim()
        if (newName.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_username_empty), Toast.LENGTH_SHORT).show()
            return
        }

        tvSaveProfile.isEnabled = false
        progressSave.visibility = View.VISIBLE

        db.collection("users").document(uid)
            .set(mapOf("username" to newName), SetOptions.merge())
            .addOnSuccessListener {
                progressSave.visibility = View.GONE
                tvSaveProfile.isEnabled = true
                tvProfileAvatar.text = newName.take(1).uppercase()
                Toast.makeText(this, getString(R.string.profile_updated), Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                progressSave.visibility = View.GONE
                tvSaveProfile.isEnabled = true
                Toast.makeText(this, e.message ?: getString(R.string.error_username_empty), Toast.LENGTH_SHORT).show()
            }
    }

    private fun uploadAvatar(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        progressPhoto.visibility = View.VISIBLE
        tvChangePhoto.isEnabled = false

        val remotePath = "avatars/$uid.jpg"
        SupabaseStorage.uploadFromUri(this, uri, remotePath, "image/jpeg") { publicUrl, error ->
            progressPhoto.visibility = View.GONE
            tvChangePhoto.isEnabled = true

            if (publicUrl != null) {
                // Bust any CDN/Glide cache so the new photo shows immediately.
                val cacheBustedUrl = "$publicUrl?t=${System.currentTimeMillis()}"
                currentAvatarUrl = publicUrl
                showAvatar(cacheBustedUrl)

                db.collection("users").document(uid)
                    .set(mapOf("avatarUrl" to publicUrl), SetOptions.merge())
            } else {
                Toast.makeText(this, error ?: getString(R.string.error_username_empty), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
