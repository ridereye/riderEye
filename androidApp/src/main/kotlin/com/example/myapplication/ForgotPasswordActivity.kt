package com.example.myapplication

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ForgotPasswordActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        val etUsername = findViewById<EditText>(R.id.et_reset_username)
        val etEmail = findViewById<EditText>(R.id.et_reset_email)
        val etNewPassword = findViewById<EditText>(R.id.et_new_password)
        val btnReset = findViewById<Button>(R.id.btn_reset_password)

        btnReset.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val newPassword = etNewPassword.text.toString().trim()

            if (username.isEmpty() || email.isEmpty() || newPassword.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            updatePassword(username, email, newPassword)
        }
    }

    private fun updatePassword(username: String, email: String, newPassword: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Verify user exist using both username and email
                val query = usersCollection
                    .whereEqualTo("user_name", username)
                    .whereEqualTo("email_add", email)
                    .get()
                    .await()

                if (!query.isEmpty) {
                    // Get matching document ID
                    val docId = query.documents[0].id

                    // Update password field
                    usersCollection.document(docId)
                        .update("password", newPassword)
                        .await()

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ForgotPasswordActivity, "Password updated successfully! 🎉", Toast.LENGTH_SHORT).show()
                        finish() // Return to Login
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ForgotPasswordActivity, "User details not found!", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ForgotPasswordActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}