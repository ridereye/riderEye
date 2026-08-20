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
        private const val KEY_USER_DOC_ID = "user_doc_id"
        private const val KEY_FULL_NAME = "full_name"
    }

    fun saveSession(userId: String, username: String, userType: String, riderId: String, familyCode: String, fullName: String) {
        val editor = prefs.edit()
        editor.putString(KEY_USER_DOC_ID, userId)
        editor.putString(KEY_USERNAME, username)
        editor.putString(KEY_USER_TYPE, userType)
        editor.putString(KEY_RIDER_ID, riderId)
        editor.putString(KEY_FAMILY_CODE, familyCode)
        editor.putString(KEY_FULL_NAME, fullName)
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.apply()
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)
    fun getFullName(): String? = prefs.getString(KEY_FULL_NAME, null)
    fun getUserType(): String? = prefs.getString(KEY_USER_TYPE, null)
    fun getFamilyCode(): String? = prefs.getString(KEY_FAMILY_CODE, null)
    fun getRiderId(): String? = prefs.getString(KEY_RIDER_ID, null)

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}