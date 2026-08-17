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

    fun saveSession(userId: String, username: String, userType: String, riderId: String, familyCode: String, fullName: String) {
        val editor = prefs.edit()
        editor.putString("USER_DOC_ID", userId)
        editor.putString("USER_NAME", username)
        editor.putString("USER_TYPE", userType)
        editor.putString("RIDER_ID", riderId)
        editor.putString("FAMILY_CODE", familyCode)
        editor.putString("FULL_NAME", fullName)
        editor.putBoolean("IS_LOGGED_IN", true)
        editor.apply()
    }

    // Idinagdag para mawala ang error sa LoginActivity
    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun getUsername(): String? { return prefs.getString("FULL_NAME", null) }
    fun getFullName(): String? = prefs.getString(KEY_USERNAME, null)
    fun getUserType(): String? = prefs.getString(KEY_USER_TYPE, null)
    fun getFamilyCode(): String? = prefs.getString(KEY_FAMILY_CODE, null)
    fun getRiderId(): String? = prefs.getString(KEY_RIDER_ID, null)

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}