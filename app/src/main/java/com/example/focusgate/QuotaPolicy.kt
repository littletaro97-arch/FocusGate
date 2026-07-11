package com.example.focusgate

data class DeveloperQuotaLimits(
    val maxDailyEntertainmentCount: Int,
    val maxEntertainmentDurationMinutes: Int
)

data class QuotaSelection(
    val entertainmentCount: Int,
    val entertainmentDurationMinutes: Int
) {
    val entertainmentDurationMillis: Long
        get() = entertainmentDurationMinutes * 60_000L
}

object QuotaPolicy {
    const val MIN_ENTERTAINMENT_COUNT = 1
    const val MIN_ENTERTAINMENT_DURATION_MINUTES = 1
    const val DEFAULT_MAX_DAILY_ENTERTAINMENT_COUNT = 10
    const val DEFAULT_MAX_ENTERTAINMENT_DURATION_MINUTES = 15
    const val DEVELOPER_MAX_DAILY_ENTERTAINMENT_COUNT = 100
    const val DEVELOPER_MAX_ENTERTAINMENT_DURATION_MINUTES = 180

    val defaultDeveloperLimits = DeveloperQuotaLimits(
        maxDailyEntertainmentCount = DEFAULT_MAX_DAILY_ENTERTAINMENT_COUNT,
        maxEntertainmentDurationMinutes = DEFAULT_MAX_ENTERTAINMENT_DURATION_MINUTES
    )

    fun sanitizeDeveloperLimits(count: Int, durationMinutes: Int): DeveloperQuotaLimits =
        DeveloperQuotaLimits(
            maxDailyEntertainmentCount = count.coerceIn(
                MIN_ENTERTAINMENT_COUNT,
                DEVELOPER_MAX_DAILY_ENTERTAINMENT_COUNT
            ),
            maxEntertainmentDurationMinutes = durationMinutes.coerceIn(
                MIN_ENTERTAINMENT_DURATION_MINUTES,
                DEVELOPER_MAX_ENTERTAINMENT_DURATION_MINUTES
            )
        )

    fun sanitizeUserSelection(
        count: Int,
        durationMinutes: Int,
        limits: DeveloperQuotaLimits
    ): QuotaSelection {
        val cleanLimits = sanitizeDeveloperLimits(
            limits.maxDailyEntertainmentCount,
            limits.maxEntertainmentDurationMinutes
        )
        return QuotaSelection(
            entertainmentCount = count.coerceIn(
                MIN_ENTERTAINMENT_COUNT,
                cleanLimits.maxDailyEntertainmentCount
            ),
            entertainmentDurationMinutes = durationMinutes.coerceIn(
                MIN_ENTERTAINMENT_DURATION_MINUTES,
                cleanLimits.maxEntertainmentDurationMinutes
            )
        )
    }

    fun sanitizeDeveloperTodaySelection(count: Int, durationMinutes: Int): QuotaSelection =
        QuotaSelection(
            entertainmentCount = count.coerceIn(
                MIN_ENTERTAINMENT_COUNT,
                DEVELOPER_MAX_DAILY_ENTERTAINMENT_COUNT
            ),
            entertainmentDurationMinutes = durationMinutes.coerceIn(
                MIN_ENTERTAINMENT_DURATION_MINUTES,
                DEVELOPER_MAX_ENTERTAINMENT_DURATION_MINUTES
            )
        )

    fun canConfirmTodayQuota(alreadyConfirmedToday: Boolean): Boolean = !alreadyConfirmedToday
}
