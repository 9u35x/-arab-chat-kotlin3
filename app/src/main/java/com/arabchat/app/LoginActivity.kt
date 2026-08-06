package com.arabchat.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }


    private lateinit var auth: FirebaseAuth
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        findViewById<TextView?>(R.id.tvForgotPassword)?.setOnClickListener {
            showForgotPasswordDialog()
        }


        auth = FirebaseAuth.getInstance()

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        progressBar = findViewById(R.id.progressBar)

        val tvLogin: TextView = findViewById(R.id.tvLogin)
        val tvGuest: TextView = findViewById(R.id.tvGuest)
        val tvGoRegister: TextView = findViewById(R.id.tvGoRegister)

        tvLogin.setOnClickListener { attemptLogin() }
        tvGuest.setOnClickListener { attemptGuestLogin() }
        tvGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun attemptLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.error_empty_fields, Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        UserRepo.ensureProfile(user) {
                            setLoading(false)
                            goHome()
                        }
                    } else {
                        setLoading(false)
                        goHome()
                    }
                } else {
                    setLoading(false)
                    Toast.makeText(
                        this,
                        task.exception?.localizedMessage ?: getString(R.string.error_invalid_email),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun attemptGuestLogin() {
        setLoading(true)
        auth.signInAnonymously()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        UserRepo.ensureProfile(user) {
                            setLoading(false)
                            goHome()
                        }
                    } else {
                        setLoading(false)
                        goHome()
                    }
                } else {
                    setLoading(false)
                    Toast.makeText(
                        this,
                        task.exception?.localizedMessage ?: "تعذر تسجيل الدخول كضيف",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun goHome() {
        BanGuard.checkBanned { banned, _ ->
            if (banned) {
                FirebaseAuth.getInstance().signOut()
                Toast.makeText(this, "حسابك محظور", Toast.LENGTH_LONG).show()
            } else {
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            }
        }
    }


    private fun showForgotPasswordDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "البريد الإلكتروني"
            setPadding(40, 30, 40, 30)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        // عبّي الإيميل من الحقل إن موجود
        findViewById<android.widget.EditText?>(R.id.etEmail)?.text?.toString()?.let {
            if (it.isNotBlank()) input.setText(it)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("إعادة تعيين كلمة المرور")
            .setMessage("سنرسل رابط إعادة التعيين إلى بريدك")
            .setView(input)
            .setPositiveButton("إرسال") { _, _ ->
                val email = input.text.toString().trim()
                if (email.isEmpty() || !email.contains("@")) {
                    android.widget.Toast.makeText(this, "أدخل بريداً صالحاً", android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                auth.sendPasswordResetEmail(email)
                    .addOnSuccessListener {
                        android.widget.Toast.makeText(
                            this,
                            "تم الإرسال. تحقق من بريدك (والبريد المزعج)",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    .addOnFailureListener { e ->
                        android.widget.Toast.makeText(this, e.message ?: "فشل الإرسال", android.widget.Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
