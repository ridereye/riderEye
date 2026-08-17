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

class RiderHomeActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private var isTracking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rider_home)

        sessionManager = SessionManager(this)

        // Kinukuha na nito ang totoong Family Code mula sa SessionManager, may fallback lang sakaling blangko
        val familyCode = sessionManager.getFamilyCode() ?: "FAM-RAPTOR-8821"

        val btnSos = findViewById<com.google.android.material.card.MaterialCardView>(R.id.btnSos)
        val btnStartRide = findViewById<Button>(R.id.btnStartRide)
        val btnShareLocation = findViewById<Button>(R.id.btnShareLocation)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        requestLocationPermissions()

        btnStartRide.setOnClickListener {
            if (!isTracking) {
                startTrackingService(familyCode)
                isTracking = true
                btnStartRide.text = "STOP RIDE"
                btnStartRide.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.RED)
                tvStatus.text = " Status: Ride Active ($familyCode)"
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
            val shareUrl = "rider://$familyCode"
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Rider Link", shareUrl)
            clipboard.setPrimaryClip(clip)

            // Naglalagay tayo ng Toast notification para alam ng user na nakopya na
            Toast.makeText(this, "Family Code & Link copied to clipboard!", Toast.LENGTH_SHORT).show()

            // Share Intent para ma-send sa Messenger/SMS
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Track my live ride on Family Eye: $shareUrl")
            }
            startActivity(Intent.createChooser(shareIntent, "Share Location Link"))
        }

        // SOS BUTTON FUNCTION (May Confirmation Dialog)
        btnSos.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("EMERGENCY SOS")
                .setMessage("Gusto mo bang magpadala ng Emergency Alert sa iyong pamilya?")
                .setPositiveButton("OO, SEND SOS") { _, _ ->
                    Toast.makeText(this, "SOS Alert Sent!", Toast.LENGTH_SHORT).show()
                    tvStatus.text = " Status: SOS EMERGENCY ACTIVE!"
                    tvStatus.setTextColor(Color.RED)
                }
                .setNegativeButton("Kanselahin", null)
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
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
    }
}