package com.example.focusgate

import android.view.accessibility.AccessibilityEvent

class RepeatedScrollGuard(
    private val windowMs: Long,
    private val minIntervalMs: Long
) {
    private val scrollTimes = ArrayDeque<Long>()
    private var lastAcceptedAt = 0L
    private var lastFromIndex = -1
    private var lastScrollY = -1

    fun observe(event: AccessibilityEvent, now: Long): Int {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return 0
        if (now - lastAcceptedAt < minIntervalMs) return scrollTimes.size

        val fromIndex = event.fromIndex
        val scrollY = event.scrollY
        val hasComparablePosition = fromIndex >= 0 || scrollY >= 0
        val movedForward = (fromIndex >= 0 && lastFromIndex >= 0 && fromIndex > lastFromIndex) ||
            (scrollY >= 0 && lastScrollY >= 0 && scrollY > lastScrollY)
        val unknownPagedScroll = !hasComparablePosition || (lastFromIndex < 0 && lastScrollY < 0)

        if (!movedForward && !unknownPagedScroll) {
            lastFromIndex = fromIndex
            lastScrollY = scrollY
            return scrollTimes.size
        }

        lastAcceptedAt = now
        lastFromIndex = fromIndex
        lastScrollY = scrollY
        scrollTimes.addLast(now)
        while (scrollTimes.isNotEmpty() && now - scrollTimes.first() > windowMs) {
            scrollTimes.removeFirst()
        }
        return scrollTimes.size
    }

    fun reset() {
        scrollTimes.clear()
        lastAcceptedAt = 0L
        lastFromIndex = -1
        lastScrollY = -1
    }
}

class BiliRecommendationGuard {
    private var lastPageState = BiliPageState.UNKNOWN
    private var lastVideoSource = BiliPageState.UNKNOWN
    private var lastDetailSignature: String? = null
    private var recommendationJumps = 0
    private var lastPageChangedAt = 0L

    fun markSearchResults() {
        observe(
            pageState = BiliPageState.SEARCH_RESULT,
            signature = null,
            jumpLimit = Int.MAX_VALUE,
            isStudyLookupActive = true,
            isEntertainmentActive = false,
            now = System.currentTimeMillis()
        )
    }

    fun observe(
        pageState: BiliPageState,
        signature: String?,
        jumpLimit: Int,
        isStudyLookupActive: Boolean,
        isEntertainmentActive: Boolean,
        now: Long
    ): BiliRecommendationDecision {
        if (pageState != lastPageState) {
            lastPageChangedAt = now
        }

        if (isEntertainmentActive) {
            lastPageState = pageState
            return decision(pageState, jumpLimit, "entertainment allowed")
        }

        if (isStudyLookupActive || pageState == BiliPageState.SEARCHING || pageState == BiliPageState.SEARCH_RESULT) {
            lastVideoSource = BiliPageState.SEARCH_RESULT
            lastPageState = pageState
            return decision(pageState, jumpLimit, "study/search flow")
        }

        if (
            pageState == BiliPageState.COLLECTION_HISTORY ||
            pageState == BiliPageState.USER_HOME ||
            pageState == BiliPageState.WEBVIEW
        ) {
            lastVideoSource = pageState
            lastPageState = pageState
            return decision(pageState, jumpLimit, "whitelisted page")
        }

        if (pageState == BiliPageState.HOME_RECOMMEND) {
            lastVideoSource = BiliPageState.HOME_RECOMMEND
            lastPageState = pageState
            return decision(pageState, jumpLimit, "home recommend source")
        }

        if (pageState != BiliPageState.VIDEO_DETAIL) {
            lastPageState = pageState
            return decision(pageState, jumpLimit, "non video detail change")
        }

        if (signature.isNullOrBlank()) {
            lastPageState = pageState
            return decision(pageState, jumpLimit, "blank video signature")
        }

        val previous = lastDetailSignature
        if (previous == null || previous == signature) {
            lastDetailSignature = signature
            lastPageState = pageState
            return decision(pageState, jumpLimit, "same or first video detail")
        }

        if (lastVideoSource == BiliPageState.SEARCH_RESULT) {
            lastDetailSignature = signature
            lastPageState = pageState
            lastVideoSource = BiliPageState.VIDEO_DETAIL
            return decision(pageState, jumpLimit, "video from search result")
        }

        if (lastVideoSource == BiliPageState.HOME_RECOMMEND || lastPageState == BiliPageState.VIDEO_DETAIL) {
            recommendationJumps += 1
        }
        lastDetailSignature = signature
        lastPageState = pageState
        lastVideoSource = BiliPageState.VIDEO_DETAIL
        return decision(pageState, jumpLimit, "recommend video jump")
    }

    fun lastPageChangedAt(): Long = lastPageChangedAt

    fun reset() {
        lastPageState = BiliPageState.UNKNOWN
        lastVideoSource = BiliPageState.UNKNOWN
        lastDetailSignature = null
        recommendationJumps = 0
        lastPageChangedAt = 0L
    }

    private fun decision(pageState: BiliPageState, jumpLimit: Int, reason: String): BiliRecommendationDecision =
        BiliRecommendationDecision(
            pageState = pageState,
            recommendationJumps = recommendationJumps,
            shouldIntercept = recommendationJumps >= jumpLimit,
            reason = reason,
            lastPageChangedAt = lastPageChangedAt
        )
}
