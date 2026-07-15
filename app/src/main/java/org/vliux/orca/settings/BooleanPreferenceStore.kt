package org.vliux.orca.settings

import android.content.SharedPreferences

internal interface BooleanPreferenceStore {
    fun getBoolean(key: String, defaultValue: Boolean): Boolean

    fun putBoolean(key: String, value: Boolean)
}

internal class SharedPreferencesBooleanPreferenceStore(
    private val preferences: SharedPreferences,
) : BooleanPreferenceStore {
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = preferences.getBoolean(key, defaultValue)

    override fun putBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }
}
