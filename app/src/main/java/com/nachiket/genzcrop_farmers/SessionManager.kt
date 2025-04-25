package com.nachiket.genzcrop_farmers

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "FarmerPrefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_FARMER_ID = "farmer_id"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }

    fun saveFarmerSession(farmerId: String) {
        val editor = sharedPreferences.edit()
        editor.putString(KEY_FARMER_ID, farmerId)
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.apply()
    }

    fun getFarmerId(): String? {
        return sharedPreferences.getString(KEY_FARMER_ID, null)
    }

    fun isLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun clearSession() {
        val editor = sharedPreferences.edit()
        editor.clear()
        editor.apply()
    }
}