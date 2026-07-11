package com.example.focusgate

import android.graphics.drawable.Drawable

enum class TargetAppType {
    HYBRID_STUDY_ENTERTAINMENT,
    ENTERTAINMENT_ONLY
}

enum class TargetAppSource {
    BUILT_IN,
    USER_ADDED
}

data class TargetAppConfig(
    val packageName: String,
    val appName: String,
    val appType: TargetAppType,
    val isEnabled: Boolean,
    val allowStudyLookup: Boolean,
    val allowEntertainment: Boolean,
    val source: TargetAppSource,
    val addedDate: String? = null,
    val canRemoveAfterDate: String? = null
)

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSelected: Boolean,
    val config: TargetAppConfig?
)

data class TargetAppSaveResult(
    val selectedCount: Int,
    val blockedLockedRemovals: List<TargetAppConfig>
)

object TargetAppLockPolicy {
    fun canRemoveUserTarget(config: TargetAppConfig, currentDate: String): Boolean {
        if (config.source != TargetAppSource.USER_ADDED) return false
        val canRemoveAfter = config.canRemoveAfterDate ?: return true
        return currentDate >= canRemoveAfter
    }

    fun applyDailyLock(
        config: TargetAppConfig,
        previous: TargetAppConfig?,
        finalEnabled: Boolean,
        todayDate: String,
        tomorrowDate: String
    ): TargetAppConfig {
        if (config.source != TargetAppSource.USER_ADDED) return config
        return when {
            finalEnabled && (previous == null || !previous.isEnabled) -> config.copy(
                addedDate = todayDate,
                canRemoveAfterDate = tomorrowDate
            )
            previous != null -> config.copy(
                addedDate = previous.addedDate,
                canRemoveAfterDate = previous.canRemoveAfterDate
            )
            else -> config
        }
    }
}
