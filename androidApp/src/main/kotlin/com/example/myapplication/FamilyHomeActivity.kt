package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class FamilyHomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_family_home)

        val btnTrack = findViewById<Button>(R.id.btn_track_location)
        val btnLogout = findViewById<Button>(R.id.btn_family_logout)

        btnTrack.setOnClickListener {
            Toast.makeText(this, "Opening Live Location Map...", Toast.LENGTH_SHORT).show()
        }

        btnLogout.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}