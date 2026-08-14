package com.example.myapplication

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SignUpActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val passwordCollection = db.collection("password")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        // --- Wire your EditText IDs from your layout ---
        val etCNumber = findViewById<android.widget.EditText>(R.id.et_cnumber)
        val etEmail = findViewById<android.widget.EditText>(R.id.et_email)
        val etFName = findViewById<android.widget.EditText>(R.id.et_fname)
        val etLName = findViewById<android.widget.EditText>(R.id.et_lname)
        val etMName = findViewById<android.widget.EditText>(R.id.et_mname)
        val spinnerGender = findViewById<android.widget.Spinner>(R.id.spinner_gender)
        val etUsername = findViewById<android.widget.EditText>(R.id.et_username)
        val spinnerUserType = findViewById<android.widget.Spinner>(R.id.spinner_usertype)
        val etPassword = findViewById<android.widget.EditText>(R.id.et_password)
        val btnSignUp = findViewById<android.widget.Button>(R.id.btn_signup)

        btnSignUp.setOnClickListener {
            val cNumber = etCNumber.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val fName = etFName.text.toString().trim()
            val lName = etLName.text.toString().trim()
            val mName = etMName.text.toString().trim()
            val gender = spinnerGender.selectedItem?.toString() ?: ""
            val username = etUsername.text.toString().trim()
            val userType = spinnerUserType.selectedItem?.toString() ?: ""
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            registerUser(
                cNumber, email, fName, lName, mName,
                gender, username, userType, password
            )
        }
    }

    private fun registerUser(
        cNumber: String, email: String, fName: String, lName: String,
        mName: String, gender: String, username: String, userType: String, password: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Check if username already exists
                val checkUser = passwordCollection
                    .whereEqualTo("user_name", username)
                    .get()
                    .await()

                if (!checkUser.isEmpty) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SignUpActivity, "Username already exists!", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Create user object
                val user = User(
                    c_number = cNumber,
                    email_add = email,
                    f_name = fName,
                    l_name = lName,
                    m_name = mName,
                    gender = gender,
                    user_name = username,
                    user_type = userType,
                    password = password
                )

                // Save to Firestore → auto-generates Document ID (like RH7eDYiniOrZfd4myFei)
                passwordCollection.add(user).await()

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SignUpActivity, "Sign Up Success! 🎉", Toast.LENGTH_SHORT).show()
                    finish() // Go back to Login
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SignUpActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}