package com.example.myapplication

data class NearbyPlace(
    val name: String,
    val type: String,
    val lat: Double,
    val lon: Double,
    val phone: String,
    var distanceKm: Double = 0.0,
    val hasRealPhone: Boolean = true
)