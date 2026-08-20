package com.example.myapplication

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.appcompat.app.AlertDialog
import com.google.firebase.firestore.FirebaseFirestore

class RiderHomeActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private val db = FirebaseFirestore.getInstance() // Ibinahagi natin ang Firestore instance
    private var isTracking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rider_home)

        sessionManager = SessionManager(this)

        // Kinukuha na nito ang totoong Family/Rider ID mula sa SessionManager
        val riderId = sessionManager.getRiderId() ?: ""

        val btnSos = findViewById<com.google.android.material.card.MaterialCardView>(R.id.btnSos)
        val btnStartRide = findViewById<Button>(R.id.btnStartRide)
        val btnShareLocation = findViewById<Button>(R.id.btnShareLocation)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        requestLocationPermissions()

        btnStartRide.setOnClickListener {
            if (!isTracking) {
                startTrackingService(riderId)
                isTracking = true
                btnStartRide.text = "STOP RIDE"
                btnStartRide.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.RED)
                tvStatus.text = " Status: Ride Active ($riderId)"
            } else {
                stopTrackingService()
                isTracking = false
                btnStartRide.text = "START RIDE"
                btnStartRide.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1E90FF"))
                tvStatus.text = " Status: Stopped"
            }
        }

        // COPY ID & SHARE LINK: rider://FAM-xxxx
        btnShareLocation.setOnClickListener {
            val shareUrl = "rider://$riderId"
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Rider Link", shareUrl)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(this, "Rider ID copied to clipboard!", Toast.LENGTH_SHORT).show()

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Track my live ride on Family Eye: $shareUrl")
            }
            startActivity(Intent.createChooser(shareIntent, "Share Location Link"))
        }

        // SOS BUTTON FUNCTION (Nagsusulat na ngayon sa Firestore)
        btnSos.setOnClickListener {
            if (riderId.isEmpty()) {
                Toast.makeText(this, "Error: Rider ID is missing!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle("EMERGENCY SOS")
                .setMessage("Do you want to send an Emergency Alert to your family?")
                .setPositiveButton("YES, SEND SOS") { _, _ ->
                    // Sine-save natin sa tamang path ng Firestore para mabasa ng Family App
                    val sosData = hashMapOf(
                        "active" to true,
                        "timestamp" to System.currentTimeMillis()
                    )

                    db.collection("families").document(riderId)
                        .collection("sos").document("alert")
                        .set(sosData)
                        .addOnSuccessListener {
                            Toast.makeText(this, "SOS Alert Sent to Family! 🚨", Toast.LENGTH_SHORT).show()
                            tvStatus.text = " Status: SOS EMERGENCY ACTIVE!"
                            tvStatus.setTextColor(Color.RED)
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to send SOS: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .setNegativeButton("CANCEL", null)
                .show()
        }

        // LOGOUT BUTTON FUNCTION
        btnLogout.setOnClickListener {
            sessionManager.clearSession()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun startTrackingService(familyCode: String) {
        val serviceIntent = Intent(this, TrackingService::class.java).apply {
            putExtra("FAMILY_CODE", familyCode)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Toast.makeText(this, "Tracking Service Started", Toast.LENGTH_SHORT).show()
    }

    private fun stopTrackingService() {
        val serviceIntent = Intent(this, TrackingService::class.java)
        stopService(serviceIntent)
        Toast.makeText(this, "Tracking Service Stopped", Toast.LENGTH_SHORT).show()
    }

    private fun requestLocationPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
    }
}