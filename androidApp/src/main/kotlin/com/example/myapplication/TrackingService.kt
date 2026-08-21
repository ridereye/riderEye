package com.example.myapplication

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.firestore.FirebaseFirestore

class TrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val db = FirebaseFirestore.getInstance()
    private var familyCode: String = ""
    private var stopStartTime: Long = 0
    private var hasLoggedStop = false // <-- Idinagdag para iwas-spam sa Firestore logs

    companion object {
        const val CHANNEL_ID = "rider_tracking_channel"
        const val NOTIF_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val speedKmH = (location.speed * 3.6).toInt() // Convert m/s to km/h
                val batteryLevel = getBatteryPercentage()
                val status = if (speedKmH > 2) "RIDING" else "STOPPED"

                updateNotification("Rider Eye Tracking ON - $speedKmH km/h ($status)")

                if (familyCode.isNotEmpty()) {
                    saveTrackingData(location.latitude, location.longitude, speedKmH, batteryLevel, status)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        familyCode = intent?.getStringExtra("FAMILY_CODE") ?: ""

        val notification = createNotification("Rider Eye Tracking Started...")
        startForeground(NOTIF_ID, notification)

        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000) // Every 10 secs
            .setMinUpdateIntervalMillis(5000)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (_: SecurityException) { }
    }

    private fun saveTrackingData(lat: Double, lng: Double, speed: Int, battery: Int, status: String) {
        val now = System.currentTimeMillis()

        val locationData = hashMapOf(
            "latitude" to lat,
            "longitude" to lng,
            "speed" to speed,
            "battery" to battery,
            "status" to status,
            "timestamp" to now
        )

        // 1. Update Current Location Node (Live location laging nau-update)
        db.collection("families").document(familyCode)
            .collection("location").document("current")
            .set(locationData)

        // 2. Trip Log Tracking (Isang beses lang maglalagay ng log kada hinto kapag umabot ng 1 minuto)
        if (speed == 0) {
            if (stopStartTime == 0L) {
                stopStartTime = now
                hasLoggedStop = false // I-reset kapag bagong hinto
            }
            val stopDurationMinutes = ((now - stopStartTime) / 60000).toInt()

            // Mag-log lang kapag umabot ng 1 min at HINDI PA na-log ang pagka-hritong ito
            if (stopDurationMinutes >= 1 && !hasLoggedStop) {
                val logEntry = hashMapOf(
                    "timestamp" to now,
                    "latitude" to lat,
                    "longitude" to lng,
                    "speed" to speed,
                    "stop_duration" to "$stopDurationMinutes mins"
                )
                db.collection("families").document(familyCode)
                    .collection("logs").add(logEntry)

                hasLoggedStop = true // I-lock para hindi na paulit-ulit magdagdag habang nakatigil pa rin
            }
        } else {
            stopStartTime = 0L // Reset stop timer kapag umandar na ulit
            hasLoggedStop = false // I-reset ang flag para sa susunod na hinto
        }
    }

    private fun getBatteryPercentage(): Int {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, intentFilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) ((level / scale.toFloat()) * 100).toInt() else 0
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Rider Eye Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, createNotification(contentText))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Rider Tracking Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}