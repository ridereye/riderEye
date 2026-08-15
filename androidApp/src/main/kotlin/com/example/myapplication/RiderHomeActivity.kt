package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class RiderHomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rider_home)

        val btnSos = findViewById<Button>(R.id.btn_sos)
        val btnStartRide = findViewById<Button>(R.id.btn_start_ride)
        val btnLogout = findViewById<Button>(R.id.btn_rider_logout)

        btnSos.setOnClickListener {
            Toast.makeText(this, "SOS Signal Triggered!", Toast.LENGTH_SHORT).show()
        }

        btnStartRide.setOnClickListener {
            Toast.makeText(this, "Ride Tracking Started...", Toast.LENGTH_SHORT).show()
        }

        btnLogout.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}