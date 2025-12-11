package com.example.flare_capstone.data.database

import android.content.Context
import android.content.SharedPreferences

object UserSession {

    private const val PREF_NAME = "flare_session"

    private const val KEY_IS_LOGGED_IN = "isLoggedIn"
    private const val KEY_ROLE = "role"              // "user" | "investigator" | "firefighter"
    private const val KEY_USER_ID = "userId"
    private const val KEY_INVESTIGATOR_ID = "investigatorId"
    private const val KEY_UNIT_ID = "unitId"
    private const val KEY_EMAIL = "email"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    fun clear() {
        if (!::prefs.isInitialized) return
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean {
        if (!::prefs.isInitialized) return false
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun getRole(): String? {
        if (!::prefs.isInitialized) return null
        return prefs.getString(KEY_ROLE, null)
    }

    fun getInvestigatorId(): String? =
        if (!::prefs.isInitialized) null else prefs.getString(KEY_INVESTIGATOR_ID, null)

    fun getUnitId(): String? =
        if (!::prefs.isInitialized) null else prefs.getString(KEY_UNIT_ID, null)

    /* ---------- Save session for each role ---------- */

    fun loginUser(userId: String, email: String?) {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_ROLE, "user")
            .putString(KEY_USER_ID, userId)
            .putString(KEY_EMAIL, email)
            .apply()
    }

    fun loginInvestigator(investigatorId: String, email: String?) {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_ROLE, "investigator")
            .putString(KEY_INVESTIGATOR_ID, investigatorId)   // same key your other code reads
            .putString(KEY_EMAIL, email)
            .apply()
    }

    fun loginFirefighter(unitId: String, email: String?) {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_ROLE, "firefighter")
            .putString(KEY_UNIT_ID, unitId)                   // same key as before
            .putString(KEY_EMAIL, email)
            .apply()
    }
}
