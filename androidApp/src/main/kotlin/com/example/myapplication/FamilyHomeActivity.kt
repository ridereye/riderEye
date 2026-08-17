package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FamilyHomeActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var sessionManager: SessionManager

    private var sosListener: ListenerRegistration? = null
    private var connectedFamilyCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_family_home)

        sessionManager = SessionManager(this)

        val etInputCode = findViewById<EditText>(R.id.et_rider_username) // Field for link or code
        val btnConnect = findViewById<Button>(R.id.btn_link_rider)
        val tvStatus = findViewById<TextView>(R.id.tv_linked_status)
        val btnTrack = findViewById<Button>(R.id.btn_track_location)
        val btnHistory = findViewById<Button>(R.id.btn_history)
        val btnLogout = findViewById<Button>(R.id.btn_family_logout)

        val layoutSosBanner = findViewById<LinearLayout>(R.id.layout_sos_banner)
        val tvSosMessage = findViewById<TextView>(R.id.tv_sos_message)
        val btnClearSos = findViewById<Button>(R.id.btn_clear_sos)

        // 1. TINGNAN KUNG BINUKSAN VIA DEEP LINK (rider://FAM-RAPTOR-8821?...)
        handleDeepLink(intent, etInputCode, tvStatus)

        // Load existing session kung nakakonek na dati
        loadExistingConnection(tvStatus, btnConnect)

        btnConnect.setOnClickListener {
            val input = etInputCode.text.toString().trim()
            if (input.isEmpty()) {
                Toast.makeText(this, "Please enter Family Code or Share Link", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (connectedFamilyCode == null) {
                // Connect Logic
                val parsedCode = parseFamilyCode(input)
                connectToRider(parsedCode, tvStatus, btnConnect)
            } else {
                // Disconnect Logic
                disconnectRider(tvStatus, btnConnect)
            }
        }

        btnClearSos.setOnClickListener {
            layoutSosBanner.visibility = View.GONE
            connectedFamilyCode?.let { code ->
                db.collection("families").document(code).collection("sos").document("alert")
                    .update("active", false)
            }
        }

        btnTrack.setOnClickListener {
            if (!connectedFamilyCode.isNullOrEmpty()) {
                val intent = Intent(this, MapActivity::class.java)
                intent.putExtra("FAMILY_CODE", connectedFamilyCode)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Please connect to a rider first!", Toast.LENGTH_SHORT).show()
            }
        }

        btnHistory.setOnClickListener {
            if (!connectedFamilyCode.isNullOrEmpty()) {
                val intent = Intent(this, HistoryActivity::class.java)
                intent.putExtra("FAMILY_CODE", connectedFamilyCode)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Please connect to a rider first!", Toast.LENGTH_SHORT).show()
            }
        }

        btnLogout.setOnClickListener {
            sessionManager.clearSession()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    // Kinukuha ang FAM- Code sa link: "rider://FAM-RAPTOR-8821?lat=14.1&lng=120.9" -> "FAM-RAPTOR-8821"
    private fun parseFamilyCode(input: String): String {
        return if (input.contains("rider://")) {
            val clean = input.replace("rider://", "")
            clean.substringBefore("?").trim()
        } else {
            input.trim()
        }
    }

    private fun handleDeepLink(intent: Intent?, etInput: EditText, tvStatus: TextView) {
        val data = intent?.data
        if (data != null && data.scheme == "rider") {
            val fullUrl = data.toString()
            val code = parseFamilyCode(fullUrl)
            etInput.setText(code)
            Toast.makeText(this, "Share Link Detected!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectToRider(familyCode: String, tvStatus: TextView, btnConnect: Button) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Verification: Tinitingnan kung umiiral ang family_code sa Firestore
                val riderResult = db.collection("users")
                    .whereEqualTo("family_code", familyCode)
                    .get()
                    .await()

                if (!riderResult.isEmpty) {
                    val riderDocument = riderResult.documents[0]
                    val riderName = riderDocument.getString("user_name") ?: "Rider"
                    val riderId = riderDocument.getString("rider_id") ?: ""

                    // Auto add System Message sa Family Chat
                    val sysMsg = hashMapOf(
                        "sender" to "SYSTEM",
                        "message" to "Family linked to $riderName via Share Code ($familyCode)",
                        "timestamp" to System.currentTimeMillis()
                    )
                    db.collection("families").document(familyCode)
                        .collection("family_chat").add(sysMsg)

                    withContext(Dispatchers.Main) {
                        connectedFamilyCode = familyCode

                        // 👈 In-adjust natin ito para tumugma sa bagong saveSession parameters mo
                        sessionManager.saveSession(
                            userId = sessionManager.getRiderId() ?: "",
                            username = sessionManager.getUsername() ?: "Family",
                            userType = "Family",
                            riderId = riderId,
                            familyCode = familyCode,
                            fullName = sessionManager.getFullName() ?: "Family User"
                        )

                        tvStatus.text = " Connected to: $riderName ($familyCode)"
                        btnConnect.text = "DISCONNECT"
                        btnConnect.setBackgroundColor(android.graphics.Color.RED)

                        Toast.makeText(this@FamilyHomeActivity, "RIDER CONNECTED! Auto synced 🎉", Toast.LENGTH_LONG).show()
                        listenForSosAlerts(familyCode)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FamilyHomeActivity, "Invalid Family Code or Share Link!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FamilyHomeActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun disconnectRider(tvStatus: TextView, btnConnect: Button) {
        connectedFamilyCode = null
        sosListener?.remove()
        tvStatus.text = "No Rider Linked Yet"
        btnConnect.text = "CONNECT"
        btnConnect.setBackgroundColor(android.graphics.Color.parseColor("#1E90FF"))
        Toast.makeText(this, "Disconnected from Rider", Toast.LENGTH_SHORT).show()
    }

    private fun loadExistingConnection(tvStatus: TextView, btnConnect: Button) {
        val savedCode = sessionManager.getFamilyCode()
        if (!savedCode.isNullOrEmpty()) {
            connectedFamilyCode = savedCode
            tvStatus.text = " Connected Code: $savedCode"
            btnConnect.text = "DISCONNECT"
            btnConnect.setBackgroundColor(android.graphics.Color.RED)
            listenForSosAlerts(savedCode)
        }
    }

    private fun listenForSosAlerts(familyCode: String) {
        val layoutSosBanner = findViewById<LinearLayout>(R.id.layout_sos_banner)
        val tvSosMessage = findViewById<TextView>(R.id.tv_sos_message)

        sosListener?.remove()

        sosListener = db.collection("families").document(familyCode)
            .collection("sos").document("alert")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val active = snapshot.getBoolean("active") ?: false
                if (active) {
                    tvSosMessage.text = "🚨 RIDER SOS ALERT! Triggered at $familyCode"
                    layoutSosBanner.visibility = View.VISIBLE
                } else {
                    layoutSosBanner.visibility = View.GONE
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        sosListener?.remove()
    }
}