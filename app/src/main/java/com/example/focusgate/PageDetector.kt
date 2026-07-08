package com.example.focusgate

import android.view.accessibility.AccessibilityNodeInfo

object PageDetector {
    fun detect(packageName: String, root: AccessibilityNodeInfo?, lastKeyword: String?): PageKind {
        if (root == null) return PageKind.UNKNOWN

        val snapshot = NodeSnapshot.from(root)
        val searchScore = searchScore(packageName, snapshot, lastKeyword)
        val detailScore = detailScore(packageName, snapshot)
        val homeScore = homeScore(packageName, snapshot)

        return when {
            searchScore >= 3 && searchScore > homeScore -> PageKind.SEARCH_RESULTS
            detailScore >= 3 && homeScore < 2 -> PageKind.CONTENT_DETAIL
            homeScore >= 2 && searchScore < 3 -> PageKind.HOME
            else -> PageKind.UNKNOWN
        }
    }

    fun pageSignature(root: AccessibilityNodeInfo?): String? {
        if (root == null) return null
        val snapshot = NodeSnapshot.from(root)
        val ignored = setOf(
            "点赞", "投币", "收藏", "分享", "评论", "缓存", "首页", "推荐", "动态", "我的",
            "综合", "视频", "番剧", "用户", "搜索"
        )
        val keyTexts = snapshot.texts
            .asSequence()
            .map { it.trim() }
            .filter { it.length in 3..80 }
            .filter { text -> ignored.none { text == it } }
            .take(5)
            .toList()
        return keyTexts.joinToString("|").takeIf { it.isNotBlank() }
    }

    fun textSummary(root: AccessibilityNodeInfo?, maxItems: Int = 12): String {
        if (root == null) return "-"
        val snapshot = NodeSnapshot.from(root)
        return snapshot.texts
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(maxItems)
            .joinToString(" | ")
            .ifBlank { "-" }
    }

    fun keywordSummary(root: AccessibilityNodeInfo?): String {
        if (root == null) return "-"
        val snapshot = NodeSnapshot.from(root)
        val keywords = listOf(
            "首页", "推荐", "搜索", "综合", "视频", "番剧", "用户", "动态", "我的",
            "简介", "评论", "弹幕", "播放", "暂停", "全屏", "点赞", "投币", "收藏",
            "历史", "稍后再看", "关注", "粉丝", "回复", "分享", "缓存",
            "朋友", "消息", "我", "直播", "商品", "购物车", "团购", "小店",
            "获赞", "作品", "喜欢", "音乐"
        )
        return keywords
            .filter { snapshot.contains(it) }
            .joinToString(",")
            .ifBlank { "-" }
    }

    fun douyinSignalSummary(root: AccessibilityNodeInfo?, lastKeyword: String?): String {
        if (root == null) return "root=null"
        val snapshot = NodeSnapshot.from(root)
        return "searchScore=${searchScore(TargetPlatform.DOUYIN.packageName, snapshot, lastKeyword)} " +
            "homeScore=${douyinHomeScore(snapshot)} " +
            "detailScore=${detailScore(TargetPlatform.DOUYIN.packageName, snapshot)} " +
            "hasEditText=${snapshot.hasEditText} " +
            "class=${snapshot.classNames.take(4).joinToString("|").ifBlank { "-" }}"
    }

    fun detectBiliPage(root: AccessibilityNodeInfo?, lastKeyword: String?): BiliPageState {
        if (root == null) return BiliPageState.UNKNOWN
        val snapshot = NodeSnapshot.from(root)

        if (snapshot.classNames.any { it.contains("WebView", ignoreCase = true) } ||
            snapshot.containsAny("网页", "WebView")
        ) {
            return BiliPageState.WEBVIEW
        }
        if (snapshot.containsAny("收藏", "历史", "稍后再看") &&
            snapshot.containsAny("全部", "最近", "播放")
        ) {
            return BiliPageState.COLLECTION_HISTORY
        }
        if (snapshot.containsAny("评论", "回复") &&
            snapshot.containsAny("发送", "说点什么", "写评论")
        ) {
            return BiliPageState.COMMENT_EXPANDED
        }
        val searchScore = searchScore(TargetPlatform.BILI.packageName, snapshot, lastKeyword)
        if (snapshot.hasEditText && snapshot.contains("搜索")) {
            return BiliPageState.SEARCHING
        }
        if (searchScore >= 3 && snapshot.containsAny("综合", "视频", "番剧", "用户")) {
            return BiliPageState.SEARCH_RESULT
        }
        val detailScore = detailScore(TargetPlatform.BILI.packageName, snapshot)
        if (detailScore >= 3) {
            return BiliPageState.VIDEO_DETAIL
        }
        if (snapshot.containsAny("全屏", "暂停", "播放", "倍速", "弹幕") &&
            !snapshot.containsAny("综合", "番剧", "用户")
        ) {
            return BiliPageState.VIDEO_PLAYING
        }
        if (snapshot.containsAny("关注", "粉丝") &&
            snapshot.containsAny("动态", "投稿", "主页")
        ) {
            return BiliPageState.USER_HOME
        }
        if (snapshot.contains("首页") && snapshot.contains("推荐")) {
            return BiliPageState.HOME_RECOMMEND
        }
        return BiliPageState.UNKNOWN
    }

    fun detectDouyinPage(root: AccessibilityNodeInfo?, lastKeyword: String?): DouyinPageState {
        if (root == null) return DouyinPageState.DOUYIN_UNKNOWN
        val snapshot = NodeSnapshot.from(root)
        val searchScore = searchScore(TargetPlatform.DOUYIN.packageName, snapshot, lastKeyword)
        val homeScore = douyinHomeScore(snapshot)

        if (snapshot.classNames.any { it.contains("WebView", ignoreCase = true) } ||
            snapshot.containsAny("网页", "WebView")
        ) {
            return DouyinPageState.DOUYIN_WEBVIEW
        }
        if (snapshot.containsAny("直播", "商品", "购物车", "团购", "小店")) {
            return DouyinPageState.DOUYIN_SHOP_OR_LIVE
        }
        if (snapshot.containsAny("评论", "回复") &&
            snapshot.containsAny("发送", "说点什么", "写评论")
        ) {
            return DouyinPageState.DOUYIN_COMMENT_OPEN
        }
        if (snapshot.hasEditText && snapshot.contains("搜索")) {
            return DouyinPageState.DOUYIN_SEARCH_PAGE
        }
        if (searchScore >= 3 && snapshot.containsAny("综合", "视频", "用户", "音乐", "直播")) {
            return DouyinPageState.DOUYIN_SEARCH_RESULT
        }
        if (snapshot.containsAny("关注", "粉丝", "获赞") &&
            snapshot.containsAny("作品", "喜欢", "动态")
        ) {
            return DouyinPageState.DOUYIN_USER_PROFILE
        }
        if (homeScore >= 2 && searchScore < 3 && !snapshot.hasEditText) {
            return DouyinPageState.DOUYIN_HOME_RECOMMEND
        }
        if (detailScore(TargetPlatform.DOUYIN.packageName, snapshot) >= 3) {
            return DouyinPageState.DOUYIN_VIDEO_DETAIL
        }
        return DouyinPageState.DOUYIN_UNKNOWN
    }

    private fun douyinHomeScore(snapshot: NodeSnapshot): Int {
        var score = 0
        if (snapshot.contains("首页")) score += 2
        if (snapshot.contains("推荐")) score += 2
        if (snapshot.contains("朋友")) score += 1
        if (snapshot.contains("消息")) score += 1
        if (snapshot.contains("商城")) score += 1
        if (snapshot.contains("精选")) score += 1
        if (snapshot.contains("关注") && snapshot.containsAny("推荐", "首页", "朋友")) score += 1
        if (snapshot.contains("我") && snapshot.containsAny("首页", "朋友", "消息")) score += 1
        return score
    }

    private fun searchScore(packageName: String, snapshot: NodeSnapshot, lastKeyword: String?): Int {
        var score = 0
        if (snapshot.hasEditText) score += 1
        if (!lastKeyword.isNullOrBlank() && snapshot.contains(lastKeyword)) score += 2
        if (snapshot.contains("搜索")) score += 1

        val tabs = when (packageName) {
            TargetPlatform.BILI.packageName -> listOf("综合", "视频", "番剧", "用户")
            TargetPlatform.DOUYIN.packageName -> listOf("综合", "视频", "用户", "音乐", "直播")
            else -> listOf("综合", "笔记", "用户")
        }
        score += tabs.count { snapshot.contains(it) }
        return score
    }

    private fun detailScore(packageName: String, snapshot: NodeSnapshot): Int {
        var score = 0
        if (packageName == TargetPlatform.XHS.packageName) {
            if (snapshot.containsAny("说点什么", "写评论", "评论")) score += 1
            if (snapshot.containsAny("点赞", "赞")) score += 1
            if (snapshot.contains("收藏")) score += 1
            if (snapshot.containsAny("分享", "转发")) score += 1
            if (snapshot.contains("关注")) score += 1
        } else if (packageName == TargetPlatform.BILI.packageName) {
            if (snapshot.containsAny("简介", "评论")) score += 1
            if (snapshot.contains("点赞")) score += 1
            if (snapshot.contains("投币")) score += 1
            if (snapshot.contains("收藏")) score += 1
            if (snapshot.containsAny("分享", "缓存")) score += 1
        } else if (packageName == TargetPlatform.DOUYIN.packageName) {
            if (snapshot.containsAny("评论", "写评论")) score += 1
            if (snapshot.containsAny("点赞", "喜欢")) score += 1
            if (snapshot.containsAny("收藏", "分享")) score += 1
            if (snapshot.containsAny("关注", "作者")) score += 1
        }
        return score
    }

    private fun homeScore(packageName: String, snapshot: NodeSnapshot): Int {
        var score = 0
        if (snapshot.contains("首页")) score += 1
        when (packageName) {
            TargetPlatform.BILI.packageName -> {
                if (snapshot.contains("推荐")) score += 1
                if (snapshot.contains("动态")) score += 1
                if (snapshot.contains("我的")) score += 1
            }
            TargetPlatform.DOUYIN.packageName -> {
                if (snapshot.contains("推荐")) score += 1
                if (snapshot.contains("朋友")) score += 1
                if (snapshot.contains("消息")) score += 1
                if (snapshot.contains("我")) score += 1
            }
            else -> {
                if (snapshot.contains("推荐")) score += 1
                if (snapshot.contains("发现")) score += 1
                if (snapshot.contains("关注")) score += 1
            }
        }
        return score
    }

    private data class NodeSnapshot(
        val texts: List<String>,
        val classNames: List<String>,
        val hasEditText: Boolean
    ) {
        fun contains(value: String): Boolean = texts.any { it.contains(value, ignoreCase = true) }
        fun containsAny(vararg values: String): Boolean = values.any(::contains)

        companion object {
            fun from(root: AccessibilityNodeInfo): NodeSnapshot {
                val texts = mutableListOf<String>()
                val classNames = mutableListOf<String>()
                var hasEditText = false

                fun visit(node: AccessibilityNodeInfo?, depth: Int) {
                    if (node == null || depth > 12 || texts.size > 80) return
                    val className = node.className?.toString().orEmpty()
                    if (className.isNotBlank()) {
                        classNames.add(className)
                    }
                    if (className.contains("EditText", ignoreCase = true)) {
                        hasEditText = true
                    }
                    node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(texts::add)
                    node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(texts::add)
                    for (i in 0 until node.childCount) {
                        visit(node.getChild(i), depth + 1)
                    }
                }

                visit(root, 0)
                return NodeSnapshot(texts = texts, classNames = classNames, hasEditText = hasEditText)
            }
        }
    }
}
