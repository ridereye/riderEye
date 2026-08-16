package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("RiderEyeSession", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USERNAME = "username"
        private const val KEY_USER_TYPE = "user_type"
        private const val KEY_FAMILY_CODE = "family_code"
        private const val KEY_RIDER_ID = "rider_id"
    }

    fun saveSession(username: String, userType: String, familyCode: String? = null, riderId: String? = null) {
        val editor = prefs.edit()
        editor.putBoolean(KEY_IS_LOGGED_IN, true) // Idinagdag para sa auto-login check
        editor.putString(KEY_USERNAME, username)
        editor.putString(KEY_USER_TYPE, userType)
        familyCode?.let { editor.putString(KEY_FAMILY_CODE, it) }
        riderId?.let { editor.putString(KEY_RIDER_ID, it) }
        editor.apply()
    }

    // Idinagdag para mawala ang error sa LoginActivity
    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)
    fun getUserType(): String? = prefs.getString(KEY_USER_TYPE, null)
    fun getFamilyCode(): String? = prefs.getString(KEY_FAMILY_CODE, null)
    fun getRiderId(): String? = prefs.getString(KEY_RIDER_ID, null)

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}