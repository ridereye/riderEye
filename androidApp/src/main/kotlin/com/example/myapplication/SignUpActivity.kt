package com.example.myapplication

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.random.Random

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

        // GENDER SPINNER (Puti ang kulay ng text para sa dark theme)
        val spinnerGender = findViewById<Spinner>(R.id.spinner_gender)
        val genderOptions = arrayOf("Male", "Female", "Prefer not to say")
        val genderAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, genderOptions) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent) as android.widget.TextView
                view.setTextColor(android.graphics.Color.WHITE)
                return view
            }

            override fun getDropDownView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent) as android.widget.TextView
                view.setTextColor(android.graphics.Color.WHITE)
                view.setBackgroundColor(android.graphics.Color.parseColor("#1E1E28"))
                return view
            }
        }
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerGender.adapter = genderAdapter

        val etUsername = findViewById<EditText>(R.id.et_username)

        // USER TYPE SPINNER (Puti ang kulay ng text para sa dark theme)
        val spinnerUserType = findViewById<Spinner>(R.id.spinner_usertype)
        val userTypeOptions = arrayOf("Rider", "Family", "Admin")
        val userTypeAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, userTypeOptions) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent) as android.widget.TextView
                view.setTextColor(android.graphics.Color.WHITE)
                return view
            }

            override fun getDropDownView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent) as android.widget.TextView
                view.setTextColor(android.graphics.Color.WHITE)
                view.setBackgroundColor(android.graphics.Color.parseColor("#1E1E28"))
                return view
            }
        }
        userTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerUserType.adapter = userTypeAdapter

        val etPassword = findViewById<EditText>(R.id.et_password)
        val cbTerms = findViewById<CheckBox>(R.id.cb_terms)
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

            if (!cbTerms.isChecked) {
                Toast.makeText(this, "Please agree to the Terms and Conditions", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            checkUsernameAndSendOtp(
                cNumber, email, fName, lName, mName,
                gender, username, userType, password
            )
        }
    }

    private fun checkUsernameAndSendOtp(
        cNumber: String, email: String, fName: String, lName: String,
        mName: String, gender: String, username: String, userType: String, password: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
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

                val generatedOtp = Random.nextInt(100000, 999999).toString()

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SignUpActivity, "OTP sent to $email (Code: $generatedOtp)", Toast.LENGTH_LONG).show()

                    showOtpVerificationDialog(
                        generatedOtp, cNumber, email, fName, lName, mName, gender, username, userType, password
                    )
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SignUpActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showOtpVerificationDialog(
        correctOtp: String, cNumber: String, email: String, fName: String, lName: String,
        mName: String, gender: String, username: String, userType: String, password: String
    ) {
        val inputField = EditText(this).apply {
            hint = "Ilagay ang 6-digit OTP"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(android.graphics.Color.BLACK)
        }

        AlertDialog.Builder(this)
            .setTitle("Email Verification")
            .setMessage("Ilagay ang 6-digit code na ipinadala sa iyong email ($email):")
            .setView(inputField)
            .setPositiveButton("Verify & Register") { _, _ ->
                val userEnteredOtp = inputField.text.toString().trim()
                if (userEnteredOtp == correctOtp) {
                    registerUserToFirestore(cNumber, email, fName, lName, mName, gender, username, userType, password)
                } else {
                    Toast.makeText(this, "Maling OTP! Subukang muli.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Kanselahin", null)
            .show()
    }

    private fun registerUserToFirestore(
        cNumber: String, email: String, fName: String, lName: String,
        mName: String, gender: String, username: String, userType: String, password: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val newDocRef = usersCollection.document()
                val firestoreId = newDocRef.id

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
                    rider_id = if (userType == "Rider") firestoreId else "",
                    family_code = if (userType == "Rider") "FAM-$firestoreId" else ""
                )

                newDocRef.set(user).await()

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SignUpActivity, "Sign Up Success! 🎉 Account Verified.", Toast.LENGTH_LONG).show()
                    finish()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SignUpActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}