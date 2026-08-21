package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class MapFragment : Fragment(R.layout.fragment_map) {

    private lateinit var mapView: MapView
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private lateinit var sessionManager: SessionManager
    private val db = FirebaseFirestore.getInstance()

    private var tvSpeed: TextView? = null
    private var tvBattery: TextView? = null
    private var tvDistanceEta: TextView? = null
    private var recyclerHospitals: RecyclerView? = null
    private lateinit var hospitalAdapter: HospitalAdapter

    private val handler = Handler(Looper.getMainLooper())
    private val LOCATION_PERMISSION_REQUEST_CODE = 101

    data class Hospital(val name: String, val lat: Double, val lon: Double, val phone: String, var distanceKm: Double = 0.0)
    private var nearestHospitals: List<Hospital> = listOf()
    private var lastHospitalFetchTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(
            requireContext(),
            PreferenceManager.getDefaultSharedPreferences(requireContext())
        )
        Configuration.getInstance().userAgentValue = "RiderEyePh (Contact: ridereyeph@gmail.com)"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        tvSpeed = view.findViewById(R.id.tv_rider_speed)
        tvBattery = view.findViewById(R.id.tv_rider_battery)
        tvDistanceEta = view.findViewById(R.id.tv_distance_eta)
        recyclerHospitals = view.findViewById(R.id.recycler_hospitals)

        // Setup RecyclerView para sa patong-patong na 4 na ospital
        recyclerHospitals?.layoutManager = LinearLayoutManager(requireContext())
        hospitalAdapter = HospitalAdapter(listOf())
        recyclerHospitals?.adapter = hospitalAdapter

        val cartoDbVoyager = XYTileSource(
            "CartoDBVoyager",
            0, 19, 256, ".png", arrayOf(
                "https://basemaps.cartocdn.com/rastertiles/voyager/"
            ),
            "© OpenStreetMap contributors © CARTO"
        )
        mapView = view.findViewById(R.id.map_fragment_item)
        mapView.setTileSource(cartoDbVoyager)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(16.0)
        mapView.controller.setCenter(GeoPoint(14.5995, 120.9842))

        checkLocationPermissionAndSetup()

        val familyCode = sessionManager.getFamilyCode() ?: ""
        val btnSos = requireActivity().findViewById<Button>(R.id.btn_rider_sos)
        btnSos?.setOnClickListener {
            triggerSosAlert(familyCode)
        }

        startLiveUpdaters(familyCode)
    }

    private fun checkLocationPermissionAndSetup() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            setupMyLocation()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupMyLocation()
            } else {
                Toast.makeText(requireContext(), "Kailangan ang Location Permission para sa tracking!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupMyLocation() {
        val provider = GpsMyLocationProvider(requireContext())
        myLocationOverlay = MyLocationNewOverlay(provider, mapView)
        myLocationOverlay?.enableMyLocation()
        myLocationOverlay?.enableFollowLocation()
        mapView.overlays.add(myLocationOverlay)

        myLocationOverlay?.runOnFirstFix {
            requireActivity().runOnUiThread {
                val userLocation = myLocationOverlay?.myLocation
                if (userLocation != null) {
                    mapView.controller.animateTo(userLocation)
                    mapView.controller.setZoom(17.0)
                    fetchHospitalsFromOSM(userLocation.latitude, userLocation.longitude)
                }
            }
        }
    }

    private fun startLiveUpdaters(familyCode: String) {
        handler.post(object : Runnable {
            override fun run() {
                val batteryPct = getBatteryPercentage()
                tvBattery?.text = "$batteryPct%"

                val userLocation = myLocationOverlay?.myLocation
                if (userLocation != null) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastHospitalFetchTime > 20000) {
                        lastHospitalFetchTime = currentTime
                        fetchHospitalsFromOSM(userLocation.latitude, userLocation.longitude)
                    }
                }

                if (familyCode.isNotEmpty()) {
                    db.collection("families").document(familyCode)
                        .collection("location").document("current")
                        .get()
                        .addOnSuccessListener { doc ->
                            if (doc != null && doc.exists()) {
                                val speed = doc.getLong("speed")?.toInt() ?: 0
                                tvSpeed?.text = "$speed"
                            }
                        }
                }

                handler.postDelayed(this, 3000)
            }
        })
    }

    private fun fetchHospitalsFromOSM(lat: Double, lon: Double) {
        Thread {
            try {
                val query = "[out:json];node(around:10000,$lat,$lon)[amenity=hospital];out body;"
                val url = URL("https://overpass-api.de/api/interpreter")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val body = "data=" + URLEncoder.encode(query, "UTF-8")
                connection.outputStream.write(body.toByteArray())

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(response)
                    val elements = jsonObject.getJSONArray("elements")

                    val hospitalList = mutableListOf<Hospital>()
                    val userGeo = GeoPoint(lat, lon)

                    for (i in 0 until elements.length()) {
                        try {
                            val element = elements.getJSONObject(i)
                            val hLat = element.getDouble("lat")
                            val hLon = element.getDouble("lon")

                            val tags = element.optJSONObject("tags")
                            val name = tags?.optString("name", "Malapit na Ospital") ?: "Malapit na Ospital"
                            val rawPhone = tags?.optString("phone") ?: tags?.optString("contact:phone") ?: "911"
                            val phone = if (rawPhone.startsWith("tel:")) rawPhone else "tel:$rawPhone"

                            val hGeo = GeoPoint(hLat, hLon)
                            val distMeters = userGeo.distanceToAsDouble(hGeo)
                            val distKm = distMeters / 1000.0

                            hospitalList.add(Hospital(name, hLat, hLon, phone, distKm))
                        } catch (e: Exception) {
                            // Skip invalid nodes
                        }
                    }

                    val top4Hospitals = hospitalList.sortedBy { it.distanceKm }.take(4)

                    requireActivity().runOnUiThread {
                        nearestHospitals = top4Hospitals
                        hospitalAdapter.updateData(nearestHospitals)

                        if (nearestHospitals.isNotEmpty()) {
                            val closest = nearestHospitals[0]
                            val distanceKm = closest.distanceKm
                            val etaMinutes = (distanceKm * 2).toInt().coerceAtLeast(1)
                            tvDistanceEta?.text = String.format(Locale.getDefault(), "%.1fkm • %dm ETA", distanceKm, etaMinutes)
                        } else {
                            tvDistanceEta?.text = "Walang ospital"
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    // RecyclerView Adapter para sa patong-patong na ospital list
    inner class HospitalAdapter(private var hospitals: List<Hospital>) :
        RecyclerView.Adapter<HospitalAdapter.HospitalViewHolder>() {

        inner class HospitalViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tv_item_hospital_name)
            val tvDistance: TextView = view.findViewById(R.id.tv_item_hospital_distance)
            val btnCall: Button = view.findViewById(R.id.btn_item_call)
            val btnGo: Button = view.findViewById(R.id.btn_item_go)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HospitalViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_hospital, parent, false)
            return HospitalViewHolder(view)
        }

        override fun onBindViewHolder(holder: HospitalViewHolder, position: Int) {
            val hospital = hospitals[position]
            holder.tvName.text = hospital.name
            val etaMinutes = (hospital.distanceKm * 2).toInt().coerceAtLeast(1)
            holder.tvDistance.text = String.format(Locale.getDefault(), "Hospital • %.1fkm • %d min", hospital.distanceKm, etaMinutes)

            // Button para tawagan ang tiyak na ospital
            holder.btnCall.setOnClickListener {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse(hospital.phone))
                holder.itemView.context.startActivity(dialIntent)
            }

            // Button para i-locate at ituro sa mapa ang tiyak na ospital
            holder.btnGo.setOnClickListener {
                val hospitalPoint = GeoPoint(hospital.lat, hospital.lon)
                mapView.controller.animateTo(hospitalPoint)
                mapView.controller.setZoom(17.0)
                Toast.makeText(holder.itemView.context, "Nilo-locate ang ${hospital.name}", Toast.LENGTH_SHORT).show()
            }
        }

        override fun getItemCount() = hospitals.size

        fun updateData(newHospitals: List<Hospital>) {
            hospitals = newHospitals
            notifyDataSetChanged()
        }
    }

    private fun getBatteryPercentage(): Int {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = requireContext().registerReceiver(null, intentFilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) ((level / scale.toFloat()) * 100).toInt() else 0
    }

    private fun triggerSosAlert(familyCode: String) {
        if (familyCode.isEmpty()) {
            Toast.makeText(requireContext(), "No Family Code found!", Toast.LENGTH_SHORT).show()
            return
        }

        val sosData = hashMapOf("active" to true, "timestamp" to System.currentTimeMillis())
        db.collection("families").document(familyCode)
            .collection("sos").document("alert")
            .set(sosData)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "🚨 SOS SENT TO FAMILY!", Toast.LENGTH_LONG).show()
            }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        myLocationOverlay?.enableMyLocation()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        myLocationOverlay?.disableMyLocation()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        mapView.onDetach()
    }
}