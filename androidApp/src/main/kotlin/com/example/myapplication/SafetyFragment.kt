package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.core.app.ActivityCompat
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
    private lateinit var emergencyAdapter: NearbyPlaceAdapter

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
        // Dito sa Safety tab, wala tayong sariling MapView, kaya ang "Go" ay
        // maglulunsad na lang ng external Maps app patungo sa lugar.
        emergencyAdapter = NearbyPlaceAdapter(listOf()) { place -> openInExternalMaps(place) }
        recyclerEmergencies?.adapter = emergencyAdapter

        val familyCode = sessionManager.getFamilyCode() ?: ""
        startLiveUpdater(familyCode)
        loadNearestEmergencies()
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

    /**
     * Kinukuha ang last known GPS location ng device (parehong device ito na
     * gumagamit ng Map tab, kaya malamang naka-grant na ang permission bago
     * pa dumating dito ang user) tapos ipinapasa sa shared NearbyPlacesFetcher.
     * Kung walang location pa (hal. bagong install, hindi pa binuksan ang Map
     * tab), babalik sa default center (Cabanatuan area) para hindi blangko.
     */
    private fun loadNearestEmergencies() {
        val hasPermission = ActivityCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        var lat = 14.5995
        var lon = 120.9842

        if (hasPermission) {
            try {
                val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (lastKnown != null) {
                    lat = lastKnown.latitude
                    lon = lastKnown.longitude
                }
            } catch (e: SecurityException) {
                // Walang permission pala talaga — gagamitin na lang ang default center
            }
        }

        NearbyPlacesFetcher.fetchNearestEmergencyPlaces(lat, lon) { places ->
            emergencyAdapter.updateData(places)
        }
    }

    private fun openInExternalMaps(place: NearbyPlace) {
        val gmmIntentUri = Uri.parse("geo:${place.lat},${place.lon}?q=${Uri.encode(place.name)}")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")

        if (mapIntent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(mapIntent)
        } else {
            // Walang naka-install na Google Maps — gamitin na lang ang generic geo intent
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${place.lat},${place.lon}")))
        }
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