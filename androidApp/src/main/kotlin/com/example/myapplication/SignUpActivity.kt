package com.example.myapplication

import org.mindrot.jbcrypt.BCrypt
import android.os.Bundle
import android.text.Spanned
import android.text.SpannableString
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
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
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent) as android.widget.TextView
                view.setTextColor(android.graphics.Color.WHITE)
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
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
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent) as android.widget.TextView
                view.setTextColor(android.graphics.Color.WHITE)
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
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

        // ==========================================
        // GINAWANG CLICKABLE ANG TERMS & CONDITIONS
        // ==========================================
        val fullText = "I agree to the Terms and Conditions"
        val spannableString = SpannableString(fullText)

        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                showTermsDialog() // Magbubukas ng dialog kapag pinindot
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = true // May guhit sa ilalim para magmukhang link
                ds.color = android.graphics.Color.parseColor("#4CAF50") // Kulay berde (pwede mong palitan)
            }
        }

        val startIndex = fullText.indexOf("Terms and Conditions")
        val endIndex = startIndex + "Terms and Conditions".length
        spannableString.setSpan(clickableSpan, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        cbTerms.text = spannableString
        cbTerms.movementMethod = LinkMovementMethod.getInstance()
        // ==========================================

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
            .setNegativeButton("Cancel", null)
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

                // 1. I-hash ang password bago i-save (Secure na ito!)
                val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())

                val user = User(
                    c_number = cNumber,
                    email_add = email,
                    f_name = fName,
                    l_name = lName,
                    m_name = mName,
                    gender = gender,
                    user_name = username,
                    user_type = userType,
                    password = hashedPassword, // 👈 Hashed password na ang isesave natin
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

    private fun showTermsDialog() {
        val termsContent = """
Rider Eye PH • Terms and Conditions
Last Updated: August 16, 2026

1. ACCEPTANCE OF TERMS
By downloading, installing, or using Rider Eye PH ("App", "we", "us"), you agree to these Terms and Conditions. If you do not agree, do not use the App. This App is developed and operated in the Philippines.

2. DESCRIPTION OF SERVICE
Rider Eye PH is a rider safety application that provides:
• Real-time GPS location sharing during active rides
• SOS emergency alert with exact location to linked Family and Admin
• Separate dashboards for Rider, Family, and Admin roles
• Ride history and safety monitoring
The App is for safety assistance only — it is NOT a replacement for emergency services (911, 117, ambulance, police).

3. USER ACCOUNTS & ROLES
A. Rider Account
• Must be 18+ years old with valid driver's license
• Responsible for granting location permission and starting/ending rides
• Must provide accurate information
B. Family Account
• Must be authorized and linked by the Rider
• Can only view location when Rider is on active ride and has consented
• Must not misuse location data for stalking or harassment
C. Admin Account
• For monitoring, support, and emergency coordination only
• Restricted access — all actions are logged
You are responsible for keeping your password secure. We use Firebase Authentication for security.

4. LOCATION AND SOS FEATURES — IMPORTANT
• Location tracking ONLY works when Rider taps "Start Ride" and grants permission.
• Background location is used ONLY during active ride for safety, to send SOS even if app is minimized.
• SOS button sends your real-time location to your linked Family and Admin. Use SOS only for real emergencies.
• False SOS alerts may result in account suspension.
• We do not guarantee 100% location accuracy — depends on GPS signal, internet, and device.

5. USER RESPONSIBILITIES
You agree NOT to:
• Use the App for illegal activities
• Share other users' location without consent
• Attempt to hack, reverse engineer, or disrupt Firebase backend
• Create fake accounts or impersonate others
• Use the App while driving in a way that causes distraction — always prioritize road safety
• Upload offensive content in profile

6. PRIVACY
Your use of the App is also governed by our Privacy Policy. By using the App, you consent to collection of location and safety data as described in Privacy Policy. We DO NOT SELL your data.

7. DISCLAIMER OF WARRANTY
• Rider Eye PH is provided "AS IS" without warranties.
• We do NOT guarantee that SOS alerts will always be delivered — depends on internet, GPS, and device battery.
• We are NOT liable for accidents, injuries, loss, or damages during rides.
• The App is a tool to help inform family — not a guarantee of safety or rescue.
• Always follow traffic laws and wear proper safety gear.

8. LIMITATION OF LIABILITY
To the maximum extent allowed by Philippine law, Rider Eye PH, its developers, and team shall not be liable for any indirect, incidental, or consequential damages arising from use of the App, including but not limited to failure to send SOS, inaccurate location, or app downtime.
Our total liability shall not exceed the amount you paid for the App (which is free).

9. ACCOUNT TERMINATION
We may suspend or terminate your account if:
• You violate these Terms
• You send false SOS alerts repeatedly
• You misuse Family tracking features
• Required by law
You may delete your account anytime via Settings > Delete Account or email ridereyeph.support@gmail.com — all your data will be deleted within 30 days.

10. INTERNET, BATTERY, AND DEVICE
You are responsible for:
• Keeping your device charged during rides
• Having mobile data / internet for location sharing
• Granting location permission — if denied, Rider features will not work

11. INTELLECTUAL PROPERTY
All logos, design, "RE Rider Eye PH" branding, red/blue/white color scheme, "Rider Safety Family Peace of Mind" and "Start Protecting" are owned by Rider Eye PH. You may not copy or use without permission.

12. CHANGES TO TERMS
We may update these Terms. We will notify you via app notification. Continued use means acceptance.

13. GOVERNING LAW
These Terms are governed by laws of the Republic of the Philippines. Any disputes shall be resolved in courts of Bacoor, Cavite, Philippines.

14. CONTACT US
For questions about Terms:
• Email: ridereyeph.support@gmail.com
• App: Rider Eye PH
• Location: Philippines

BY USING RIDER EYE PH, YOU ACKNOWLEDGE THAT YOU HAVE READ, UNDERSTOOD, AND AGREED TO THESE TERMS AND CONDITIONS AND OUR PRIVACY POLICY.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Terms and Conditions")
            .setMessage(termsContent)
            .setPositiveButton("Ok", null)
            .show()
    }
}