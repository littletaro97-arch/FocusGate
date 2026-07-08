package com.example.focusgate

object TimePolicy {
    const val INTERCEPT_DEBOUNCE_MS = 1_200L

    fun isWithinEntertainmentWindow(state: GuardState, packageName: String, now: Long): Boolean =
        state.entertainmentPackage == packageName && now < state.entertainmentUntil

    fun isWithinSearchGrace(state: GuardState, packageName: String, now: Long): Boolean =
        state.searchSessionPackage == packageName && now < state.searchGraceUntil

    fun isDebounced(state: GuardState, packageName: String, now: Long): Boolean =
        state.lastTargetPackage == packageName && now - state.lastInterceptTime < INTERCEPT_DEBOUNCE_MS
}
