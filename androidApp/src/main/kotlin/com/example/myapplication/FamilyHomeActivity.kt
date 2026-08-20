package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FamilyHomeActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var sessionManager: SessionManager

    private var sosListener: ListenerRegistration? = null
    private var connectedRiderId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_family_home)

        sessionManager = SessionManager(this)

        val etInputCode = findViewById<EditText>(R.id.et_rider_username)
        val btnConnect = findViewById<Button>(R.id.btn_link_rider)
        val tvStatus = findViewById<TextView>(R.id.tv_linked_status)
        val btnTrack = findViewById<Button>(R.id.btn_track_location)
        val btnHistory = findViewById<Button>(R.id.btn_history)
        val btnLogout = findViewById<Button>(R.id.btn_family_logout)

        val layoutSosBanner = findViewById<LinearLayout>(R.id.layout_sos_banner)
        val tvSosMessage = findViewById<TextView>(R.id.tv_sos_message)
        val btnClearSos = findViewById<Button>(R.id.btn_clear_sos)

        // 1. TINGNAN KUNG BINUKSAN VIA DEEP LINK (rider://RIDER_ID)
        handleDeepLink(intent, etInputCode)

        // Load existing connection gamit ang local prefs ng activity
        loadExistingConnection(tvStatus, btnConnect)

        btnConnect.setOnClickListener {
            val input = etInputCode.text.toString().trim()
            if (input.isEmpty()) {
                Toast.makeText(this, "Please enter Rider ID or Share Link", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (connectedRiderId == null) {
                val parsedId = parseRiderId(input)
                connectToRider(parsedId, tvStatus, btnConnect)
            } else {
                disconnectRider(tvStatus, btnConnect)
            }
        }

        btnClearSos.setOnClickListener {
            layoutSosBanner.visibility = View.GONE
            connectedRiderId?.let { riderId ->
                db.collection("families").document(riderId).collection("sos").document("alert")
                    .update("active", false)
            }
        }

        btnTrack.setOnClickListener {
            if (!connectedRiderId.isNullOrEmpty()) {
                val intent = Intent(this, MapActivity::class.java)
                intent.putExtra("FAMILY_CODE", connectedRiderId)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Please connect to a rider first!", Toast.LENGTH_SHORT).show()
            }
        }

        btnHistory.setOnClickListener {
            if (!connectedRiderId.isNullOrEmpty()) {
                val intent = Intent(this, HistoryActivity::class.java)
                intent.putExtra("KEY_RIDER_ID", connectedRiderId)
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

    private fun parseRiderId(input: String): String {
        return if (input.contains("rider://")) {
            val clean = input.replace("rider://", "")
            clean.substringBefore("?").trim()
        } else {
            input.trim()
        }
    }

    private fun handleDeepLink(intent: Intent?, etInput: EditText) {
        val data = intent?.data
        if (data != null && data.scheme == "rider") {
            val fullUrl = data.toString()
            val id = parseRiderId(fullUrl)
            etInput.setText(id)
            Toast.makeText(this, "Share Link Detected!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectToRider(riderIdInput: String, tvStatus: TextView, btnConnect: Button) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val riderResult = db.collection("users")
                    .whereEqualTo("rider_id", riderIdInput)
                    .get()
                    .await()

                if (!riderResult.isEmpty) {
                    val riderDocument = riderResult.documents[0]
                    val riderName = riderDocument.getString("user_name") ?: "Rider"
                    val riderId = riderDocument.getString("rider_id") ?: riderIdInput

                    withContext(Dispatchers.Main) {
                        connectedRiderId = riderId

                        val prefs = getSharedPreferences("FamilyConnectionPrefs", Context.MODE_PRIVATE)
                        prefs.edit().putString("saved_rider_id", riderId).apply()

                        tvStatus.text = " Connected to: $riderName ($riderId)"
                        btnConnect.text = "DISCONNECT"
                        btnConnect.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.RED)

                        Toast.makeText(this@FamilyHomeActivity, "RIDER CONNECTED SUCCESSFULLY! 🎉", Toast.LENGTH_LONG).show()
                        listenForSosAlerts(riderId)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FamilyHomeActivity, "Invalid Rider ID or Share Link!", Toast.LENGTH_SHORT).show()
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
        connectedRiderId = null
        sosListener?.remove()

        val prefs = getSharedPreferences("FamilyConnectionPrefs", Context.MODE_PRIVATE)
        prefs.edit().remove("saved_rider_id").apply()

        tvStatus.text = "No Rider Linked Yet"
        btnConnect.text = "CONNECT"
        btnConnect.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1E90FF"))
        Toast.makeText(this, "Disconnected from Rider", Toast.LENGTH_SHORT).show()
    }

    private fun loadExistingConnection(tvStatus: TextView, btnConnect: Button) {
        val prefs = getSharedPreferences("FamilyConnectionPrefs", Context.MODE_PRIVATE)
        val savedId = prefs.getString("saved_rider_id", null)

        if (!savedId.isNullOrEmpty()) {
            connectedRiderId = savedId
            tvStatus.text = " Connected ID: $savedId"
            btnConnect.text = "DISCONNECT"
            btnConnect.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.RED)
            listenForSosAlerts(savedId)
        }
    }

    private fun listenForSosAlerts(riderId: String) {
        val layoutSosBanner = findViewById<LinearLayout>(R.id.layout_sos_banner)
        val tvSosMessage = findViewById<TextView>(R.id.tv_sos_message)

        sosListener?.remove()

        sosListener = db.collection("families").document(riderId)
            .collection("sos").document("alert")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val active = snapshot.getBoolean("active") ?: false
                if (active) {
                    tvSosMessage.text = "🚨 RIDER SOS ALERT! ($riderId)"
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