package com.example.myapplication

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.abs

class RiderHomeActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private val db = FirebaseFirestore.getInstance() // ✅ Idinagdag para sa Firebase

    // SOS Gesture Variables
    private var isHoldLocked = false
    private var holdStartTime = 0L
    private var startX = 0f
    private var startY = 0f
    private val HOLD_DELAY_MS = 800 // 0.8 segundo na paghawak

    // SOS Type Constants — ito ang ipapadala para makita sa Family Eye
    companion object {
        const val SOS_TYPE = "sos_type"
        const val SOS_ACCIDENT = "accident"
        const val SOS_MECHANICAL = "mechanical"
        const val SOS_MEDICAL = "medical"
        const val SOS_HOLDUP = "holdup"
    }

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

        // Bottom Navigation Clicks
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

        // 🆘 SOS BUTTON — HOLD + SWIPE + SEND SOS TYPE
        findViewById<View>(R.id.btn_rider_sos).setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    holdStartTime = System.currentTimeMillis()
                    startX = event.rawX
                    startY = event.rawY
                    isHoldLocked = false
                    view.scaleX = 0.9f
                    view.scaleY = 0.9f
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val elapsed = System.currentTimeMillis() - holdStartTime
                    if (elapsed >= HOLD_DELAY_MS && !isHoldLocked) {
                        isHoldLocked = true
                        Toast.makeText(this, "🔴 SOS READY — Swipe to choose emergency", Toast.LENGTH_SHORT).show()
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.scaleX = 1f
                    view.scaleY = 1f

                    if (isHoldLocked) {
                        val endX = event.rawX
                        val endY = event.rawY
                        val dx = endX - startX
                        val dy = endY - startY
                        val absDx = abs(dx)
                        val absDy = abs(dy)

                        when {
                            // ⬆️ SWIPE UP — Accident / Crash
                            absDy > absDx && dy < -50 -> {
                                sendSosAlert(SOS_ACCIDENT, "🚨 Accident / Crash")
                            }
                            // ⬇️ SWIPE DOWN — Mechanical Breakdown
                            absDy > absDx && dy > 50 -> {
                                sendSosAlert(SOS_MECHANICAL, "🔧 Mechanical Breakdown")
                            }
                            // ⬅️ SWIPE LEFT — Medical Emergency
                            absDx > absDy && dx < -50 -> {
                                sendSosAlert(SOS_MEDICAL, "🏥 Medical Emergency")
                            }
                            // ➡️ SWIPE RIGHT — Holdup / Crime
                            absDx > absDy && dx > 50 -> {
                                sendSosAlert(SOS_HOLDUP, "⚠️ Holdup / Crime")
                            }
                            else -> {
                                Toast.makeText(this, "🔴 Hold & Swipe to send SOS", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "🔴 Hold button longer then swipe", Toast.LENGTH_SHORT).show()
                    }

                    isHoldLocked = false
                    true
                }

                else -> false
            }
        }
    }

    // ✅ Ipadala ang SOS Alert — Ise-save sa Firebase + Ipapasa sa TrackingService
    private fun sendSosAlert(sosType: String, displayText: String) {
        Toast.makeText(this, "$displayText reported!", Toast.LENGTH_LONG).show()

        val familyCode = sessionManager.getFamilyCode() ?: return

        // 📤 Ipadala sa TrackingService — para ma-monitor sa Family Eye
        val serviceIntent = Intent(this, TrackingService::class.java).apply {
            action = "SEND_SOS"
            putExtra("FAMILY_CODE", familyCode)
            putExtra(SOS_TYPE, sosType)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // 🔴 STEP 2 — I-SAVE SA FIREBASE ← ITO ANG IDINAGDAG!
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val sosData = hashMapOf(
                    "active" to true,
                    "sos_type" to sosType,
                    "timestamp" to System.currentTimeMillis()
                )

                db.collection("families")
                    .document(familyCode)
                    .collection("sos")
                    .document("alert")
                    .set(sosData)
                    .addOnSuccessListener {
                        Toast.makeText(this@RiderHomeActivity, "✅ SOS Sent to Family!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this@RiderHomeActivity, "❌ Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}