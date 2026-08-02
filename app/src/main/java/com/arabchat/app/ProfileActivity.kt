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

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) uploadAvatar(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        findViewById<TextView?>(R.id.tvReportUser)?.setOnClickListener {
            val tid = intent.getStringExtra("uid")
                ?: intent.getStringExtra("userId")
                ?: intent.getStringExtra("peerUid")
                ?: ""
            if (tid.isNotBlank()) showReportFromProfile(tid)
            else Toast.makeText(this, "لا يمكن الإبلاغ", Toast.LENGTH_SHORT).show()
        }
        findViewById<View?>(R.id.tvProfileAvatar)?.setOnLongClickListener {
            val tid = intent.getStringExtra("uid") ?: intent.getStringExtra("userId")
            val me = FirebaseAuth.getInstance().currentUser?.uid
            if (!tid.isNullOrBlank() && tid != me) {
                showReportFromProfile(tid); true
            } else {
                changePassword(); true
            }
        }
        // changePasswordBind — visible via long-press on Save or Settings text
        findViewById<View?>(R.id.tvSaveProfile)?.setOnLongClickListener {
            changePassword(); true
        }
        findViewById<View?>(R.id.tvSettings)?.setOnLongClickListener {
            changePassword(); true
        }
        findViewById<View?>(R.id.tvLogout)?.setOnLongClickListener {
            // report only when viewing someone else
            true
        }

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
    private fun saveProfile(uid: String) {
        val displayName = etDisplayName.text.toString().trim()
        val username = UsernameRules.normalize(etUsername.text.toString())
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
        if (!UsernameRules.isValid(username)) {
            Toast.makeText(this, R.string.error_username_format, Toast.LENGTH_SHORT).show()
            return
        }

        tvSaveProfile.isEnabled = false
        progressSave.visibility = android.view.View.VISIBLE

        UsernameRules.checkUnique(username, uid) { unique ->
            if (!unique) {
                progressSave.visibility = android.view.View.GONE
                tvSaveProfile.isEnabled = true
                Toast.makeText(this, R.string.error_username_taken, Toast.LENGTH_SHORT).show()
                return@checkUnique
            }
            val data = hashMapOf<String, Any>(
                "displayName" to displayName,
                "username" to username,
                "bio" to bio,
                "gender" to gender
            )
            db.collection("users").document(uid)
                .set(data, SetOptions.merge())
                .addOnSuccessListener {
                    progressSave.visibility = android.view.View.GONE
                    tvSaveProfile.isEnabled = true
                    tvProfileAvatar.text = displayName.take(1).uppercase()
                    Toast.makeText(this, getString(R.string.profile_updated), Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    progressSave.visibility = android.view.View.GONE
                    tvSaveProfile.isEnabled = true
                    Toast.makeText(this, e.message ?: "خطأ", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun changePassword() {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.new_password)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val input2 = android.widget.EditText(this).apply {
            hint = getString(R.string.confirm_password)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val box = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
            addView(input)
            addView(input2)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.change_password)
            .setView(box)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val p1 = input.text.toString()
                val p2 = input2.text.toString()
                if (p1.length < 6) {
                    Toast.makeText(this, R.string.password_too_short, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (p1 != p2) {
                    Toast.makeText(this, R.string.password_mismatch, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                auth.currentUser?.updatePassword(p1)
                    ?.addOnSuccessListener {
                        Toast.makeText(this, R.string.password_changed, Toast.LENGTH_SHORT).show()
                    }
                    ?.addOnFailureListener { e ->
                        Toast.makeText(this, e.message ?: getString(R.string.password_change_failed), Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showReportFromProfile(reportedUid: String) {
        val reasons = arrayOf(
            getString(R.string.report_reason_spam),
            getString(R.string.report_reason_abuse),
            getString(R.string.report_reason_scam),
            getString(R.string.report_reason_other)
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.report_title)
            .setItems(reasons) { _, which ->
                ReportHelper.submitReport(reportedUid, reasons[which]) { ok, err ->
                    Toast.makeText(
                        this,
                        if (ok) getString(R.string.report_sent) else (err ?: getString(R.string.report_failed)),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

}
