package com.example.myapplication

data class RideLog(
    val rider_username: String = "",
    val start_time: Long? = null,
    val end_time: Long? = null,
    val status: String = ""
)