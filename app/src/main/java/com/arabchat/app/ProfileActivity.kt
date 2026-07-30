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
    private lateinit var tvProfileSubtitle: TextView

    private lateinit var layoutOwnFields: View
    private lateinit var layoutContactFields: View

    private lateinit var etDisplayName: EditText
    private lateinit var etUsername: EditText
    private lateinit var etBio: EditText
    private lateinit var radioGender: RadioGroup
    private lateinit var radioMale: RadioButton
    private lateinit var radioFemale: RadioButton
    private lateinit var tvSaveProfile: TextView
    private lateinit var progressSave: ProgressBar
    private lateinit var tvLogoutProfile: TextView

    private lateinit var tvContactName: TextView
    private lateinit var tvContactUsername: TextView
    private lateinit var tvContactBio: TextView
    private lateinit var tvContactGender: TextView
    private var contactUid: String? = null
    private var contactBlocked = false

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
        tvProfileSubtitle = findViewById(R.id.tvProfileSubtitle)

        layoutOwnFields = findViewById(R.id.layoutOwnFields)
        layoutContactFields = findViewById(R.id.layoutContactFields)

        etDisplayName = findViewById(R.id.etDisplayName)
        etUsername = findViewById(R.id.etUsername)
        etBio = findViewById(R.id.etBio)
        radioGender = findViewById(R.id.radioGender)
        radioMale = findViewById(R.id.radioMale)
        radioFemale = findViewById(R.id.radioFemale)
        tvSaveProfile = findViewById(R.id.tvSaveProfile)
        progressSave = findViewById(R.id.progressSave)
        tvLogoutProfile = findViewById(R.id.tvLogoutProfile)

        tvContactName = findViewById(R.id.tvContactName)
        tvContactUsername = findViewById(R.id.tvContactUsername)
        tvContactBio = findViewById(R.id.tvContactBio)
        tvContactGender = findViewById(R.id.tvContactGender)

        tvBack.setOnClickListener { finish() }

        val targetUid = intent.getStringExtra("uid")
            ?: intent.getStringExtra("userId")
        val nameExtra = intent.getStringExtra("name").orEmpty()
        val isGroup = intent.getBooleanExtra("isGroup", false)

        // Only own profile when no uid is passed
        if (targetUid.isNullOrBlank()) {
            setupOwnProfileMode()
        } else {
            setupContactMode(targetUid, nameExtra, isGroup)
        }
    }

    private fun setupContactMode(uid: String, name: String, isGroup: Boolean) {
        isOwnProfile = false
        contactUid = uid
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        layoutOwnFields.visibility = View.GONE
        layoutContactFields.visibility = View.VISIBLE
        tvChangePhoto.visibility = View.GONE
        tvProfileSubtitle.visibility = View.GONE

        // Big name under avatar (modern look)
        tvProfileName.visibility = View.VISIBLE
        tvProfileName.text = name.ifBlank { "…" }

        tvProfileTitle.text = getString(R.string.profile_title)
        tvContactName.text = name.ifBlank { "—" }
        tvProfileAvatar.text = if (name.isNotEmpty()) name.take(1).uppercase() else "?"

        if (isGroup) {
            tvContactUsername.text = "—"
            tvContactBio.text = getString(R.string.no_bio)
            tvContactGender.text = "—"
            return
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { snapshot ->
                val profile = snapshot.toObject(UserProfile::class.java)
                val display = profile?.bestName()?.takeIf { it.isNotBlank() && it != "مستخدم" }
                    ?: name.ifBlank { "مستخدم" }

                // Header name
                tvProfileName.text = display
                tvProfileAvatar.text = display.take(1).uppercase()

                // Separate cards
                tvContactName.text = display

                val uname = profile?.username?.trim().orEmpty()
                tvContactUsername.text = if (uname.isNotEmpty()) "@$uname" else getString(R.string.no_username)

                val bio = profile?.bio?.trim().orEmpty()
                tvContactBio.text = if (bio.isNotEmpty()) bio else getString(R.string.no_bio)

                tvContactGender.text = when (profile?.gender) {
                    "male" -> getString(R.string.gender_male)
                    "female" -> getString(R.string.gender_female)
                    else -> "—"
                }

                
                showAvatar(profile?.avatarUrl)

                val tvBlock = findViewById<TextView?>(R.id.tvBlockUser)
                val me = auth.currentUser?.uid
                if (tvBlock != null && me != null && !isGroup) {
                    tvBlock.visibility = android.view.View.VISIBLE
                    BlockManager.isBlocked(me, uid) { blocked ->
                        contactBlocked = blocked
                        tvBlock.text = getString(if (blocked) R.string.unblock_user else R.string.block_user)
                    }
                    tvBlock.setOnClickListener {
                        val target = contactUid ?: return@setOnClickListener
                        if (contactBlocked) {
                            BlockManager.unblockUser(me, target) { ok, err ->
                                if (ok) {
                                    contactBlocked = false
                                    tvBlock.text = getString(R.string.block_user)
                                    Toast.makeText(this, R.string.user_unblocked, Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this, err ?: "خطأ", Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle(R.string.block_user)
                                .setMessage(R.string.block_confirm)
                                .setPositiveButton(R.string.block_user) { _, _ ->
                                    BlockManager.blockUser(me, target) { ok, err ->
                                        if (ok) {
                                            contactBlocked = true
                                            tvBlock.text = getString(R.string.unblock_user)
                                            Toast.makeText(this, R.string.user_blocked, Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(this, err ?: "خطأ", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                                .setNegativeButton(android.R.string.cancel, null)
                                .show()
                        }
                    }
                }

            }
            .addOnFailureListener {
                tvContactBio.text = getString(R.string.no_bio)
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

        layoutOwnFields.visibility = View.VISIBLE
        layoutContactFields.visibility = View.GONE
        tvChangePhoto.visibility = View.VISIBLE
        tvProfileName.visibility = View.GONE
        tvProfileSubtitle.visibility = View.GONE

        tvProfileTitle.text = getString(R.string.my_profile)
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

        findViewById<TextView?>(R.id.tvOpenSettings)?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun loadOwnProfile(uid: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { snapshot ->
                val profile = snapshot.toObject(UserProfile::class.java)
                val display = profile?.displayName?.takeIf { it.isNotBlank() }
                    ?: profile?.username?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.guest_label)

                etDisplayName.setText(profile?.displayName ?: "")
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
                etDisplayName.setText("")
                etUsername.setText("")
                etBio.setText("")
                tvProfileAvatar.text = "?"
            }
    }

    private fun showAvatar(url: String?) {
        if (!url.isNullOrEmpty()) {
            ivProfileAvatar.visibility = View.VISIBLE
            tvProfileAvatar.visibility = View.GONE
            Glide.with(this).load(url).circleCrop().into(ivProfileAvatar)
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
