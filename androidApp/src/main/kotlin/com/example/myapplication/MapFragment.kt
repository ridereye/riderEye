package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
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

    private var nearestPlaces: List<NearbyPlace> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(
            requireContext(),
            PreferenceManager.getDefaultSharedPreferences(requireContext())
        )
        Configuration.getInstance().userAgentValue = "RiderEyePh (Contact: ridereyeph@gmail.com)"

        // Palakihin ang disk cache (default ay medyo mababa) — para once
        // na-download na ang tiles ng isang lugar (hal. Jaen, Nueva Ecija),
        // mananatili ito nang mas matagal sa cache at hindi na paulit-ulit
        // dina-download tuwing babalikan mo ang parehong area. Malaking
        // tulong ito lalo na sa mabagal o unstable na mobile data.
        Configuration.getInstance().tileFileSystemCacheMaxBytes = 300L * 1024 * 1024 // 300MB
        Configuration.getInstance().tileFileSystemCacheTrimBytes = 250L * 1024 * 1024 // 250MB

        // Default lang ng osmdroid ay 2 concurrent tile downloads kasabay —
        // kaya "unti-unti"/isa-isa lumalabas ang mapa kahit malakas ang
        // signal. Dinagdagan dito para sabay-sabay na madodownload ang
        // mas maraming tiles, mas mabilis mapuno ang screen.
        Configuration.getInstance().tileDownloadThreads = 8
        Configuration.getInstance().tileFileSystemThreads = 8
        Configuration.getInstance().tileDownloadMaxQueueSize = 40
        Configuration.getInstance().tileFileSystemMaxQueueSize = 40
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())
        tvSpeed = view.findViewById(R.id.tv_rider_speed)
        tvBattery = view.findViewById(R.id.tv_rider_battery)
        tvDistanceEta = view.findViewById(R.id.tv_distance_eta)
        recyclerHospitals = view.findViewById(R.id.recycler_hospitals)

        recyclerHospitals?.layoutManager = LinearLayoutManager(requireContext())
        // "Go" dito ay gumagawa ng route sa mismong MapView (dati itong nasa loob
        // ng inner adapter class — ngayon callback na lang dahil shared adapter na ito)
        placeAdapter = NearbyPlaceAdapter(listOf()) { place -> goToPlaceOnMap(place) }
        recyclerHospitals?.adapter = placeAdapter

        // Mapbox Streets style tiles — 256px (walang size segment sa URL, kaya
        // default 256px na compatible agad sa osmdroid, walang kailangang
        // zoom-offset adjustment). Palitan ang YOUR_MAPBOX_ACCESS_TOKEN ng
        // sarili mong public token mula sa account.mapbox.com (nagsisimula sa "pk.").
        val mapboxStreets = XYTileSource(
            "MapboxStreets",
            0, 19, 256, "?access_token=" + BuildConfig.MAPBOX_ACCESS_TOKEN, arrayOf(
                "https://api.mapbox.com/styles/v1/mapbox/streets-v12/tiles/"
            ),
            "© Mapbox © OpenStreetMap contributors"
        )
        mapView = view.findViewById(R.id.map_fragment_item)
        mapView.setTileSource(mapboxStreets)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(14.0)

        val defaultCenter = GeoPoint(14.5995, 120.9842)
        mapView.controller.setCenter(defaultCenter)

        checkLocationPermissionAndSetup()

        // 🔑 Agad na maglo-load ang listahan gamit ang default center para hindi blangko
        loadNearbyPlaces(defaultCenter.latitude, defaultCenter.longitude)

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
                    mapView.controller.setZoom(15.0)
                    loadNearbyPlaces(userLocation.latitude, userLocation.longitude)
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

    /**
     * Dating "fetchNearbyPlacesFromOSM" — ngayon tumatawag na lang sa shared
     * NearbyPlacesFetcher, tapos ina-update ang RecyclerView at distance/ETA text.
     */
    private fun loadNearbyPlaces(lat: Double, lon: Double) {
        NearbyPlacesFetcher.fetchNearestEmergencyPlaces(lat, lon) { places ->
            nearestPlaces = places
            placeAdapter.updateData(nearestPlaces)

            if (nearestPlaces.isNotEmpty()) {
                val closest = nearestPlaces[0]
                tvDistanceEta?.text = String.format(Locale.getDefault(), "%.1fkm", closest.distanceKm)
            } else {
                tvDistanceEta?.text = "0.0km"
            }
        }
    }

    private fun goToPlaceOnMap(place: NearbyPlace) {
        myLocationOverlay?.disableFollowLocation()

        val userLoc = myLocationOverlay?.myLocation
        if (userLoc == null) {
            Toast.makeText(requireContext(), "Naghihintay pa sa GPS location mo...", Toast.LENGTH_SHORT).show()
            return
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

        Toast.makeText(requireContext(), "Pinapakita ang ruta papuntang ${place.name}", Toast.LENGTH_SHORT).show()
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
                    val json = org.json.JSONObject(response)
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

            // activity?.runOnUiThread (hindi requireActivity()) para hindi mag-crash
            // kapag naiwan ang Thread na 'to habang naka-alis na sa fragment/tab.
            // Dinoble pang chinecheck ang isAdded sa loob para sigurado.
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread

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