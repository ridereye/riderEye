package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
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
import android.widget.ImageButton
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
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline // 👈 Para sa blue route highlight line
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class MapFragment : Fragment(R.layout.fragment_map) {

    private lateinit var mapView: MapView
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var currentDestinationMarker: Marker? = null
    private var currentRoutePolyline: Polyline? = null // 👈 Taguan ng lumang route line para ma-clear
    private lateinit var sessionManager: SessionManager
    private val db = FirebaseFirestore.getInstance()

    private var tvSpeed: TextView? = null
    private var tvBattery: TextView? = null
    private var tvDistanceEta: TextView? = null
    private var recyclerHospitals: RecyclerView? = null
    private lateinit var placeAdapter: NearbyPlaceAdapter

    private val handler = Handler(Looper.getMainLooper())
    private val LOCATION_PERMISSION_REQUEST_CODE = 101

    data class NearbyPlace(
        val name: String,
        val type: String,
        val lat: Double,
        val lon: Double,
        val phone: String,
        var distanceKm: Double = 0.0,
        val hasRealPhone: Boolean = true
    )

    private var nearestPlaces: List<NearbyPlace> = listOf()
    private var lastPlaceFetchTime: Long = 0

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

        recyclerHospitals?.layoutManager = LinearLayoutManager(requireContext())
        placeAdapter = NearbyPlaceAdapter(listOf())
        recyclerHospitals?.adapter = placeAdapter

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

        val defaultCenter = GeoPoint(14.5995, 120.9842)
        mapView.controller.setCenter(defaultCenter)

        checkLocationPermissionAndSetup()

        // 🔑 Agad na maglo-load ang listahan gamit ang default center para hindi blangko
        fetchNearbyPlacesFromOSM(defaultCenter.latitude, defaultCenter.longitude)

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
                    fetchNearbyPlacesFromOSM(userLocation.latitude, userLocation.longitude)
                }
            }
        }
    }

    private fun startLiveUpdaters(familyCode: String) {
        handler.post(object : Runnable {
            override fun run() {
                val batteryPct = getBatteryPercentage()
                tvBattery?.text = "$batteryPct%"

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

    private fun fetchNearbyPlacesFromOSM(lat: Double, lon: Double) {
        Thread {
            val allPlaces = mutableListOf<NearbyPlace>()
            try {
                // Lumingon sa paligid (10km radius) para hindi bumagal ang server
                val query = "[out:json][timeout:15];(" +
                        "nwr(around:10000,$lat,$lon)[amenity=hospital];" +
                        "nwr(around:10000,$lat,$lon)[amenity=police];" +
                        "nwr(around:10000,$lat,$lon)[amenity=fuel];" +
                        ");out center;"

                val url = URL("https://overpass-api.de/api/interpreter")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.setRequestProperty("User-Agent", "RiderEyePh (Contact: ridereyeph@gmail.com)")

                val body = "data=" + URLEncoder.encode(query, "UTF-8")
                connection.outputStream.write(body.toByteArray())

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(response)
                    val elements = jsonObject.getJSONArray("elements")

                    val userGeo = GeoPoint(lat, lon)

                    for (i in 0 until elements.length()) {
                        try {
                            val element = elements.getJSONObject(i)
                            val pLat = if (element.has("lat")) element.getDouble("lat")
                            else element.optJSONObject("center")?.optDouble("lat") ?: 0.0
                            val pLon = if (element.has("lon")) element.getDouble("lon")
                            else element.optJSONObject("center")?.optDouble("lon") ?: 0.0

                            if (pLat == 0.0 || pLon == 0.0) continue

                            val tags = element.optJSONObject("tags")
                            val amenity = tags?.optString("amenity") ?: "hospital"

                            // Kunin ang mismong totoong pangalan o brand sa OSM (Tulad ng Jaen Police Station)
                            val name = tags?.optString("name")
                                ?: tags?.optString("brand")
                                ?: tags?.optString("operator")
                                ?: ""

                            // Kung walang pangalan sa mapa, laktawan para puro may totoong pangalan ang lumabas
                            if (name.isEmpty()) continue

                            val rawPhone = tags?.optString("phone")
                                ?: tags?.optString("contact:phone")
                                ?: tags?.optString("mobile")
                                ?: tags?.optString("contact:mobile")
                                ?: ""

                            val hasReal = rawPhone.isNotEmpty()
                            val formattedPhone = if (hasReal) {
                                if (rawPhone.startsWith("tel:")) rawPhone else "tel:$rawPhone"
                            } else {
                                "tel:911"
                            }

                            val pGeo = GeoPoint(pLat, pLon)
                            val distMeters = userGeo.distanceToAsDouble(pGeo)
                            val distKm = distMeters / 1000.0

                            allPlaces.add(NearbyPlace(name, amenity, pLat, pLon, formattedPhone, distKm, hasReal))
                        } catch (e: Exception) {
                            // Skip invalid nodes
                        }
                    }

                    // Kunin ang pinakamalapit sa bawat kategorya na may totoong pangalan
                    val nearestHospital = allPlaces.filter { it.type == "hospital" }.minByOrNull { it.distanceKm }
                    val nearestPolice = allPlaces.filter { it.type == "police" }.minByOrNull { it.distanceKm }
                    val nearestFuel = allPlaces.filter { it.type == "fuel" }.minByOrNull { it.distanceKm }

                    val selectedPlaces = mutableListOf<NearbyPlace>()
                    if (nearestHospital != null) selectedPlaces.add(nearestHospital)
                    if (nearestPolice != null) selectedPlaces.add(nearestPolice)
                    if (nearestFuel != null) selectedPlaces.add(nearestFuel)

                    val finalPlaces = if (selectedPlaces.isEmpty()) {
                        allPlaces.sortedBy { it.distanceKm }.take(4)
                    } else {
                        selectedPlaces.sortedBy { it.distanceKm }
                    }

                    requireActivity().runOnUiThread {
                        nearestPlaces = finalPlaces
                        placeAdapter.updateData(nearestPlaces)

                        if (nearestPlaces.isNotEmpty()) {
                            val closest = nearestPlaces[0]
                            tvDistanceEta?.text = String.format(Locale.getDefault(), "%.1fkm", closest.distanceKm)
                        } else {
                            tvDistanceEta?.text = "0.0km"
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    inner class NearbyPlaceAdapter(private var places: List<NearbyPlace>) :
        RecyclerView.Adapter<NearbyPlaceAdapter.PlaceViewHolder>() {

        inner class PlaceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvIcon: TextView = view.findViewById(R.id.tv_item_icon)
            val tvName: TextView = view.findViewById(R.id.tv_item_hospital_name)
            val tvDistance: TextView = view.findViewById(R.id.tv_item_hospital_distance)
            val btnCall: ImageButton = view.findViewById(R.id.btn_item_call)
            val btnGo: ImageButton = view.findViewById(R.id.btn_item_go)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_hospital, parent, false)
            return PlaceViewHolder(view)
        }

        override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
            val place = places[position]
            holder.tvName.text = place.name

            when (place.type) {
                "hospital" -> {
                    holder.tvIcon.text = "🏥"
                    holder.tvDistance.text = String.format(Locale.getDefault(), "Hospital • %.1fkm", place.distanceKm)
                }
                "police" -> {
                    holder.tvIcon.text = "🛡️"
                    holder.tvDistance.text = String.format(Locale.getDefault(), "Police Station • %.1fkm", place.distanceKm)
                }
                "fuel" -> {
                    holder.tvIcon.text = "⛽"
                    holder.tvDistance.text = String.format(Locale.getDefault(), "Gas Station • %.1fkm", place.distanceKm)
                }
                else -> {
                    holder.tvIcon.text = "📍"
                    holder.tvDistance.text = String.format(Locale.getDefault(), "Lugar • %.1fkm", place.distanceKm)
                }
            }

            holder.btnCall.setOnClickListener {
                if (!place.hasRealPhone) {
                    Toast.makeText(holder.itemView.context, "Walang nakarehistrong numero ang ${place.name} sa mapa. Tumatawag sa 911.", Toast.LENGTH_LONG).show()
                }
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse(place.phone))
                holder.itemView.context.startActivity(dialIntent)
            }

            holder.btnGo.setOnClickListener {
                myLocationOverlay?.disableFollowLocation()

                val userLoc = myLocationOverlay?.myLocation
                if (userLoc == null) {
                    Toast.makeText(holder.itemView.context, "Naghihintay pa sa GPS location mo...", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val destinationPoint = GeoPoint(place.lat, place.lon)

                currentDestinationMarker?.let { mapView.overlays.remove(it) }
                currentRoutePolyline?.let { mapView.overlays.remove(it) }

                val destinationMarker = Marker(mapView).apply {
                    setPosition(destinationPoint)
                    title = place.name
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                mapView.overlays.add(destinationMarker)
                currentDestinationMarker = destinationMarker
                destinationMarker.showInfoWindow()

                fetchAndDrawRoute(userLoc, destinationPoint)

                mapView.controller.animateTo(destinationPoint)
                mapView.controller.setZoom(16.0)

                Toast.makeText(holder.itemView.context, "Pinapakita ang ruta papuntang ${place.name}", Toast.LENGTH_SHORT).show()
            }
        }

        override fun getItemCount() = places.size

        fun updateData(newPlaces: List<NearbyPlace>) {
            places = newPlaces
            notifyDataSetChanged()
        }
    }

    private fun fetchAndDrawRoute(start: GeoPoint, destination: GeoPoint) {
        Thread {
            var routePoints = mutableListOf<GeoPoint>()
            try {
                val urlStr = "https://router.project-osrm.org/route/v1/driving/${start.longitude},${start.latitude};${destination.longitude},${destination.latitude}?overview=full&geometries=geojson"
                val url = URL(urlStr)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "RiderEyePh (ridereyeph@gmail.com)")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val routes = json.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val geometry = routes.getJSONObject(0).getJSONObject("geometry")
                        val coordinates = geometry.getJSONArray("coordinates")

                        for (i in 0 until coordinates.length()) {
                            val coord = coordinates.getJSONArray(i)
                            val lon = coord.getDouble(0)
                            val lat = coord.getDouble(1)
                            routePoints.add(GeoPoint(lat, lon))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (routePoints.isEmpty()) {
                routePoints.add(start)
                routePoints.add(destination)
            }

            requireActivity().runOnUiThread {
                currentRoutePolyline?.let { mapView.overlays.remove(it) }

                val polyline = Polyline().apply {
                    setPoints(routePoints)
                    outlinePaint.color = Color.parseColor("#00B0FF")
                    outlinePaint.strokeWidth = 12f
                }
                mapView.overlays.add(polyline)
                currentRoutePolyline = polyline
                mapView.invalidate()
            }
        }.start()
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