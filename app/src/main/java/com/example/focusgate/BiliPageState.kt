package com.example.focusgate

enum class BiliPageState {
    UNKNOWN,
    HOME_RECOMMEND,
    SEARCHING,
    SEARCH_RESULT,
    VIDEO_DETAIL,
    VIDEO_PLAYING,
    COMMENT_EXPANDED,
    USER_HOME,
    COLLECTION_HISTORY,
    WEBVIEW
}

data class BiliRecommendationDecision(
    val pageState: BiliPageState,
    val recommendationJumps: Int,
    val shouldIntercept: Boolean,
    val reason: String,
    val lastPageChangedAt: Long
)

object SearchGateBiliLog {
    const val TAG = "SearchGateBiliDebug"
}
