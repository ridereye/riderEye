package com.example.myapplication

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class SafetyFragment : Fragment(R.layout.fragment_safety) {

    private lateinit var sessionManager: SessionManager
    private val db = FirebaseFirestore.getInstance()

    private var tvSpeed: TextView? = null
    private var tvStatus: TextView? = null
    private var tvDistance: TextView? = null
    private var tvEta: TextView? = null
    private var tvBattery: TextView? = null
    private var tvBatteryStatus: TextView? = null
    private var recyclerEmergencies: RecyclerView? = null

    private val handler = Handler(Looper.getMainLooper())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())

        tvSpeed = view.findViewById(R.id.tv_safety_speed)
        tvStatus = view.findViewById(R.id.tv_safety_status)
        tvDistance = view.findViewById(R.id.tv_safety_distance)
        tvEta = view.findViewById(R.id.tv_safety_eta)
        tvBattery = view.findViewById(R.id.tv_safety_battery)
        tvBatteryStatus = view.findViewById(R.id.tv_safety_battery_status)
        recyclerEmergencies = view.findViewById(R.id.recycler_safety_emergencies)

        recyclerEmergencies?.layoutManager = LinearLayoutManager(requireContext())
        // Kung gusto mo gamitin ulit ang parehong NearbyPlaceAdapter mula sa MapFragment,
        // pwedeng i-refactor iyon papuntang standalone class para magamit dito rin.
        // Sa ngayon, i-populate lang natin ang live stats (speed/battery).

        val familyCode = sessionManager.getFamilyCode() ?: ""
        startLiveUpdater(familyCode)
    }

    private fun startLiveUpdater(familyCode: String) {
        handler.post(object : Runnable {
            override fun run() {
                // Battery
                val batteryPct = getBatteryPercentage()
                tvBattery?.text = "$batteryPct%"
                tvBatteryStatus?.text = if (batteryPct <= 20) "Low Battery" else "Normal Status"

                // Live speed mula sa Firestore
                if (familyCode.isNotEmpty()) {
                    db.collection("families").document(familyCode)
                        .collection("location").document("current")
                        .get()
                        .addOnSuccessListener { doc ->
                            if (doc != null && doc.exists()) {
                                val speed = doc.getLong("speed")?.toInt() ?: 0
                                tvSpeed?.text = "$speed"

                                tvStatus?.text = if (speed > 60) {
                                    "🔴 Warning - Over 60 km/h"
                                } else {
                                    "🟢 Safe - Under 60 km/h"
                                }

                                val distanceKm = doc.getDouble("distanceFromHome") ?: 0.0
                                tvDistance?.text = String.format(Locale.getDefault(), "%.1fkm", distanceKm)
                            }
                        }
                }

                handler.postDelayed(this, 3000)
            }
        })
    }

    private fun getBatteryPercentage(): Int {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = requireContext().registerReceiver(null, intentFilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) ((level / scale.toFloat()) * 100).toInt() else 0
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
    }
}