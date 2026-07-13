package com.example.focusgate

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun DeveloperCenterScreen(
    repository: GuardRepository,
    accessibilityEnabled: Boolean,
    overlayEnabled: Boolean,
    notificationEnabled: Boolean,
    expandedSections: Set<String>,
    onSectionExpandedChange: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    onExit: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenNotification: () -> Unit
) {
    var refreshVersion by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var pendingReset by remember { mutableStateOf<DeveloperResetAction?>(null) }
    val now = System.currentTimeMillis()
    val today = LocalDate.now().toString()
    val snapshot = refreshVersion.let {
        DeveloperCenterPolicy.snapshot(
            state = repository.state(),
            configs = repository.targetAppConfigs(),
            limits = repository.developerQuotaLimits(),
            developerModeEnabled = repository.isDeveloperModeEnabled(),
            globalGuardEnabled = repository.isGlobalGuardEnabled(),
            today = today,
            nowMillis = now
        )
    }

    LaunchedEffect(Unit) {
        Log.i(SearchGateDeveloperCenterLog.TAG, "page opened")
    }

    fun refresh(reason: String) {
        refreshVersion += 1
        Log.i(SearchGateDeveloperCenterLog.TAG, "status refresh reason=$reason")
    }

    fun runOperation(name: String, operation: () -> Unit) {
        runCatching(operation).onSuccess {
            message = "$name 已生效"
            Log.i(SearchGateDeveloperCenterLog.TAG, "operation success name=$name")
            refresh(name)
        }.onFailure {
            message = "$name 失败：${it.javaClass.simpleName}"
            Log.e(SearchGateDeveloperCenterLog.TAG, "operation failed name=$name error=${it.javaClass.simpleName}")
            refresh("rollback_$name")
        }
    }

    pendingReset?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingReset = null },
            title = { Text(action.title) },
            text = { Text(action.impact) },
            confirmButton = {
                Button(onClick = {
                    pendingReset = null
                    runOperation(action.title) { action.execute(repository, snapshot) }
                }) { Text("确认执行") }
            },
            dismissButton = { TextButton(onClick = { pendingReset = null }) { Text("取消") } }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("开发者", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("返回") }
        }
        Text("状态", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        DeveloperStatusCard("status_basic", "基础状态", expandedSections, onSectionExpandedChange) {
            StatusLine("开发者模式", if (snapshot.developerModeEnabled) "已开启" else "已关闭")
            StatusLine("总限制", if (snapshot.globalGuardEnabled) "启用" else "暂停")
            StatusLine("前台服务", if (notificationEnabled) "可运行" else "通知权限异常")
            StatusLine("无障碍服务", enabledText(accessibilityEnabled))
            StatusLine("悬浮窗权限", enabledText(overlayEnabled))
            StatusLine("通知权限", enabledText(notificationEnabled), warning = !notificationEnabled)
            StatusLine("日期", today)
            StatusLine("版本", "${BuildConfig.VERSION_NAME} / ${if (BuildConfig.DEBUG) "Debug" else "Release"}")
        }
        DeveloperStatusCard("status_quota", "今日额度", expandedSections, onSectionExpandedChange) {
            StatusLine("确认状态", if (snapshot.todayQuotaConfirmed) "已确认" else "未确认")
            StatusLine("已使用", "${snapshot.usedCount} / ${snapshot.dailyLimit} 次")
            StatusLine("剩余次数", "${snapshot.remainingCount} 次")
            StatusLine("单次时间", "${snapshot.durationMinutes} 分钟")
            StatusLine("娱乐状态", if (snapshot.entertainmentActive) "进行中" else "未进行")
            StatusLine("开始时间戳", snapshot.entertainmentStartedAt.takeIf { it > 0L }?.toString() ?: "-")
            StatusLine("结束时间戳", snapshot.entertainmentUntil.takeIf { it > 0L }?.toString() ?: "-")
        }
        DeveloperStatusCard("status_recognition", "识别与应用", expandedSections, onSectionExpandedChange) {
            StatusLine("当前前台应用", snapshot.currentAppName ?: "未记录")
            StatusLine("当前包名", snapshot.lastTargetPackage ?: "-")
            StatusLine("目标应用类型", snapshot.currentAppType ?: "-")
            StatusLine("是否限制应用", if (snapshot.currentRestricted) "是" else "否")
            StatusLine("页面识别", snapshot.pageRecognition)
            StatusLine("状态机", snapshot.promptState)
            StatusLine("弹窗状态", if (snapshot.promptShowing) "显示：${snapshot.promptPackage}" else "未显示")
            StatusLine("资料查询", snapshot.searchState)
            StatusLine("防抖状态", snapshot.debounceState)
            StatusLine("限制应用", "${snapshot.targetCount} 个")
            StatusLine("内置 / 用户", "${snapshot.builtInCount} / ${snapshot.userAddedCount}")
            StatusLine("今日新增 / 可取消", "${snapshot.todayAddedCount} / ${snapshot.removableTodayCount}")
            StatusLine("纯限制 / 资料+娱乐", "${snapshot.entertainmentOnlyCount} / ${snapshot.hybridCount}")
            StatusLine("最近事件", when {
                snapshot.entertainmentActive -> "娱乐进行中"
                snapshot.lastTargetPackage != null -> "识别 ${snapshot.lastTargetPackage}"
                else -> "暂无关键事件"
            })
        }

        HorizontalDivider()
        Text("功能调整", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        DeveloperActionCard("actions_switches", "开关", expandedSections, onSectionExpandedChange) {
            DeveloperSwitchRow(
                title = "总限制开关",
                subtitle = "立即控制限制逻辑",
                checked = snapshot.globalGuardEnabled,
                onCheckedChange = { enabled ->
                    runOperation("总限制${if (enabled) "开启" else "关闭"}") {
                        repository.setGlobalGuardEnabled(enabled)
                        check(repository.isGlobalGuardEnabled() == enabled)
                    }
                }
            )
        }
        DeveloperActionCard("actions_quota", "额度", expandedSections, onSectionExpandedChange) {
            ImmediateNumberRow("每日娱乐次数上限", snapshot.limits.maxDailyEntertainmentCount, "次", QuotaPolicy.MIN_ENTERTAINMENT_COUNT, QuotaPolicy.DEVELOPER_MAX_DAILY_ENTERTAINMENT_COUNT) { value ->
                runOperation("每日娱乐次数上限") { repository.updateDeveloperQuotaLimits(value, snapshot.limits.maxEntertainmentDurationMinutes) }
            }
            ImmediateNumberRow("单次娱乐时间上限", snapshot.limits.maxEntertainmentDurationMinutes, "分钟", QuotaPolicy.MIN_ENTERTAINMENT_DURATION_MINUTES, QuotaPolicy.DEVELOPER_MAX_ENTERTAINMENT_DURATION_MINUTES) { value ->
                runOperation("单次娱乐时间上限") { repository.updateDeveloperQuotaLimits(snapshot.limits.maxDailyEntertainmentCount, value) }
            }
            ImmediateNumberRow("今日娱乐次数上限", snapshot.dailyLimit, "次", 1, QuotaPolicy.DEVELOPER_MAX_DAILY_ENTERTAINMENT_COUNT) { value ->
                runOperation("今日娱乐次数上限") { repository.debugUpdateTodayQuota(value, snapshot.usedCount, snapshot.durationMinutes, false) }
            }
            ImmediateNumberRow("今日已使用次数", snapshot.usedCount, "次", 0, snapshot.dailyLimit.coerceAtLeast(1)) { value ->
                runOperation("今日已使用次数") { repository.debugUpdateTodayQuota(snapshot.dailyLimit, value, snapshot.durationMinutes, false) }
            }
            ImmediateNumberRow("今日单次娱乐时间", snapshot.durationMinutes, "分钟", 1, QuotaPolicy.DEVELOPER_MAX_ENTERTAINMENT_DURATION_MINUTES) { value ->
                runOperation("今日单次娱乐时间") { repository.debugUpdateTodayQuota(snapshot.dailyLimit, snapshot.usedCount, value, false) }
            }
        }
        DeveloperActionCard("actions_reset", "状态重置", expandedSections, onSectionExpandedChange) {
            DeveloperResetAction.entries.forEach { action ->
                OutlinedButton(onClick = { pendingReset = action }, modifier = Modifier.fillMaxWidth()) {
                    Text(action.title)
                }
            }
        }
        DeveloperActionCard("actions_tools", "工具", expandedSections, onSectionExpandedChange) {
            OutlinedButton(onClick = { refresh("manual") }, modifier = Modifier.fillMaxWidth()) { Text("刷新当前状态") }
            OutlinedButton(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) { Text("打开无障碍设置") }
            OutlinedButton(onClick = onOpenOverlay, modifier = Modifier.fillMaxWidth()) { Text("打开悬浮窗设置") }
            OutlinedButton(onClick = onOpenNotification, modifier = Modifier.fillMaxWidth()) { Text("打开通知设置") }
            OutlinedButton(onClick = onOpenAppDetails, modifier = Modifier.fillMaxWidth()) { Text("打开应用信息") }
            Text("完整日志请按 SearchGate 日志 Tag 查看。", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("退出开发者模式") }
        if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

private enum class DeveloperResetAction(val title: String, val impact: String) {
    RESET_USAGE("重置今日使用次数", "今日已使用次数将变为 0。"),
    CLEAR_ENTERTAINMENT("清除当前娱乐状态", "当前娱乐倒计时与放行状态将立即结束。"),
    CLEAR_QUOTA_CONFIRMATION("清除今日额度确认", "普通页面将重新允许确认今日额度。"),
    CLEAR_LOOKUP_PROMPT("清除查询和弹窗状态", "资料查询、当前弹窗和状态机临时状态将被清除。"),
    CLEAR_TARGET_LOCKS("清除目标应用临时锁", "用户新增应用的今日取消锁将被解除，仅用于调试。"),
    CLEAR_USER_TARGETS("清空用户新增应用", "所有用户新增限制应用将被移除，内置应用保留。"),
    RESTORE_LIMITS("恢复额度上限默认值", "开发者上限恢复为 10 次和 15 分钟。");

    fun execute(repository: GuardRepository, snapshot: DeveloperCenterSnapshot) {
        when (this) {
            RESET_USAGE -> repository.debugResetDailyUsage()
            CLEAR_ENTERTAINMENT -> repository.debugClearEntertainmentSession()
            CLEAR_QUOTA_CONFIRMATION -> repository.debugUpdateTodayQuota(snapshot.dailyLimit, snapshot.usedCount, snapshot.durationMinutes, true)
            CLEAR_LOOKUP_PROMPT -> { repository.debugClearStudyLookupState(); repository.clearPromptState() }
            CLEAR_TARGET_LOCKS -> repository.debugClearUserTargetLocks()
            CLEAR_USER_TARGETS -> repository.debugClearUserAddedTargets()
            RESTORE_LIMITS -> repository.restoreDefaultDeveloperQuotaLimits()
        }
    }
}

@Composable
private fun DeveloperStatusCard(
    id: String,
    title: String,
    expandedSections: Set<String>,
    onExpandedChange: (String, Boolean) -> Unit,
    content: @Composable () -> Unit
) = DeveloperCard(id, title, id in expandedSections, onExpandedChange, content)

@Composable
private fun DeveloperActionCard(
    id: String,
    title: String,
    expandedSections: Set<String>,
    onExpandedChange: (String, Boolean) -> Unit,
    content: @Composable () -> Unit
) = DeveloperCard(id, title, id in expandedSections, onExpandedChange, content)

@Composable
private fun DeveloperCard(
    id: String,
    title: String,
    expanded: Boolean,
    onExpandedChange: (String, Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(FocusGateMotion.ARROW_MS),
        label = "developer-arrow-$id"
    )
    OutlinedCard(modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Color(0xFFE1E1D9))) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = {
                    Log.d(SearchGateMotionLog.TAG, "developer section ${if (expanded) "collapse" else "expand"} start id=$id")
                    Log.i(SearchGateCollapsePerfLog.TAG, "module=$id targetExpanded=${!expanded} persistence=false cleanup=false")
                    onExpandedChange(id, !expanded)
                }) {
                    Text(if (expanded) "收起" else "展开")
                    Text("⌄", modifier = Modifier.rotate(arrowRotation))
                }
            }
            SmoothCollapsibleContent(
                visible = expanded,
                contentSpacing = 9.dp
            ) {
                content()
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String, warning: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DeveloperSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ImmediateNumberRow(title: String, value: Int, unit: String, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(enabled = value > min, onClick = { onChange((value - 1).coerceAtLeast(min)) }) { Text("−") }
        Text("$value $unit", modifier = Modifier.padding(horizontal = 4.dp), fontWeight = FontWeight.SemiBold)
        TextButton(enabled = value < max, onClick = { onChange((value + 1).coerceAtMost(max)) }) { Text("+") }
    }
}

private fun enabledText(enabled: Boolean) = if (enabled) "正常" else "异常"
