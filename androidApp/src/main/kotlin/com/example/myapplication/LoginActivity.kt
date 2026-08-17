package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)

        // AUTO-LOGIN CHECK: Kung naka-login na, rekta agad sa Home Dashboard
        if (sessionManager.isLoggedIn()) {
            redirectToDashboard(sessionManager.getUserType() ?: "")
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        val etUsername = findViewById<EditText>(R.id.et_username)
        val etPassword = findViewById<EditText>(R.id.et_password)
        val btnLogin = findViewById<Button>(R.id.btn_login)
        val btnGoSignUp = findViewById<Button>(R.id.btn_goto_signup)
        val btnForgotPassword = findViewById<TextView>(R.id.btn_forgot_password)

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this@LoginActivity, "Enter Username & Password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginUser(username, password)
        }

        btnGoSignUp.setOnClickListener {
            startActivity(Intent(this@LoginActivity, SignUpActivity::class.java))
        }

        btnForgotPassword.setOnClickListener {
            startActivity(Intent(this@LoginActivity, ForgotPasswordActivity::class.java))
        }
    }

    private fun loginUser(username: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = usersCollection
                    .whereEqualTo("user_name", username)
                    .whereEqualTo("password", password)
                    .get()
                    .await()

                withContext(Dispatchers.Main) {
                    if (!result.isEmpty) {
                        val document = result.documents[0]
                        val firestoreId = document.id // 👈 Kinuha natin ang Document ID!
                        val userType = document.getString("user_type") ?: ""
                        val riderId = document.getString("rider_id") ?: ""
                        val familyCode = document.getString("family_code") ?: ""
                        val fName = document.getString("f_name") ?: ""

                        // 👈 I-save lahat kasama ang mga ID sa SessionManager
                        sessionManager.saveSession(
                            userId = firestoreId,
                            username = username,
                            userType = userType,
                            riderId = riderId,
                            familyCode = familyCode,
                            fullName = fName
                        )

                        Toast.makeText(this@LoginActivity, "Welcome back, $fName! 🎉", Toast.LENGTH_SHORT).show()

                        redirectToDashboard(userType)
                        finish() // Isara ang Login screen
                    } else {
                        Toast.makeText(this@LoginActivity, "Invalid Username or Password", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun redirectToDashboard(userType: String) {
        val intent = when (userType) {
            "Rider" -> Intent(this@LoginActivity, RiderHomeActivity::class.java)
            "Family" -> Intent(this@LoginActivity, FamilyHomeActivity::class.java)
            "Admin" -> Intent(this@LoginActivity, AdminHomeActivity::class.java)
            else -> Intent(this@LoginActivity, RiderHomeActivity::class.java)
        }
        startActivity(intent)
    }
}