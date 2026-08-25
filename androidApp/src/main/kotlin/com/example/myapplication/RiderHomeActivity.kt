package com.example.myapplication

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

class RiderHomeActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rider_home)

        sessionManager = SessionManager(this)

        val familyCode = sessionManager.getFamilyCode() ?: ""
        if (familyCode.isNotEmpty()) {
            val serviceIntent = Intent(this, TrackingService::class.java).apply {
                putExtra("FAMILY_CODE", familyCode)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }

        if (savedInstanceState == null) {
            loadFragment(MapFragment())
        }

        findViewById<LinearLayout>(R.id.nav_map).setOnClickListener { loadFragment(MapFragment()) }
        findViewById<LinearLayout>(R.id.nav_safety).setOnClickListener { loadFragment(SafetyFragment()) }
        findViewById<LinearLayout>(R.id.nav_bell).setOnClickListener {
            Toast.makeText(this, "Notifications", Toast.LENGTH_SHORT).show()
        }
        findViewById<LinearLayout>(R.id.nav_settings).setOnClickListener {
            sessionManager.clearSession()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}