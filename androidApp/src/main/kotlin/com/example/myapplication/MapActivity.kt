package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Locale

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private val db = FirebaseFirestore.getInstance()
    private var locationListener: ListenerRegistration? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var familyLatLng: LatLng? = null

    private var riderMarker: Marker? = null
    private var familyMarker: Marker? = null

    private lateinit var tvDistance: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvBattery: TextView
    private lateinit var tvMapStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        tvDistance = findViewById(R.id.tv_distance)
        tvSpeed = findViewById(R.id.tv_speed)
        tvBattery = findViewById(R.id.tv_battery)
        tvMapStatus = findViewById(R.id.tv_map_status)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        getFamilyCurrentLocation()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        val familyCode = intent.getStringExtra("FAMILY_CODE") ?: ""
        if (familyCode.isNotEmpty()) {
            listenToRiderLocation(familyCode)
        } else {
            Toast.makeText(this, "No Family Code specified!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFamilyCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    familyLatLng = LatLng(loc.latitude, loc.longitude)
                    if (::mMap.isInitialized) {
                        familyMarker?.remove()
                        familyMarker = mMap.addMarker(
                            MarkerOptions()
                                .position(familyLatLng!!)
                                .title("Your Location (Family)")
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                        )
                    }
                }
            }
        }
    }

    private fun listenToRiderLocation(familyCode: String) {
        locationListener = db.collection("families").document(familyCode)
            .collection("location").document("current")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || !snapshot.exists()) {
                    tvMapStatus.text = "Status: No active tracking data yet"
                    return@addSnapshotListener
                }

                val lat = snapshot.getDouble("latitude") ?: 0.0
                val lng = snapshot.getDouble("longitude") ?: 0.0
                val speed = snapshot.getLong("speed")?.toInt() ?: 0
                val battery = snapshot.getLong("battery")?.toInt() ?: 0
                val status = snapshot.getString("status") ?: "UNKNOWN"

                val riderLatLng = LatLng(lat, lng)

                // 1. Update UI Text Cards
                tvSpeed.text = "$speed km/h"
                tvBattery.text = "$battery %"
                tvMapStatus.text = "Status: Rider is $status"

                // 2. Compute Distance via Haversine Formula
                familyLatLng?.let { fLoc ->
                    val distKm = calculateHaversineDistance(fLoc.latitude, fLoc.longitude, lat, lng)
                    tvDistance.text = String.format(Locale.getDefault(), "%.2f km", distKm)
                }

                // 3. Update Map Marker
                if (::mMap.isInitialized) {
                    if (riderMarker == null) {
                        riderMarker = mMap.addMarker(
                            MarkerOptions()
                                .position(riderLatLng)
                                .title("Rider Position")
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                        )
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(riderLatLng, 15f))
                    } else {
                        riderMarker?.position = riderLatLng
                    }
                }
            }
    }

    // Haversine Formula: Kinukwenta ang eksaktong layo ng 2 GPS coordinates
    private fun calculateHaversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Radius ng mundo sa kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    override fun onDestroy() {
        super.onDestroy()
        locationListener?.remove()
    }
}