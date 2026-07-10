package com.example.focusgate

enum class TargetPlatform(
    val packageName: String,
    val displayName: String
) {
    XHS("com.xingin.xhs", "小红书"),
    BILI("tv.danmaku.bili", "B站"),
    DOUYIN("com.ss.android.ugc.aweme", "抖音");

    companion object {
        fun fromPackage(packageName: String?): TargetPlatform =
            fromPackageOrNull(packageName) ?: XHS

        fun fromPackageOrNull(packageName: String?): TargetPlatform? =
            entries.firstOrNull { it.packageName == packageName }
    }
}
