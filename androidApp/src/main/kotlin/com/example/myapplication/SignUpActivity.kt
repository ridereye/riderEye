package com.example.myapplication

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
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
    private val usersCollection = db.collection("users")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        val etCNumber = findViewById<EditText>(R.id.et_cnumber)
        val etEmail = findViewById<EditText>(R.id.et_email)
        val etFName = findViewById<EditText>(R.id.et_fname)
        val etLName = findViewById<EditText>(R.id.et_lname)
        val etMName = findViewById<EditText>(R.id.et_mname)

        val spinnerGender = findViewById<Spinner>(R.id.spinner_gender)
        val genderOptions = arrayOf("Male", "Female", "Prefer not to say")
        val genderAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, genderOptions)
        spinnerGender.adapter = genderAdapter

        val etUsername = findViewById<EditText>(R.id.et_username)

        val spinnerUserType = findViewById<Spinner>(R.id.spinner_usertype)
        val userTypeOptions = arrayOf("Rider", "Family", "Admin")
        val userTypeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, userTypeOptions)
        spinnerUserType.adapter = userTypeAdapter

        val etPassword = findViewById<EditText>(R.id.et_password)
        val btnSignUp = findViewById<Button>(R.id.btn_signup)

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
                val checkUser = usersCollection
                    .whereEqualTo("user_name", username)
                    .get()
                    .await()

                if (!checkUser.isEmpty) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SignUpActivity, "Username already exists!", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // 1. AUTO-GENERATE RIDER ID & FAMILY CODE FOR RIDERS
                val randomNum = (1000..9999).random()
                val riderId = if (userType == "Rider") "RAPTOR_X-$randomNum" else ""
                val familyCode = if (userType == "Rider") "FAM-RAPTOR-$randomNum" else ""

                // 2. CREATE USER OBJECT WITH GENERATED CODES
                val user = User(
                    c_number = cNumber,
                    email_add = email,
                    f_name = fName,
                    l_name = lName,
                    m_name = mName,
                    gender = gender,
                    user_name = username,
                    user_type = userType,
                    password = password,
                    rider_id = riderId,
                    family_code = familyCode
                )

                // Save to Firestore
                usersCollection.add(user).await()

                withContext(Dispatchers.Main) {
                    val message = if (userType == "Rider") {
                        "Sign Up Success! 🎉 Your Family Code: $familyCode"
                    } else {
                        "Sign Up Success! 🎉"
                    }
                    Toast.makeText(this@SignUpActivity, message, Toast.LENGTH_LONG).show()
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