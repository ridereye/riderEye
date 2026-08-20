package com.example.myapplication

data class RideLog(
    val timestamp: Long = 0L,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speed: Int = 0,
    val stop_duration: String = ""
)