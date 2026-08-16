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

class RiderHomeActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private var isTracking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rider_home)

        sessionManager = SessionManager(this)
        val familyCode = sessionManager.getFamilyCode() ?: "FAM-RAPTOR-8821"

        val btnStartRide = findViewById<Button>(R.id.btn_start_ride)
        val btnShareLocation = findViewById<Button>(R.id.btn_share_location)
        val tvStatus = findViewById<TextView>(R.id.tv_rider_status)

        requestLocationPermissions()

        btnStartRide.setOnClickListener {
            if (!isTracking) {
                startTrackingService(familyCode)
                isTracking = true
                btnStartRide.text = "STOP RIDE"
                btnStartRide.setBackgroundColor(Color.RED)
                tvStatus.text = " Status: Ride Active ($familyCode)"
            } else {
                stopTrackingService()
                isTracking = false
                btnStartRide.text = "START RIDE"
                btnStartRide.setBackgroundColor(Color.parseColor("#1E90FF"))
                tvStatus.text = " Status: Stopped"
            }
        }

        // GENERATE SHARE LINK: rider://FAM-RAPTOR-8821
        btnShareLocation.setOnClickListener {
            val shareUrl = "rider://$familyCode"
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Rider Link", shareUrl)
            clipboard.setPrimaryClip(clip)

            // Share Intent para ma-send sa Messenger/SMS
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Track my live ride on Family Eye: $shareUrl")
            }
            startActivity(Intent.createChooser(shareIntent, "Share Location Link"))
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