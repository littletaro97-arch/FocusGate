package com.example.focusgate

import android.content.SharedPreferences

interface DeveloperSettingsStore {
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun getInt(key: String, defaultValue: Int): Int
    fun putBoolean(key: String, value: Boolean)
    fun putInt(key: String, value: Int)
}

class SharedPreferencesDeveloperSettingsStore(
    private val preferences: SharedPreferences
) : DeveloperSettingsStore {
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        preferences.getBoolean(key, defaultValue)

    override fun getInt(key: String, defaultValue: Int): Int =
        preferences.getInt(key, defaultValue)

    override fun putBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }

    override fun putInt(key: String, value: Int) {
        preferences.edit().putInt(key, value).apply()
    }
}

class DeveloperSettingsManager(
    private val store: DeveloperSettingsStore
) {
    fun isDeveloperModeEnabled(): Boolean =
        store.getBoolean(KEY_DEVELOPER_MODE_ENABLED, false)

    fun setDeveloperModeEnabled(enabled: Boolean) {
        store.putBoolean(KEY_DEVELOPER_MODE_ENABLED, enabled)
    }

    fun quotaLimits(): DeveloperQuotaLimits = QuotaPolicy.sanitizeDeveloperLimits(
        count = store.getInt(
            KEY_MAX_DAILY_ENTERTAINMENT_COUNT,
            QuotaPolicy.DEFAULT_MAX_DAILY_ENTERTAINMENT_COUNT
        ),
        durationMinutes = store.getInt(
            KEY_MAX_ENTERTAINMENT_DURATION_MINUTES,
            QuotaPolicy.DEFAULT_MAX_ENTERTAINMENT_DURATION_MINUTES
        )
    )

    fun updateQuotaLimits(count: Int, durationMinutes: Int): DeveloperQuotaLimits {
        val clean = QuotaPolicy.sanitizeDeveloperLimits(count, durationMinutes)
        store.putInt(KEY_MAX_DAILY_ENTERTAINMENT_COUNT, clean.maxDailyEntertainmentCount)
        store.putInt(KEY_MAX_ENTERTAINMENT_DURATION_MINUTES, clean.maxEntertainmentDurationMinutes)
        return clean
    }

    fun restoreDefaultQuotaLimits(): DeveloperQuotaLimits = updateQuotaLimits(
        QuotaPolicy.DEFAULT_MAX_DAILY_ENTERTAINMENT_COUNT,
        QuotaPolicy.DEFAULT_MAX_ENTERTAINMENT_DURATION_MINUTES
    )

    companion object {
        private const val KEY_DEVELOPER_MODE_ENABLED = "developerModeEnabled"
        private const val KEY_MAX_DAILY_ENTERTAINMENT_COUNT = "maxDailyEntertainmentCount"
        private const val KEY_MAX_ENTERTAINMENT_DURATION_MINUTES = "maxEntertainmentDurationMinutes"
    }
}
