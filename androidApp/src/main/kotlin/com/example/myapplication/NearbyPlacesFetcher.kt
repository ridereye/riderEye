package com.example.myapplication

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Shared/reusable na Overpass API fetcher. Dating naka-loob lang sa MapFragment
 * ang lohika na ito ("fetchNearbyPlacesFromOSM") — inilipat dito para magamit
 * din ng SafetyFragment nang hindi na kailangang kopyahin ang buong function.
 *
 * Tumatakbo sa background Thread, at ang onResult callback ay awtomatikong
 * tinatawag sa main/UI thread — kaya safe na direktang mag-update ng UI
 * (hal. adapter.updateData(...)) sa loob ng callback.
 */
object NearbyPlacesFetcher {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun fetchNearestEmergencyPlaces(lat: Double, lon: Double, onResult: (List<NearbyPlace>) -> Unit) {
        Thread {
            val allPlaces = mutableListOf<NearbyPlace>()
            try {
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

                            val name = tags?.optString("name")
                                ?: tags?.optString("brand")
                                ?: tags?.optString("operator")
                                ?: ""

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

                    mainHandler.post { onResult(finalPlaces) }
                } else {
                    mainHandler.post { onResult(emptyList()) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mainHandler.post { onResult(emptyList()) }
            }
        }.start()
    }
}