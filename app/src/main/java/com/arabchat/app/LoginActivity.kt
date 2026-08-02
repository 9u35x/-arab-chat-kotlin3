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

    private lateinit var auth: FirebaseAuth
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

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
        BanGuard.checkBanned { banned, reason ->
                        if (banned) {
                            FirebaseAuth.getInstance().signOut()
                        } else {
                            startActivity(Intent(this, HomeActivity::class.java))
                            finish()
                        }
                    }
        finish()
    }
}
