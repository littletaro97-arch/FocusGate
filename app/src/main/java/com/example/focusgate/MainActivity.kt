package com.example.focusgate

import android.Manifest
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.NumberPicker
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class MainActivity : ComponentActivity() {
    private lateinit var repository: GuardRepository
    private var permissionRefreshVersion by mutableIntStateOf(0)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        permissionRefreshVersion += 1
        maybeStartStabilityService()
        PermissionAlertCoordinator.evaluate(
            this,
            "notification_permission_result",
            includeForegroundService = false
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = GuardRepository(this)
        setContent {
            AppSurface {
                MainScreen(
                    repository = repository,
                    accessibilityEnabled = permissionRefreshVersion.let { PermissionUtils.isAccessibilityEnabled(this) },
                    overlayEnabled = PermissionUtils.canDrawOverlays(this),
                    notificationEnabled = PermissionUtils.canPostNotifications(this),
                    batteryIgnored = PermissionUtils.isIgnoringBatteryOptimizations(this),
                    onOpenAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onOpenOverlay = { startActivity(PermissionUtils.overlaySettingsIntent(this)) },
                    onOpenNotification = { requestNotificationPermission() },
                    onOpenBattery = { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
                    onOpenAppDetails = { startActivity(PermissionUtils.appDetailsIntent(this)) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionRefreshVersion += 1
        maybeStartStabilityService()
        lifecycleScope.launch {
            delay(PERMISSION_SERVICE_SETTLE_MS)
            PermissionAlertCoordinator.evaluate(this@MainActivity, "activity_resumed")
            permissionRefreshVersion += 1
        }
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionUtils.hasPostNotificationsRuntimePermission(this)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startActivity(PermissionUtils.notificationSettingsIntent(this))
        }
    }

    private fun maybeStartStabilityService() {
        if (PermissionUtils.isAccessibilityEnabled(this) && PermissionUtils.canPostNotifications(this)) {
            StabilityForegroundService.startIfAllowed(this)
        } else {
            Log.i(FocusGateLog.TAG, "stability service not started accessibility=${PermissionUtils.isAccessibilityEnabled(this)} notifications=${PermissionUtils.canPostNotifications(this)}")
        }
    }

    companion object {
        private const val PERMISSION_SERVICE_SETTLE_MS = 500L
    }
}

@Composable
fun AppSurface(content: @Composable () -> Unit) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = androidx.compose.ui.graphics.Color(0xFFF7F7F2),
            content = content
        )
    }
}

@Composable
private fun CollapsibleSection(
    repository: GuardRepository,
    moduleKey: String,
    title: String,
    summary: String,
    defaultExpanded: Boolean,
    forceExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by remember(moduleKey) {
        mutableStateOf(repository.isModuleExpanded(moduleKey, defaultExpanded))
    }
    val shownExpanded = forceExpanded || expanded
    val borderColor = if (forceExpanded) MaterialTheme.colorScheme.error else Color(0xFFD8D8CF)

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (summary.isNotBlank()) {
                        Text(summary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton(
                    onClick = {
                        if (!forceExpanded) {
                            expanded = !expanded
                            repository.setModuleExpanded(moduleKey, expanded)
                        }
                    },
                    enabled = !forceExpanded
                ) {
                    Text(if (shownExpanded) "收起" else "展开")
                }
            }
            if (shownExpanded) {
                content()
            }
        }
    }
}

@Composable
private fun MainScreen(
    repository: GuardRepository,
    accessibilityEnabled: Boolean,
    overlayEnabled: Boolean,
    notificationEnabled: Boolean,
    batteryIgnored: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenNotification: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenAppDetails: () -> Unit
) {
    var selectedInstalledApps by remember {
        mutableStateOf(
            repository.targetAppConfigs()
                .filter { it.isEnabled }
                .associate { it.packageName to it.appName }
        )
    }
    var savedMessage by remember { mutableStateOf("") }
    var showTargetPicker by rememberSaveable { mutableStateOf(false) }
    var targetPickerRequestedAt by remember { mutableLongStateOf(0L) }
    var developerModeEnabled by remember { mutableStateOf(repository.isDeveloperModeEnabled()) }
    var developerQuotaLimits by remember { mutableStateOf(repository.developerQuotaLimits()) }
    var showDeveloperPasswordDialog by remember { mutableStateOf(false) }
    var developerTapCount by remember { mutableStateOf(0) }
    var firstDeveloperTapAt by remember { mutableStateOf(0L) }
    val state = repository.state()
    val hasTodayQuota = repository.hasTodayEntertainmentPlan()
    val initialQuotaSelection = QuotaPolicy.sanitizeUserSelection(
        count = if (hasTodayQuota) state.entertainmentDailyLimit else state.defaultEntertainmentDailyLimit,
        durationMinutes = if (hasTodayQuota) {
            (state.entertainmentDurationMs / 60_000L).toInt()
        } else {
            (state.defaultEntertainmentDurationMs / 60_000L).toInt()
        },
        limits = developerQuotaLimits
    )
    var entertainmentDailyLimit by rememberSaveable {
        mutableIntStateOf(initialQuotaSelection.entertainmentCount)
    }
    var entertainmentDurationMinutes by rememberSaveable {
        mutableIntStateOf(initialQuotaSelection.entertainmentDurationMinutes)
    }
    var entertainmentMessage by remember { mutableStateOf("") }
    val hasTodayControlPlan = repository.hasTodayControlPlan()
    val criticalPermissionsOk = accessibilityEnabled && overlayEnabled && notificationEnabled
    val entertainmentActive = state.entertainmentPackage != null && System.currentTimeMillis() < state.entertainmentUntil
    val permissionForceExpanded = !criticalPermissionsOk || !hasTodayQuota
    val selectedCount = selectedInstalledApps.size

    LaunchedEffect(Unit) {
        Log.i(
            SearchGateQuotaPickerLog.TAG,
            "quota draft initialized count=$entertainmentDailyLimit durationMinutes=$entertainmentDurationMinutes maxCount=${developerQuotaLimits.maxDailyEntertainmentCount} maxDurationMinutes=${developerQuotaLimits.maxEntertainmentDurationMinutes} historicalCountClamped=${initialQuotaSelection.entertainmentCount != state.defaultEntertainmentDailyLimit} historicalDurationClamped=${initialQuotaSelection.entertainmentDurationMillis != state.defaultEntertainmentDurationMs}"
        )
    }
    LaunchedEffect(developerModeEnabled) {
        Log.i(
            SearchGateDevModeLog.TAG,
            "developer mode badge visible=$developerModeEnabled screen=main"
        )
    }

    if (showDeveloperPasswordDialog) {
        DeveloperPasswordDialog(
            onDismiss = { showDeveloperPasswordDialog = false },
            onVerified = {
                repository.setDeveloperModeEnabled(true)
                developerModeEnabled = true
                showDeveloperPasswordDialog = false
            }
        )
    }

    if (showTargetPicker) {
        TargetAppPickerScreen(
            repository = repository,
            requestedAtElapsedRealtime = targetPickerRequestedAt,
            developerModeEnabled = developerModeEnabled,
            onBack = {
                selectedInstalledApps = repository.targetAppConfigs()
                    .filter { it.isEnabled }
                    .associate { it.packageName to it.appName }
                showTargetPicker = false
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "FocusGate",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    val now = System.currentTimeMillis()
                    if (now - firstDeveloperTapAt > 2_000L) {
                        firstDeveloperTapAt = now
                        developerTapCount = 1
                    } else {
                        developerTapCount += 1
                    }
                    if (developerTapCount >= 5) {
                        Log.i(SearchGateDevModeLog.TAG, "developer entry triggered")
                        developerTapCount = 0
                        firstDeveloperTapAt = 0L
                        showDeveloperPasswordDialog = true
                    }
                }
            )
            if (developerModeEnabled) {
                DeveloperModeBadge()
            }
        }

        HorizontalDivider()

        CollapsibleSection(
            repository = repository,
            moduleKey = "permissions",
            title = "权限状态",
            summary = if (criticalPermissionsOk) "关键权限正常" else "关键权限异常，限制能力会失效",
            defaultExpanded = !hasTodayQuota,
            forceExpanded = permissionForceExpanded
        ) {
        if (!notificationEnabled) {
            Text(
                "通知权限已关闭：系统会阻止普通权限提醒。此处红色状态与重新打开应用后的提示是主要降级路径；已有前台服务通知只会尽力更新，不能保证显示。",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (!hasTodayQuota) {
            Text("今日额度未确认", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("确认前请完成关键权限。", style = MaterialTheme.typography.bodySmall)
            PermissionRow("无障碍服务", accessibilityEnabled, onOpenAccessibility)
            PermissionRow("悬浮窗权限", overlayEnabled, onOpenOverlay)
            PermissionRow("通知权限", notificationEnabled, onOpenNotification)
            PermissionRow("电池优化白名单", batteryIgnored, onOpenBattery)
            PermissionRow("后台运行 / 自启动", false, onOpenAppDetails, "按机型在系统页面手动设置")
        } else if (!criticalPermissionsOk) {
            Text("关键权限异常", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
            Text("关键权限已关闭，检索门无法继续执行限制。请重新开启权限后继续使用。")
            if (!accessibilityEnabled) PermissionRow("无障碍服务", false, onOpenAccessibility)
            if (!overlayEnabled) PermissionRow("悬浮窗权限", false, onOpenOverlay)
            if (!notificationEnabled) PermissionRow("通知权限", false, onOpenNotification)
        } else {
            Text("今日额度已确认", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (batteryIgnored) "关键权限正常，电池优化白名单已开启。" else "关键权限正常。电池优化白名单未开启，后台稳定性可能下降。")
        }
        }

        HorizontalDivider()

        CollapsibleSection(
            repository = repository,
            moduleKey = "targets",
            title = "目标应用",
            summary = "当前限制应用数：$selectedCount",
            defaultExpanded = true
        ) {
        Text("当前限制应用数：$selectedCount")
        Button(
            onClick = {
                targetPickerRequestedAt = SystemClock.elapsedRealtime()
                Log.i(SearchGateAppPickerLog.TAG, "picker entry clicked atElapsedRealtime=$targetPickerRequestedAt")
                showTargetPicker = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("选择限制应用")
        }
        if (savedMessage.isNotEmpty()) {
            Text(savedMessage)
        }
        }

        HorizontalDivider()

        CollapsibleSection(
            repository = repository,
            moduleKey = "quota",
            title = "今日额度",
            summary = if (hasTodayQuota) {
                "已锁定：${state.entertainmentDailyLimit} 次，每次 ${state.entertainmentDurationMs / 60_000L} 分钟"
            } else {
                "未确认"
            },
            defaultExpanded = true,
            forceExpanded = !hasTodayQuota
        ) {
        if (!hasTodayQuota) {
            QuotaSetupCard(title = "每日娱乐次数") {
                QuotaWheelPicker(
                value = entertainmentDailyLimit,
                unit = "次",
                min = QuotaPolicy.MIN_ENTERTAINMENT_COUNT,
                max = developerQuotaLimits.maxDailyEntertainmentCount,
                onValueChange = {
                    entertainmentDailyLimit = it
                    entertainmentMessage = ""
                }
            )
            }
            QuotaSetupCard(title = "单次娱乐时间") {
                QuotaWheelPicker(
                value = entertainmentDurationMinutes,
                unit = "分钟",
                min = QuotaPolicy.MIN_ENTERTAINMENT_DURATION_MINUTES,
                max = developerQuotaLimits.maxEntertainmentDurationMinutes,
                onValueChange = {
                    entertainmentDurationMinutes = it
                    entertainmentMessage = ""
                }
            )
            }
            QuotaSetupCard(title = "限制应用") {
                Text("当前限制应用数：$selectedCount")
                OutlinedButton(
                    onClick = {
                        targetPickerRequestedAt = SystemClock.elapsedRealtime()
                        showTargetPicker = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("添加更多应用")
                }
            }
            QuotaSetupCard(title = "确认") {
                Text("确认后今日不可修改", style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = {
                        entertainmentMessage = if (repository.confirmTodayEntertainmentPlan(entertainmentDailyLimit, entertainmentDurationMinutes)) {
                            val refreshed = repository.state()
                            entertainmentDailyLimit = refreshed.entertainmentDailyLimit
                            entertainmentDurationMinutes = (refreshed.entertainmentDurationMs / 60_000L).toInt()
                            "已确认：${refreshed.entertainmentDailyLimit} 次 / ${refreshed.entertainmentDurationMs / 60_000L} 分钟"
                        } else {
                            "今日额度已经确认，不能再次修改"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("确认今日额度")
                }
                if (entertainmentMessage.isNotEmpty()) {
                    Text(entertainmentMessage)
                }
            }
        } else {
            QuotaSetupCard(title = "今日额度已锁定") {
                Text("${state.entertainmentDailyLimit} 次 / ${state.entertainmentDurationMs / 60_000L} 分钟")
                Text("已用 ${state.dailyEntertainmentCount} 次，剩余 ${(state.entertainmentDailyLimit - state.dailyEntertainmentCount).coerceAtLeast(0)} 次")
                if (entertainmentActive) {
                    Text("娱乐倒计时进行中")
                }
                OutlinedButton(
                    onClick = {
                        targetPickerRequestedAt = SystemClock.elapsedRealtime()
                        showTargetPicker = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("添加更多应用")
                }
            }
        }
        }

        HorizontalDivider()

        CollapsibleSection(
            repository = repository,
            moduleKey = "status",
            title = "当前状态",
            summary = if (entertainmentActive) "娱乐倒计时进行中" else "当前未处于娱乐放行",
            defaultExpanded = true,
            forceExpanded = entertainmentActive
        ) {
        Text("当前状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            if (!hasTodayQuota) {
                "今日额度：未确认；上次默认 ${state.defaultEntertainmentDailyLimit} 次 / ${state.defaultEntertainmentDurationMs / 60_000L} 分钟"
            } else {
                "今日娱乐次数：${state.dailyEntertainmentCount}/${state.entertainmentDailyLimit}，每次 ${state.entertainmentDurationMs / 60_000L} 分钟"
            }
        )
        if (hasTodayControlPlan) {
            Text("今日控制应用：${state.dailyControlledPackages.joinToString { repository.appDisplayName(it) }.ifBlank { "无" }}")
        } else {
            Text("今日控制应用：未选择")
        }
        Text("最近目标 App：${state.lastTargetPackage?.let { repository.appDisplayName(it) } ?: "-"}")
        Text("前台服务：${if (criticalPermissionsOk) "可运行" else "权限异常"}")
        }

        if (developerModeEnabled) {
            CollapsibleSection(
                repository = repository,
                moduleKey = "debug",
                title = "调试日志",
                summary = "最近状态机和跳转判断",
                defaultExpanded = false
            ) {
                Text("当前状态机：${state.promptFlowState}")
                Text("状态包名：${state.promptPackage ?: "-"}")
                Text("搜索流程：${state.searchSessionPackage ?: "-"} / ${state.lastDeepLinkResult}")
                Text("返回复查：${state.searchReturnCheckPackage ?: "-"}，until=${state.searchReturnCheckUntil}")
                Text("最近关键词：${state.lastKeyword ?: "-"}")
                Text("Bilibili 日志 Tag：SearchGateBiliDebug")
                Text("抖音日志 Tag：SearchGateDouyinDebug")
            }
        }

        CollapsibleSection(
            repository = repository,
            moduleKey = "advanced",
            title = "高级设置",
            summary = "守卫阈值和系统详情",
            defaultExpanded = false
        ) {
            GuardSettingsPanel(repository = repository)
            if (developerModeEnabled) {
                DeveloperModePanel(
                    repository = repository,
                    limits = developerQuotaLimits,
                    onLimitsChanged = { updated ->
                        developerQuotaLimits = updated
                        val clamped = QuotaPolicy.sanitizeUserSelection(
                            entertainmentDailyLimit,
                            entertainmentDurationMinutes,
                            updated
                        )
                        entertainmentDailyLimit = clamped.entertainmentCount
                        entertainmentDurationMinutes = clamped.entertainmentDurationMinutes
                    },
                    onExitDeveloperMode = {
                        repository.setDeveloperModeEnabled(false)
                        developerModeEnabled = false
                    }
                )
            }
            OutlinedButton(onClick = onOpenAppDetails, modifier = Modifier.fillMaxWidth()) {
                Text("打开系统应用详情")
            }
            Text("版本：v0.5.4", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DeveloperModeBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            "开发者模式",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun QuotaSetupCard(
    title: String,
    content: @Composable () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, Color(0xFFE1E1D9))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun QuotaWheelPicker(
    value: Int,
    unit: String,
    min: Int,
    max: Int,
    onValueChange: (Int) -> Unit
) {
    val safeMax = max.coerceAtLeast(min)
    val safeValue = value.coerceIn(min, safeMax)
    val indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .drawWithContent {
                    drawContent()
                    val halfBand = 22.dp.toPx()
                    val centerY = size.height / 2f
                    drawLine(indicatorColor, Offset(0f, centerY - halfBand), Offset(size.width, centerY - halfBand), 1.dp.toPx())
                    drawLine(indicatorColor, Offset(0f, centerY + halfBand), Offset(size.width, centerY + halfBand), 1.dp.toPx())
                },
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { context ->
                    NumberPicker(context).apply {
                        descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                        wrapSelectorWheel = false
                    }
                },
                update = { picker ->
                    picker.setOnValueChangedListener(null)
                    if (picker.minValue != min || picker.maxValue != safeMax) {
                        picker.displayedValues = null
                        picker.minValue = min
                        picker.maxValue = safeMax
                    }
                    picker.setFormatter { item -> "$item $unit" }
                    if (picker.value != safeValue) picker.value = safeValue
                    picker.setOnValueChangedListener { _, _, newValue ->
                        if (newValue != value) onValueChange(newValue)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            "当前：$safeValue $unit",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TargetAppPickerScreen(
    repository: GuardRepository,
    requestedAtElapsedRealtime: Long,
    developerModeEnabled: Boolean,
    onBack: () -> Unit
) {
    var installedApps by remember { mutableStateOf(repository.cachedInstalledLaunchableApps()) }
    var isLoadingApps by remember { mutableStateOf(installedApps.isEmpty()) }
    var targetConfigs by remember { mutableStateOf(repository.targetAppConfigs()) }
    var selectedInstalledApps by remember {
        mutableStateOf(
            targetConfigs
                .filter { it.isEnabled }
                .associate { it.packageName to it.appName }
        )
    }
    var appSearchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("all") }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(developerModeEnabled) {
        Log.i(
            SearchGateDevModeLog.TAG,
            "developer mode badge visible=$developerModeEnabled screen=target_picker"
        )
    }

    fun refreshFromRepository() {
        targetConfigs = repository.targetAppConfigs()
        selectedInstalledApps = targetConfigs
            .filter { it.isEnabled }
            .associate { it.packageName to it.appName }
    }

    LaunchedEffect(Unit) {
        val shellElapsedMs = SystemClock.elapsedRealtime() - requestedAtElapsedRealtime
        Log.i(
            SearchGateAppPickerLog.TAG,
            "picker shell rendered elapsedMs=$shellElapsedMs cacheHit=${installedApps.isNotEmpty()} cachedCount=${installedApps.size}"
        )
        isLoadingApps = installedApps.isEmpty()
        val refreshedApps = withContext(Dispatchers.IO) {
            repository.installedLaunchableApps()
        }
        installedApps = refreshedApps
        isLoadingApps = false
        Log.i(
            SearchGateAppPickerLog.TAG,
            "picker first list shown elapsedMs=${SystemClock.elapsedRealtime() - requestedAtElapsedRealtime} count=${refreshedApps.size}"
        )
        delay(800L)
        Log.i(SearchGateAppPickerLog.TAG, "icon cache summary ${repository.appIconCacheSummary()}")
    }

    val configsByPackage = remember(targetConfigs) { targetConfigs.associateBy { it.packageName } }
    val cleanQuery = appSearchQuery.trim()
    val allRows = remember(installedApps, targetConfigs, selectedInstalledApps, cleanQuery, filterMode) {
        val installedByPackage = installedApps.associateBy { it.packageName }
        val configuredRows = targetConfigs.map { config ->
            installedByPackage[config.packageName] ?: InstalledAppInfo(
                packageName = config.packageName,
                appName = config.appName,
                icon = null,
                isSelected = config.isEnabled,
                config = config
            )
        }
        val configuredPackages = configuredRows.map { it.packageName }.toSet()
        (configuredRows + installedApps.filter { it.packageName !in configuredPackages })
            .distinctBy { it.packageName }
            .filter { app ->
                val config = configsByPackage[app.packageName] ?: app.config
                val matchesQuery = cleanQuery.isEmpty() ||
                    app.appName.contains(cleanQuery, ignoreCase = true) ||
                    app.packageName.contains(cleanQuery, ignoreCase = true)
                val matchesFilter = when (filterMode) {
                    "selected" -> selectedInstalledApps.containsKey(app.packageName) ||
                        app.packageName in GuardRepository.DEFAULT_TARGET_PACKAGES
                    "user" -> config?.source == TargetAppSource.USER_ADDED ||
                        (config == null && app.packageName !in GuardRepository.DEFAULT_TARGET_PACKAGES)
                    "builtIn" -> app.packageName in GuardRepository.DEFAULT_TARGET_PACKAGES ||
                        config?.source == TargetAppSource.BUILT_IN
                    else -> true
                }
                matchesQuery && matchesFilter
            }
    }
    val canAddPackageFromSearch = cleanQuery.looksLikePackageName() &&
        cleanQuery != "com.example.focusgate" &&
        allRows.none { it.packageName == cleanQuery } &&
        cleanQuery !in GuardRepository.DEFAULT_TARGET_PACKAGES

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("选择限制应用", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (developerModeEnabled) DeveloperModeBadge()
            }
            TextButton(onClick = onBack) {
                Text("返回")
            }
        }
        OutlinedTextField(
            value = appSearchQuery,
            onValueChange = { appSearchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            singleLine = true,
            placeholder = { Text("搜索应用") }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TargetFilterButton("全部", filterMode == "all", Modifier.weight(1f)) { filterMode = "all" }
            TargetFilterButton("已选", filterMode == "selected", Modifier.weight(1f)) { filterMode = "selected" }
            TargetFilterButton("用户", filterMode == "user", Modifier.weight(1f)) { filterMode = "user" }
            TargetFilterButton("内置", filterMode == "builtIn", Modifier.weight(1f)) { filterMode = "builtIn" }
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isLoadingApps) {
                item(key = "installed-apps-loading") {
                    Text(
                        "正在读取已安装应用…",
                        modifier = Modifier.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            items(allRows, key = { it.packageName }) { app ->
                val config = configsByPackage[app.packageName] ?: app.config
                val builtIn = config?.source == TargetAppSource.BUILT_IN ||
                    app.packageName in GuardRepository.DEFAULT_TARGET_PACKAGES
                val appType = config?.appType ?: TargetAppType.ENTERTAINMENT_ONLY
                val source = config?.source ?: TargetAppSource.USER_ADDED
                val checked = selectedInstalledApps.containsKey(app.packageName) || builtIn
                val lockedToday = config?.let { repository.isUserTargetLockedToday(it) } == true && checked
                TargetAppPickerRow(
                    app = app.copy(config = config),
                    repository = repository,
                    checked = checked,
                    enabled = !builtIn,
                    appType = appType,
                    source = source,
                    statusText = when {
                        builtIn -> "内置"
                        lockedToday -> "明日可取消"
                        checked && source == TargetAppSource.USER_ADDED -> "用户"
                        else -> null
                    },
                    onCheckedChange = { newChecked ->
                        if (!newChecked && lockedToday && config != null) {
                            repository.logTargetLockAttempt(config)
                            message = "明天可取消：${app.appName}"
                        } else {
                            selectedInstalledApps = if (newChecked) {
                                selectedInstalledApps + (app.packageName to app.appName)
                            } else {
                                selectedInstalledApps - app.packageName
                            }
                            message = ""
                        }
                    }
                )
                HorizontalDivider()
            }
            if (canAddPackageFromSearch) {
                item(key = "add-package-$cleanQuery") {
                    OutlinedButton(
                        onClick = {
                            if (repository.addCandidatePackage(cleanQuery)) {
                                refreshFromRepository()
                                message = "已添加：${repository.appDisplayName(cleanQuery)}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("添加包名：$cleanQuery")
                    }
                }
            }
        }
        HorizontalDivider()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (message.isNotEmpty()) {
                Text(message, color = if (message.contains("不能") || message.contains("明天")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "已选 ${selectedInstalledApps.size}",
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterVertically),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Button(
                    onClick = {
                        val result = repository.saveInstalledAppSelections(selectedInstalledApps)
                        refreshFromRepository()
                        message = if (result.blockedLockedRemovals.isEmpty()) {
                            "已保存"
                        } else {
                            "已保存，${result.blockedLockedRemovals.size} 个明天可取消"
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存")
                }
            }
        }
    }
}

@Composable
private fun TargetFilterButton(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.height(40.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
        ) {
            Text(title, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(40.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
        ) {
            Text(title, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
        }
    }
}

@Composable
private fun DeveloperPasswordDialog(
    onDismiss: () -> Unit,
    onVerified: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("开发者调试模式") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("这是隐藏调试入口，发布版也可进入；固定密钥不是强安全机制，密钥不会写入日志。")
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = ""
                    },
                    singleLine = true,
                    label = { Text("开发者密钥") }
                )
                if (error.isNotEmpty()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (password == DEVELOPER_MODE_PASSWORD) {
                    Log.i(SearchGateDevModeLog.TAG, "developer password verified success")
                    onVerified()
                } else {
                    Log.w(SearchGateDevModeLog.TAG, "developer password verified failed")
                    error = "密钥错误"
                }
            }) {
                Text("进入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun DeveloperModePanel(
    repository: GuardRepository,
    limits: DeveloperQuotaLimits,
    onLimitsChanged: (DeveloperQuotaLimits) -> Unit,
    onExitDeveloperMode: () -> Unit
) {
    var state by remember { mutableStateOf(repository.state()) }
    var globalGuardEnabled by remember { mutableStateOf(repository.isGlobalGuardEnabled()) }
    var dailyLimit by remember { mutableStateOf(state.entertainmentDailyLimit.coerceAtLeast(state.defaultEntertainmentDailyLimit)) }
    var usedCount by remember { mutableStateOf(state.dailyEntertainmentCount) }
    var durationMinutes by remember {
        mutableStateOf(
            ((if (state.entertainmentDurationMs > 0L) state.entertainmentDurationMs else state.defaultEntertainmentDurationMs) / 60_000L).toInt()
        )
    }
    var message by remember { mutableStateOf("") }
    var maxCountInput by remember(limits) {
        mutableStateOf(limits.maxDailyEntertainmentCount.toString())
    }
    var maxDurationInput by remember(limits) {
        mutableStateOf(limits.maxEntertainmentDurationMinutes.toString())
    }

    fun refresh() {
        state = repository.state()
        globalGuardEnabled = repository.isGlobalGuardEnabled()
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("开发者调试模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("发布版保留隐藏入口和固定密钥，仅用于本机排查；不要把它当作强安全机制。", style = MaterialTheme.typography.bodySmall)
        Text("今日日期：${state.entertainmentDate}")
        Text("今日额度确认：${state.todayQuotaConfirmed}")
        Text("今日额度：${state.entertainmentDailyLimit} 次 / ${state.entertainmentDurationMs / 60_000L} 分钟")
        Text("今日已用：${state.dailyEntertainmentCount}，剩余 ${(state.entertainmentDailyLimit - state.dailyEntertainmentCount).coerceAtLeast(0)}")
        Text("娱乐倒计时：${if (state.entertainmentPackage != null && System.currentTimeMillis() < state.entertainmentUntil) "进行中 until=${state.entertainmentUntil}" else "未进行"}")
        Text("当前状态机：${state.promptFlowState} / ${state.promptPackage ?: "-"}")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("总限制开关（调试用）", fontWeight = FontWeight.SemiBold)
                Text(if (globalGuardEnabled) "限制逻辑启用" else "限制逻辑暂停", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = globalGuardEnabled,
                onCheckedChange = {
                    repository.setGlobalGuardEnabled(it)
                    refresh()
                }
            )
        }
        QuotaSetupCard(title = "额度上限") {
            DeveloperNumberField(
                label = "每日娱乐次数上限",
                value = maxCountInput,
                rangeHint = "1–${QuotaPolicy.DEVELOPER_MAX_DAILY_ENTERTAINMENT_COUNT}",
                onValueChange = { maxCountInput = it.filter(Char::isDigit).take(3) }
            )
            DeveloperNumberField(
                label = "单次娱乐时间上限（分钟）",
                value = maxDurationInput,
                rangeHint = "1–${QuotaPolicy.DEVELOPER_MAX_ENTERTAINMENT_DURATION_MINUTES}",
                onValueChange = { maxDurationInput = it.filter(Char::isDigit).take(3) }
            )
            Button(
                onClick = {
                    val updated = repository.updateDeveloperQuotaLimits(
                        maxCountInput.toIntOrNull() ?: limits.maxDailyEntertainmentCount,
                        maxDurationInput.toIntOrNull() ?: limits.maxEntertainmentDurationMinutes
                    )
                    maxCountInput = updated.maxDailyEntertainmentCount.toString()
                    maxDurationInput = updated.maxEntertainmentDurationMinutes.toString()
                    onLimitsChanged(updated)
                    message = "额度上限已保存"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存额度上限")
            }
            OutlinedButton(
                onClick = {
                    val restored = repository.restoreDefaultDeveloperQuotaLimits()
                    maxCountInput = restored.maxDailyEntertainmentCount.toString()
                    maxDurationInput = restored.maxEntertainmentDurationMinutes.toString()
                    onLimitsChanged(restored)
                    message = "额度上限已恢复默认"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("恢复默认值")
            }
        }
        ThresholdRow(
            title = "调试：今日次数上限",
            value = dailyLimit,
            unit = "次",
            enabled = true,
            min = QuotaPolicy.MIN_ENTERTAINMENT_COUNT,
            max = QuotaPolicy.DEVELOPER_MAX_DAILY_ENTERTAINMENT_COUNT,
            onChange = { dailyLimit = it }
        )
        ThresholdRow(
            title = "调试：今日已用次数",
            value = usedCount,
            unit = "次",
            enabled = true,
            min = 0,
            max = QuotaPolicy.DEVELOPER_MAX_DAILY_ENTERTAINMENT_COUNT,
            onChange = { usedCount = it }
        )
        ThresholdRow(
            title = "调试：单次娱乐时间",
            value = durationMinutes,
            unit = "分钟",
            enabled = true,
            min = QuotaPolicy.MIN_ENTERTAINMENT_DURATION_MINUTES,
            max = QuotaPolicy.DEVELOPER_MAX_ENTERTAINMENT_DURATION_MINUTES,
            onChange = { durationMinutes = it }
        )
        Button(
            onClick = {
                repository.debugUpdateTodayQuota(dailyLimit, usedCount, durationMinutes, clearConfirmation = false)
                refresh()
                message = "已写入调试额度"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("写入调试额度")
        }
        OutlinedButton(
            onClick = {
                repository.debugResetDailyUsage()
                refresh()
                message = "已重置今日使用次数"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("重置今日次数")
        }
        OutlinedButton(
            onClick = {
                repository.debugClearEntertainmentSession()
                refresh()
                message = "已清除娱乐倒计时"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("清除娱乐倒计时")
        }
        OutlinedButton(
            onClick = {
                repository.debugClearStudyLookupState()
                repository.clearPromptState()
                refresh()
                message = "已清除资料查询和弹窗状态"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("清除资料查询/弹窗状态")
        }
        OutlinedButton(
            onClick = {
                repository.debugUpdateTodayQuota(dailyLimit, usedCount, durationMinutes, clearConfirmation = true)
                refresh()
                message = "已清除今日额度确认状态"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("清除今日额度确认")
        }
        OutlinedButton(
            onClick = {
                repository.debugClearUserTargetLocks()
                refresh()
                message = "已解除用户新增应用的今日取消锁"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("调试：解除今日新增应用锁")
        }
        OutlinedButton(
            onClick = {
                repository.debugClearUserAddedTargets()
                refresh()
                message = "已清空用户新增限制应用"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("调试：清空用户新增应用")
        }
        OutlinedButton(
            onClick = onExitDeveloperMode,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("退出开发者模式")
        }
        if (message.isNotEmpty()) {
            Text(message)
        }
    }
}

@Composable
private fun DeveloperNumberField(
    label: String,
    value: String,
    rangeHint: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = { Text("范围：$rangeHint") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun GuardSettingsPanel(repository: GuardRepository) {
    var repeatedScrollThreshold by remember { mutableStateOf(repository.repeatedScrollThreshold()) }
    var biliJumpLimit by remember { mutableStateOf(repository.biliRecommendationJumpLimit()) }
    var message by remember { mutableStateOf("") }
    val lockedToday = repository.hasTodayGuardSettingsEdit()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("守卫设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            if (lockedToday) {
                "今天已经修改过，明天才可以再次保存。"
            } else {
                "连续滑动和推荐连跳阈值每天只能保存一次。"
            },
            style = MaterialTheme.typography.bodySmall
        )
        ThresholdRow(
            title = "连续上滑拦截",
            value = repeatedScrollThreshold,
            unit = "次",
            enabled = !lockedToday,
            min = GuardRepository.MIN_REPEATED_SCROLL_THRESHOLD,
            max = GuardRepository.MAX_REPEATED_SCROLL_THRESHOLD,
            onChange = { repeatedScrollThreshold = it }
        )
        ThresholdRow(
            title = "B站推荐连跳拦截",
            value = biliJumpLimit,
            unit = "次",
            enabled = !lockedToday,
            min = GuardRepository.MIN_BILI_RECOMMENDATION_JUMP_LIMIT,
            max = GuardRepository.MAX_BILI_RECOMMENDATION_JUMP_LIMIT,
            onChange = { biliJumpLimit = it }
        )
        Button(
            onClick = {
                message = if (repository.saveGuardSettings(repeatedScrollThreshold, biliJumpLimit)) {
                    "已保存"
                } else {
                    "今天已经修改过"
                }
            },
            enabled = !lockedToday,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存守卫设置")
        }
        if (message.isNotEmpty()) Text(message)
    }
}

@Composable
private fun ThresholdRow(
    title: String,
    value: Int,
    unit: String,
    enabled: Boolean,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("$value $unit", style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onChange((value - 1).coerceAtLeast(min)) },
                enabled = enabled && value > min
            ) {
                Text("-")
            }
            OutlinedButton(
                onClick = { onChange((value + 1).coerceAtMost(max)) },
                enabled = enabled && value < max
            ) {
                Text("+")
            }
        }
    }
}

@Composable
private fun TargetAppPickerRow(
    app: InstalledAppInfo,
    repository: GuardRepository,
    checked: Boolean,
    enabled: Boolean,
    appType: TargetAppType,
    source: TargetAppSource,
    statusText: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DrawableAppIcon(app.packageName, app.icon, repository)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 12.dp)
        ) {
            Text(app.appName, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TargetTag(
                    when (appType) {
                        TargetAppType.HYBRID_STUDY_ENTERTAINMENT -> "资料+娱乐"
                        TargetAppType.ENTERTAINMENT_ONLY -> "纯限制"
                    }
                )
                TargetTag(if (source == TargetAppSource.BUILT_IN) "内置" else "用户")
                if (statusText != null && statusText != "内置" && statusText != "用户") {
                    TargetTag(statusText)
                }
            }
        }
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
private fun TargetTag(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text,
            modifier = Modifier
                .heightIn(min = 22.dp)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@Composable
private fun DrawableAppIcon(packageName: String, initialIcon: Drawable?, repository: GuardRepository) {
    var icon by remember(packageName, initialIcon) {
        mutableStateOf(initialIcon ?: repository.cachedAppIcon(packageName))
    }
    LaunchedEffect(packageName) {
        if (icon == null) {
            icon = withContext(Dispatchers.IO) { repository.loadAppIcon(packageName) }
        }
    }
    val bitmap = remember(icon) {
        runCatching { icon?.toBitmap(width = 48, height = 48)?.asImageBitmap() }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
    } else {
        Spacer(Modifier.size(40.dp))
    }
}

@Composable
private fun AppPackageRow(
    appName: String,
    packageName: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(appName, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(packageName, style = MaterialTheme.typography.bodySmall)
        }
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

private const val DEVELOPER_MODE_PASSWORD = "qwertyuiopasdfghjklzxcvbnm"

private fun String.looksLikePackageName(): Boolean {
    val clean = trim()
    if (clean.count { it == '.' } < 2) return false
    return clean.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*){2,}"))
}

@Composable
private fun PermissionRow(
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
    note: String = if (enabled) "已开启" else "未开启"
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(note, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = onClick) {
            Text(if (enabled) "查看" else "去开启")
        }
    }
}
