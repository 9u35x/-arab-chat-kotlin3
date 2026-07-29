package com.arabchat.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

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
    private lateinit var etDisplayName: EditText
    private lateinit var etUsername: EditText
    private lateinit var etBio: EditText
    private lateinit var radioGender: RadioGroup
    private lateinit var radioMale: RadioButton
    private lateinit var radioFemale: RadioButton
    private lateinit var tvProfileSubtitle: TextView
    private lateinit var tvSaveProfile: TextView
    private lateinit var progressSave: ProgressBar
    private lateinit var tvLogoutProfile: TextView
    private lateinit var layoutOwnFields: View

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
        etDisplayName = findViewById(R.id.etDisplayName)
        etUsername = findViewById(R.id.etUsername)
        etBio = findViewById(R.id.etBio)
        radioGender = findViewById(R.id.radioGender)
        radioMale = findViewById(R.id.radioMale)
        radioFemale = findViewById(R.id.radioFemale)
        tvProfileSubtitle = findViewById(R.id.tvProfileSubtitle)
        tvSaveProfile = findViewById(R.id.tvSaveProfile)
        progressSave = findViewById(R.id.progressSave)
        tvLogoutProfile = findViewById(R.id.tvLogoutProfile)
        layoutOwnFields = findViewById(R.id.layoutOwnFields)

        tvBack.setOnClickListener { finish() }

        val userId = intent.getStringExtra("userId")
        val name = intent.getStringExtra("name")
        if (userId != null || name != null) {
            setupContactMode(userId, name, intent.getBooleanExtra("isGroup", false))
        } else {
            setupOwnProfileMode()
        }
    }

    private fun setupContactMode(userId: String?, fallbackName: String?, isGroup: Boolean) {
        isOwnProfile = false
        layoutOwnFields.visibility = View.GONE
        val name = fallbackName ?: "?"
        tvProfileAvatar.text = if (name.isNotEmpty()) name.take(1).uppercase() else "?"
        tvProfileName.visibility = View.VISIBLE
        tvProfileName.text = name
        tvProfileSubtitle.text = if (isGroup) "مجموعة / قناة" else ""

        // Never show email. Load public fields only.
        if (!userId.isNullOrBlank() && !isGroup) {
            db = FirebaseFirestore.getInstance()
            db.collection("users").document(userId).get()
                .addOnSuccessListener { snap ->
                    val profile = snap.toObject(UserProfile::class.java)
                    val display = profile?.bestName() ?: name
                    tvProfileName.text = display
                    tvProfileAvatar.text = if (display.isNotEmpty()) display.take(1).uppercase() else "?"
                    val parts = mutableListOf<String>()
                    if (!profile?.username.isNullOrBlank()) parts.add("@${profile?.username}")
                    when (profile?.gender) {
                        "male" -> parts.add(getString(R.string.gender_male))
                        "female" -> parts.add(getString(R.string.gender_female))
                    }
                    if (!profile?.bio.isNullOrBlank()) parts.add(profile!!.bio)
                    tvProfileSubtitle.text = parts.joinToString(" · ")
                    showAvatar(profile?.avatarUrl)
                }
        }
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
        layoutOwnFields.visibility = View.VISIBLE
        tvChangePhoto.visibility = View.VISIBLE
        tvSaveProfile.visibility = View.VISIBLE
        tvLogoutProfile.visibility = View.VISIBLE

        val tvOpenSettings: TextView? = findViewById(R.id.tvOpenSettings)
        tvOpenSettings?.visibility = View.VISIBLE
        tvOpenSettings?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        tvProfileSubtitle.text = getString(R.string.my_profile) // never show email
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

        tvSaveProfile.setOnClickListener { saveProfile(user.uid) }
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
                val display = profile?.displayName?.takeIf { it.isNotBlank() }
                    ?: profile?.username?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.guest_label)

                etDisplayName.setText(profile?.displayName ?: display)
                etUsername.setText(profile?.username ?: "")
                etBio.setText(profile?.bio ?: "")
                when (profile?.gender) {
                    "male" -> radioMale.isChecked = true
                    "female" -> radioFemale.isChecked = true
                    else -> radioGender.clearCheck()
                }

                tvProfileAvatar.text = if (display.isNotEmpty()) display.take(1).uppercase() else "?"
                currentAvatarUrl = profile?.avatarUrl
                showAvatar(currentAvatarUrl)
            }
            .addOnFailureListener {
                val fallback = getString(R.string.guest_label)
                etDisplayName.setText("")
                tvProfileAvatar.text = "?"
            }
    }

    private fun showAvatar(url: String?) {
        if (!url.isNullOrEmpty()) {
            ivProfileAvatar.visibility = View.VISIBLE
            tvProfileAvatar.visibility = View.GONE
            Glide.with(this).load(url).circleCrop().error(R.drawable.bg_avatar_circle).into(ivProfileAvatar)
        } else {
            ivProfileAvatar.visibility = View.GONE
            tvProfileAvatar.visibility = View.VISIBLE
        }
    }

    private fun saveProfile(uid: String) {
        val displayName = etDisplayName.text.toString().trim()
        val username = etUsername.text.toString().trim().replace(" ", "")
        val bio = etBio.text.toString().trim()
        val gender = when {
            radioMale.isChecked -> "male"
            radioFemale.isChecked -> "female"
            else -> ""
        }

        if (displayName.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_username_empty), Toast.LENGTH_SHORT).show()
            return
        }

        tvSaveProfile.isEnabled = false
        progressSave.visibility = View.VISIBLE

        val data = hashMapOf<String, Any>(
            "displayName" to displayName,
            "username" to username,
            "bio" to bio,
            "gender" to gender
        )

        db.collection("users").document(uid)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                progressSave.visibility = View.GONE
                tvSaveProfile.isEnabled = true
                tvProfileAvatar.text = displayName.take(1).uppercase()
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
        Toast.makeText(this, getString(R.string.uploading_photo), Toast.LENGTH_SHORT).show()

        val remotePath = "avatars/$uid.jpg"
        SupabaseStorage.uploadFromUri(this, uri, remotePath, "image/jpeg") { publicUrl, error ->
            progressPhoto.visibility = View.GONE
            tvChangePhoto.isEnabled = true

            if (publicUrl != null) {
                val cacheBustedUrl = "$publicUrl?t=${System.currentTimeMillis()}"
                currentAvatarUrl = publicUrl
                showAvatar(cacheBustedUrl)
                db.collection("users").document(uid)
                    .set(mapOf("avatarUrl" to publicUrl), SetOptions.merge())
                    .addOnSuccessListener {
                        Toast.makeText(this, getString(R.string.profile_updated), Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, error ?: "فشل رفع الصورة", Toast.LENGTH_LONG).show()
            }
        }
    }
}
