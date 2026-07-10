package com.example.focusgate

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.time.LocalDate

data class GuardState(
    val targetPackages: Set<String>,
    val dailyControlPlanDate: String?,
    val dailyControlledPackages: Set<String>,
    val searchGraceUntil: Long,
    val searchSessionPackage: String?,
    val searchStartedAt: Long,
    val entertainmentStartedAt: Long,
    val entertainmentUntil: Long,
    val entertainmentPackage: String?,
    val entertainmentDate: String,
    val entertainmentPlanDate: String?,
    val todayQuotaDate: String?,
    val todayQuotaConfirmed: Boolean,
    val defaultEntertainmentDailyLimit: Int,
    val defaultEntertainmentDurationMs: Long,
    val entertainmentDailyLimit: Int,
    val entertainmentDurationMs: Long,
    val dailyEntertainmentCount: Int,
    val lastTargetPackage: String?,
    val lastKeyword: String?,
    val lastPlatform: String?,
    val lastDeepLinkResult: DeepLinkResult,
    val lastInterceptTime: Long,
    val repeatedScrollThreshold: Int,
    val biliRecommendationJumpLimit: Int,
    val guardSettingsDate: String?,
    val entertainmentSettingsConfigured: Boolean,
    val promptFlowState: PromptFlowState,
    val promptPackage: String?,
    val promptUpdatedAt: Long,
    val searchReturnCheckPackage: String?,
    val searchReturnCheckUntil: Long
)

data class EntertainmentSessionStatus(
    val entertainmentStartAtMillis: Long,
    val entertainmentEndAtMillis: Long,
    val entertainmentDurationMillis: Long,
    val nowMillis: Long,
    val remainingMillis: Long,
    val packageName: String?,
    val targetPackage: String,
    val isEntertainmentActive: Boolean,
    val anomaly: String?
)

class GuardRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun state(): GuardState {
        migrateEntertainmentQuotaModel()
        normalizeEntertainmentDate()
        normalizeDailyControlDate()
        normalizeExpiredEntertainment()
        normalizeSearchSession()
        val searchGraceUntil = prefs.getLong(KEY_SEARCH_GRACE_UNTIL, 0L)
        val promptState = prefs.getString(KEY_PROMPT_FLOW_STATE, PromptFlowState.IDLE.name)
            ?.let { runCatching { PromptFlowState.valueOf(it) }.getOrDefault(PromptFlowState.IDLE) }
            ?: PromptFlowState.IDLE
        return GuardState(
            targetPackages = targetPackages(),
            dailyControlPlanDate = prefs.getString(KEY_DAILY_CONTROL_PLAN_DATE, null),
            dailyControlledPackages = todayControlledPackages(),
            searchGraceUntil = searchGraceUntil,
            searchSessionPackage = prefs.getString(KEY_SEARCH_SESSION_PACKAGE, null),
            searchStartedAt = prefs.getLong(KEY_SEARCH_STARTED_AT, searchGraceUntil - SEARCH_GRACE_MS).coerceAtLeast(0L),
            entertainmentStartedAt = prefs.getLong(KEY_ENTERTAINMENT_STARTED_AT, 0L),
            entertainmentUntil = prefs.getLong(KEY_ENTERTAINMENT_UNTIL, 0L),
            entertainmentPackage = prefs.getString(KEY_ENTERTAINMENT_PACKAGE, null),
            entertainmentDate = prefs.getString(KEY_ENTERTAINMENT_DATE, today()) ?: today(),
            entertainmentPlanDate = prefs.getString(KEY_ENTERTAINMENT_PLAN_DATE, null),
            todayQuotaDate = prefs.getString(KEY_TODAY_QUOTA_DATE, null),
            todayQuotaConfirmed = hasTodayEntertainmentPlan(),
            defaultEntertainmentDailyLimit = defaultEntertainmentLimit(),
            defaultEntertainmentDurationMs = defaultEntertainmentDurationMs(),
            entertainmentDailyLimit = todayEntertainmentLimit(),
            entertainmentDurationMs = todayEntertainmentDurationMs(),
            dailyEntertainmentCount = prefs.getInt(KEY_DAILY_ENTERTAINMENT_COUNT, 0),
            lastTargetPackage = prefs.getString(KEY_LAST_TARGET_PACKAGE, null),
            lastKeyword = prefs.getString(KEY_LAST_KEYWORD, null),
            lastPlatform = prefs.getString(KEY_LAST_PLATFORM, null),
            lastDeepLinkResult = prefs.getString(KEY_LAST_DEEP_LINK_RESULT, DeepLinkResult.NOT_STARTED.name)
                ?.let { runCatching { DeepLinkResult.valueOf(it) }.getOrDefault(DeepLinkResult.NOT_STARTED) }
                ?: DeepLinkResult.NOT_STARTED,
            lastInterceptTime = prefs.getLong(KEY_LAST_INTERCEPT_TIME, 0L),
            repeatedScrollThreshold = repeatedScrollThreshold(),
            biliRecommendationJumpLimit = biliRecommendationJumpLimit(),
            guardSettingsDate = prefs.getString(KEY_GUARD_SETTINGS_DATE, null),
            entertainmentSettingsConfigured = hasDefaultEntertainmentSettings(),
            promptFlowState = promptState,
            promptPackage = prefs.getString(KEY_PROMPT_PACKAGE, null),
            promptUpdatedAt = prefs.getLong(KEY_PROMPT_UPDATED_AT, 0L),
            searchReturnCheckPackage = prefs.getString(KEY_SEARCH_RETURN_CHECK_PACKAGE, null),
            searchReturnCheckUntil = prefs.getLong(KEY_SEARCH_RETURN_CHECK_UNTIL, 0L)
        )
    }

    fun targetPackages(): Set<String> {
        migrateDefaultTargets()
        return prefs.getStringSet(KEY_TARGET_PACKAGES, DEFAULT_TARGET_PACKAGES) ?: DEFAULT_TARGET_PACKAGES
    }

    fun targetAppConfigs(): List<TargetAppConfig> {
        migrateDefaultTargets()
        val enabledTargets = targetPackages()
        val userConfigs = storedUserTargetConfigs()
        val userPackages = (
            userConfigs.map { it.packageName } +
                candidatePackagesWithoutMigration().filter { it !in DEFAULT_TARGET_PACKAGES }
            )
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        val builtIns = listOf(
            builtInTargetConfig(TargetPlatform.XHS, TargetAppType.HYBRID_STUDY_ENTERTAINMENT),
            builtInTargetConfig(TargetPlatform.BILI, TargetAppType.HYBRID_STUDY_ENTERTAINMENT),
            builtInTargetConfig(TargetPlatform.DOUYIN, TargetAppType.ENTERTAINMENT_ONLY)
        )
        val userRows = userPackages.map { packageName ->
            userConfigs.firstOrNull { it.packageName == packageName }?.copy(
                appName = appDisplayName(packageName),
                isEnabled = enabledTargets.contains(packageName)
            ) ?: TargetAppConfig(
                packageName = packageName,
                appName = appDisplayName(packageName),
                appType = TargetAppType.ENTERTAINMENT_ONLY,
                isEnabled = enabledTargets.contains(packageName),
                allowStudyLookup = false,
                allowEntertainment = true,
                source = TargetAppSource.USER_ADDED
            )
        }
        return builtIns + userRows
    }

    fun targetAppConfig(packageName: String): TargetAppConfig? =
        targetAppConfigs().firstOrNull { it.packageName == packageName && it.isEnabled }

    fun canRemoveUserTarget(config: TargetAppConfig, currentDate: String = today()): Boolean {
        if (config.source != TargetAppSource.USER_ADDED) return false
        val canRemoveAfter = config.canRemoveAfterDate ?: return true
        return currentDate >= canRemoveAfter
    }

    fun isUserTargetLockedToday(config: TargetAppConfig, currentDate: String = today()): Boolean =
        config.source == TargetAppSource.USER_ADDED &&
            config.isEnabled &&
            !canRemoveUserTarget(config, currentDate)

    fun logTargetLockAttempt(config: TargetAppConfig) {
        Log.w(
            SearchGateTargetAppLockLog.TAG,
            "locked target app removal blocked package=${config.packageName} addedDate=${config.addedDate} canRemoveAfterDate=${config.canRemoveAfterDate} today=${today()}"
        )
    }

    fun installedLaunchableApps(): List<InstalledAppInfo> {
        val pm = appContext.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }
        val configs = targetAppConfigs().associateBy { it.packageName }
        val rows = activities
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName?.trim().orEmpty()
                if (packageName.isEmpty() || packageName == appContext.packageName) {
                    null
                } else {
                    val appName = resolveInfo.loadLabel(pm).toString().ifBlank { packageName }
                    InstalledAppInfo(
                        packageName = packageName,
                        appName = appName,
                        icon = runCatching { resolveInfo.loadIcon(pm) }.getOrNull(),
                        isSelected = targetPackages().contains(packageName),
                        config = configs[packageName]
                    )
                }
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
        Log.i(
            SearchGateAppPickerLog.TAG,
            "installedAppsRead totalResolveCount=${activities.size} launchableCount=${rows.size} selectedCount=${rows.count { it.isSelected }} packageVisibility=launcher-intent-query"
        )
        return rows
    }

    fun saveInstalledAppSelections(selected: Map<String, String>): TargetAppSaveResult {
        val cleanSelected = selected
            .mapKeys { it.key.trim() }
            .filterKeys { it.isNotEmpty() && it != appContext.packageName }
        val builtInPackages = DEFAULT_TARGET_PACKAGES
        val existing = storedUserTargetConfigs().associateBy { it.packageName }
        val todayDate = today()
        val tomorrowDate = LocalDate.now().plusDays(1).toString()
        val blockedLockedRemovals = mutableListOf<TargetAppConfig>()
        val allUserPackages = (existing.keys + cleanSelected.keys.filter { it !in builtInPackages }).toSet()
        val userConfigs = allUserPackages.map { packageName ->
            val previous = existing[packageName]
            val requestedEnabled = cleanSelected.containsKey(packageName)
            val lockedRemoval = previous != null &&
                previous.source == TargetAppSource.USER_ADDED &&
                previous.isEnabled &&
                !requestedEnabled &&
                !canRemoveUserTarget(previous, todayDate)
            if (lockedRemoval) {
                val lockedConfig = checkNotNull(previous)
                blockedLockedRemovals += lockedConfig
                Log.w(
                    SearchGateTargetAppLockLog.TAG,
                    "blocked locked removal package=$packageName addedDate=${lockedConfig.addedDate} canRemoveAfterDate=${lockedConfig.canRemoveAfterDate} today=$todayDate"
                )
            }
            val finalEnabled = requestedEnabled || lockedRemoval
            val appName = cleanSelected[packageName]
                ?: previous?.appName
                ?: appDisplayName(packageName)
            TargetAppConfig(
                packageName = packageName,
                appName = appName,
                appType = previous?.appType ?: TargetAppType.ENTERTAINMENT_ONLY,
                isEnabled = finalEnabled,
                allowStudyLookup = previous?.allowStudyLookup ?: false,
                allowEntertainment = previous?.allowEntertainment ?: true,
                source = TargetAppSource.USER_ADDED
            ).withDailyLockIfNewlyEnabled(previous, finalEnabled, todayDate, tomorrowDate)
        }.toSet()
        val enabledUserPackages = userConfigs.filter { it.isEnabled }.map { it.packageName }.toSet()
        val enabledTargets = builtInPackages + enabledUserPackages
        val editor = prefs.edit()
            .putStringSet(KEY_USER_TARGET_CONFIGS, userConfigs.map { encodeTargetConfig(it) }.toSet())
            .putStringSet(KEY_CANDIDATE_PACKAGES, candidatePackagesWithoutMigration() + enabledTargets + allUserPackages)
            .putStringSet(KEY_TARGET_PACKAGES, enabledTargets)
        if (hasTodayControlPlan()) {
            editor.putStringSet(
                KEY_DAILY_CONTROLLED_PACKAGES,
                (todayControlledPackages().filter { it in builtInPackages } + enabledUserPackages).toSet()
            )
        }
        editor.apply()
        Log.i(
            SearchGateAppPickerLog.TAG,
            "saveInstalledSelections added=${enabledUserPackages.filter { it !in existing || existing[it]?.isEnabled == false }} removed=${existing.values.filter { it.isEnabled && it.packageName !in enabledTargets }.map { it.packageName }} blockedLockedRemovals=${blockedLockedRemovals.map { it.packageName }} configs=${targetAppConfigs().joinToString { "${it.packageName}:${it.appType}:${it.isEnabled}:${it.source}:${it.addedDate}:${it.canRemoveAfterDate}" }}"
        )
        Log.i(
            SearchGateTargetAppLockLog.TAG,
            "saveSelections today=$todayDate enabledUserPackages=$enabledUserPackages blockedLockedRemovals=${blockedLockedRemovals.map { it.packageName }}"
        )
        return TargetAppSaveResult(
            selectedCount = enabledTargets.size,
            blockedLockedRemovals = blockedLockedRemovals
        )
    }

    fun candidatePackages(): Set<String> {
        val stored = prefs.getStringSet(KEY_CANDIDATE_PACKAGES, null).orEmpty()
        return (DEFAULT_TARGET_PACKAGES + stored + targetPackages())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun addCandidatePackage(packageName: String): Boolean {
        val clean = packageName.trim()
        if (clean.isEmpty()) return false
        saveUserTargetConfig(clean, appDisplayName(clean), enabled = true)
        val candidates = candidatePackages() + clean
        val targets = targetPackages() + clean
        val editor = prefs.edit()
            .putStringSet(KEY_CANDIDATE_PACKAGES, candidates)
            .putStringSet(KEY_TARGET_PACKAGES, targets)
        if (hasTodayControlPlan()) {
            editor.putStringSet(KEY_DAILY_CONTROLLED_PACKAGES, todayControlledPackages() + clean)
        }
        editor.apply()
        return true
    }

    fun saveTargetPackages(packages: Set<String>) {
        val clean = packages.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        clean.filter { it !in DEFAULT_TARGET_PACKAGES }.forEach {
            saveUserTargetConfig(it, appDisplayName(it), enabled = true)
        }
        val targets = if (hasTodayControlPlan()) targetPackages() + clean else clean
        val editor = prefs.edit()
            .putStringSet(KEY_TARGET_PACKAGES, targets)
            .putStringSet(KEY_CANDIDATE_PACKAGES, candidatePackages() + targets)
        if (hasTodayControlPlan()) {
            editor.putStringSet(KEY_DAILY_CONTROLLED_PACKAGES, todayControlledPackages() + clean)
        }
        editor.apply()
    }

    private fun migrateDefaultTargets() {
        val version = prefs.getInt(KEY_TARGET_DEFAULTS_VERSION, 0)
        if (version >= TARGET_DEFAULTS_VERSION) return
        val current = prefs.getStringSet(KEY_TARGET_PACKAGES, null)
        val migrated = (current ?: DEFAULT_TARGET_PACKAGES) + DEFAULT_TARGET_PACKAGES
        val editor = prefs.edit()
            .putStringSet(KEY_TARGET_PACKAGES, migrated)
            .putStringSet(KEY_CANDIDATE_PACKAGES, candidatePackagesWithoutMigration() + migrated)
            .putInt(KEY_TARGET_DEFAULTS_VERSION, TARGET_DEFAULTS_VERSION)
        if (hasTodayControlPlan()) {
            editor.putStringSet(KEY_DAILY_CONTROLLED_PACKAGES, todayControlledPackages() + DEFAULT_TARGET_PACKAGES)
        }
        editor.apply()
    }

    private fun candidatePackagesWithoutMigration(): Set<String> {
        val stored = prefs.getStringSet(KEY_CANDIDATE_PACKAGES, null).orEmpty()
        val targets = prefs.getStringSet(KEY_TARGET_PACKAGES, null).orEmpty()
        return (DEFAULT_TARGET_PACKAGES + stored + targets)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun resetScrollIntervention(packageName: String) {
        if (!usesRepeatedScrollGuard(packageName)) return
        prefs.edit()
            .remove(scrollInterventionKey(packageName))
            .apply()
    }

    fun markScrollIntervention(packageName: String, now: Long = System.currentTimeMillis()) {
        if (!usesRepeatedScrollGuard(packageName)) return
        prefs.edit()
            .putLong(scrollInterventionKey(packageName), now + SCROLL_INTERVENTION_COOLDOWN_MS)
            .apply()
    }

    fun isInScrollInterventionCooldown(packageName: String, now: Long = System.currentTimeMillis()): Boolean =
        usesRepeatedScrollGuard(packageName) &&
            now < prefs.getLong(scrollInterventionKey(packageName), 0L)

    fun repeatedScrollThreshold(): Int =
        prefs.getInt(KEY_REPEATED_SCROLL_THRESHOLD, DEFAULT_REPEATED_SCROLL_THRESHOLD)
            .coerceIn(MIN_REPEATED_SCROLL_THRESHOLD, MAX_REPEATED_SCROLL_THRESHOLD)

    fun biliRecommendationJumpLimit(): Int =
        prefs.getInt(KEY_BILI_RECOMMENDATION_JUMP_LIMIT, DEFAULT_BILI_RECOMMENDATION_JUMP_LIMIT)
            .coerceIn(MIN_BILI_RECOMMENDATION_JUMP_LIMIT, MAX_BILI_RECOMMENDATION_JUMP_LIMIT)

    fun hasTodayGuardSettingsEdit(): Boolean =
        prefs.getString(KEY_GUARD_SETTINGS_DATE, null) == today()

    fun saveGuardSettings(repeatedScrollThreshold: Int, biliRecommendationJumpLimit: Int): Boolean {
        if (hasTodayGuardSettingsEdit()) return false
        prefs.edit()
            .putString(KEY_GUARD_SETTINGS_DATE, today())
            .putInt(
                KEY_REPEATED_SCROLL_THRESHOLD,
                repeatedScrollThreshold.coerceIn(MIN_REPEATED_SCROLL_THRESHOLD, MAX_REPEATED_SCROLL_THRESHOLD)
            )
            .putInt(
                KEY_BILI_RECOMMENDATION_JUMP_LIMIT,
                biliRecommendationJumpLimit.coerceIn(
                    MIN_BILI_RECOMMENDATION_JUMP_LIMIT,
                    MAX_BILI_RECOMMENDATION_JUMP_LIMIT
                )
            )
            .apply()
        return true
    }

    fun hasTodayControlPlan(): Boolean =
        prefs.getString(KEY_DAILY_CONTROL_PLAN_DATE, null) == today()

    fun todayControlledPackages(): Set<String> {
        if (!hasTodayControlPlan()) return emptySet()
        return prefs.getStringSet(KEY_DAILY_CONTROLLED_PACKAGES, emptySet()).orEmpty()
    }

    fun isControlledToday(packageName: String): Boolean =
        hasTodayControlPlan() && todayControlledPackages().contains(packageName)

    fun setTodayControlledPackages(packages: Set<String>): Boolean {
        normalizeDailyControlDate()
        if (hasTodayControlPlan()) return false
        val clean = packages
            .map { it.trim() }
            .filter { it.isNotEmpty() && targetPackages().contains(it) }
            .toSet()
        prefs.edit()
            .putString(KEY_DAILY_CONTROL_PLAN_DATE, today())
            .putStringSet(KEY_DAILY_CONTROLLED_PACKAGES, clean)
            .apply()
        return true
    }

    fun markIntercept(packageName: String, at: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putString(KEY_LAST_TARGET_PACKAGE, packageName)
            .putLong(KEY_LAST_INTERCEPT_TIME, at)
            .apply()
    }

    fun startSearch(packageName: String, keyword: String, platformName: String, now: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putLong(KEY_SEARCH_GRACE_UNTIL, now + SEARCH_GRACE_MS)
            .putString(KEY_SEARCH_SESSION_PACKAGE, packageName)
            .putLong(KEY_SEARCH_STARTED_AT, now)
            .putString(KEY_LAST_KEYWORD, keyword)
            .putString(KEY_LAST_PLATFORM, platformName)
            .putString(KEY_LAST_DEEP_LINK_RESULT, DeepLinkResult.NOT_STARTED.name)
            .putString(KEY_SEARCH_RETURN_CHECK_PACKAGE, packageName)
            .putLong(KEY_SEARCH_RETURN_CHECK_UNTIL, now + SEARCH_RETURN_CHECK_TTL_MS)
            .putString(KEY_PROMPT_FLOW_STATE, PromptFlowState.SEARCH_ACTIVITY.name)
            .putString(KEY_PROMPT_PACKAGE, packageName)
            .putLong(KEY_PROMPT_UPDATED_AT, now)
            .apply()
        Log.i(FocusGateLog.TAG, "search flow started package=$packageName keyword=$keyword returnCheckUntil=${now + SEARCH_RETURN_CHECK_TTL_MS}")
    }

    fun saveDeepLinkResult(result: DeepLinkResult) {
        prefs.edit().putString(KEY_LAST_DEEP_LINK_RESULT, result.name).apply()
    }

    fun markStartedButNotVerified() {
        prefs.edit().putString(KEY_LAST_DEEP_LINK_RESULT, DeepLinkResult.STARTED_BUT_NOT_VERIFIED.name).apply()
    }

    fun markSearchVerified() {
        prefs.edit().putString(KEY_LAST_DEEP_LINK_RESULT, DeepLinkResult.VERIFIED_SEARCH.name).apply()
    }

    fun markSearchPageObserved(packageName: String, now: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putString(KEY_SEARCH_SESSION_PACKAGE, packageName)
            .putLong(KEY_SEARCH_STARTED_AT, now)
            .putLong(KEY_SEARCH_GRACE_UNTIL, now)
            .putString(KEY_LAST_DEEP_LINK_RESULT, DeepLinkResult.VERIFIED_SEARCH.name)
            .putString(KEY_SEARCH_RETURN_CHECK_PACKAGE, packageName)
            .putLong(KEY_SEARCH_RETURN_CHECK_UNTIL, now + SEARCH_RETURN_CHECK_TTL_MS)
            .apply()
    }

    fun allowManualOpen(packageName: String, now: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putLong(KEY_SEARCH_GRACE_UNTIL, now + SEARCH_GRACE_MS)
            .putString(KEY_SEARCH_SESSION_PACKAGE, packageName)
            .putLong(KEY_SEARCH_STARTED_AT, now)
            .putString(KEY_LAST_DEEP_LINK_RESULT, DeepLinkResult.STARTED_BUT_NOT_VERIFIED.name)
            .putString(KEY_SEARCH_RETURN_CHECK_PACKAGE, packageName)
            .putLong(KEY_SEARCH_RETURN_CHECK_UNTIL, now + SEARCH_RETURN_CHECK_TTL_MS)
            .putString(KEY_PROMPT_FLOW_STATE, PromptFlowState.SEARCH_ACTIVITY.name)
            .putString(KEY_PROMPT_PACKAGE, packageName)
            .putLong(KEY_PROMPT_UPDATED_AT, now)
            .apply()
    }

    fun clearSearchSession() {
        prefs.edit()
            .putLong(KEY_SEARCH_GRACE_UNTIL, 0L)
            .remove(KEY_SEARCH_SESSION_PACKAGE)
            .remove(KEY_SEARCH_STARTED_AT)
            .remove(KEY_SEARCH_RETURN_CHECK_PACKAGE)
            .remove(KEY_SEARCH_RETURN_CHECK_UNTIL)
            .putString(KEY_LAST_DEEP_LINK_RESULT, DeepLinkResult.NOT_STARTED.name)
            .apply()
    }

    fun refreshVerifiedSearchSession(now: Long = System.currentTimeMillis()) {
        if (prefs.getString(KEY_LAST_DEEP_LINK_RESULT, null) != DeepLinkResult.VERIFIED_SEARCH.name) return
        prefs.edit().putLong(KEY_SEARCH_STARTED_AT, now).apply()
    }

    fun refreshSearchSession(now: Long = System.currentTimeMillis()) {
        if (prefs.getString(KEY_SEARCH_SESSION_PACKAGE, null) == null) return
        prefs.edit().putLong(KEY_SEARCH_STARTED_AT, now).apply()
    }

    fun canUseEntertainment(): Boolean {
        normalizeEntertainmentDate()
        normalizeExpiredEntertainment()
        val limit = todayEntertainmentLimit()
        val count = prefs.getInt(KEY_DAILY_ENTERTAINMENT_COUNT, 0)
        val canUse = hasTodayEntertainmentPlan() && count < limit
        Log.i(
            FocusGateLog.TAG,
            "quota read date=${today()} todayConfirmed=${hasTodayEntertainmentPlan()} todayLimit=$limit todayDurationMinutes=${todayEntertainmentDurationMs() / 60_000L} defaultLimit=${defaultEntertainmentLimit()} defaultDurationMinutes=${defaultEntertainmentDurationMs() / 60_000L} usedCount=$count remaining=${(limit - count).coerceAtLeast(0)} canUse=$canUse"
        )
        return canUse
    }

    fun startEntertainment(packageName: String, now: Long = System.currentTimeMillis()): Boolean {
        normalizeEntertainmentDate()
        normalizeExpiredEntertainment(now)
        if (!hasTodayEntertainmentPlan()) return false
        val count = prefs.getInt(KEY_DAILY_ENTERTAINMENT_COUNT, 0)
        val limit = todayEntertainmentLimit()
        if (count >= limit) {
            markPromptState(PromptFlowState.ENTERTAINMENT_EXHAUSTED, packageName, now)
            return false
        }
        val durationMs = todayEntertainmentDurationMs()
        prefs.edit()
            .putLong(KEY_ENTERTAINMENT_STARTED_AT, now)
            .putLong(KEY_ENTERTAINMENT_UNTIL, now + durationMs)
            .putString(KEY_ENTERTAINMENT_PACKAGE, packageName)
            .putString(KEY_ENTERTAINMENT_DATE, today())
            .putInt(KEY_DAILY_ENTERTAINMENT_COUNT, count + 1)
            .putString(KEY_PROMPT_FLOW_STATE, PromptFlowState.ENTERTAINMENT_ACTIVE.name)
            .putString(KEY_PROMPT_PACKAGE, packageName)
            .putLong(KEY_PROMPT_UPDATED_AT, now)
            .apply()
        Log.i(FocusGateLog.TAG, "entertainment started package=$packageName dailyLimit=$limit durationMs=$durationMs usedCount=${count + 1} until=${now + durationMs}")
        Log.i(
            SearchGateTimerLog.TAG,
            "startEntertainment nowMillis=$now entertainmentStartAtMillis=$now entertainmentEndAtMillis=${now + durationMs} entertainmentDurationMillis=$durationMs target=$packageName usedCount=${count + 1}/$limit"
        )
        StabilityForegroundService.syncEntertainmentState(appContext)
        return true
    }

    fun endEntertainment(reason: PromptFlowState = PromptFlowState.ENTERTAINMENT_ENDED_BY_USER, targetPackage: String? = null) {
        val nowMillis = System.currentTimeMillis()
        val previousStartAtMillis = prefs.getLong(KEY_ENTERTAINMENT_STARTED_AT, 0L)
        val previousEndAtMillis = prefs.getLong(KEY_ENTERTAINMENT_UNTIL, 0L)
        val previousPackage = prefs.getString(KEY_ENTERTAINMENT_PACKAGE, null)
        prefs.edit()
            .putLong(KEY_ENTERTAINMENT_STARTED_AT, 0L)
            .putLong(KEY_ENTERTAINMENT_UNTIL, 0L)
            .remove(KEY_ENTERTAINMENT_PACKAGE)
            .putString(KEY_PROMPT_FLOW_STATE, reason.name)
            .remove(KEY_PROMPT_PACKAGE)
            .putLong(KEY_PROMPT_UPDATED_AT, nowMillis)
            .apply()
        Log.i(FocusGateLog.TAG, "entertainment ended reason=$reason")
        Log.i(
            SearchGateTimerLog.TAG,
            "endEntertainment reason=$reason requestedTarget=${targetPackage ?: "-"} previousPackage=${previousPackage ?: "-"} nowMillis=$nowMillis entertainmentStartAtMillis=$previousStartAtMillis entertainmentEndAtMillis=$previousEndAtMillis remainingMillis=${(previousEndAtMillis - nowMillis).coerceAtLeast(0L)}"
        )
        StabilityForegroundService.syncEntertainmentState(appContext)
    }

    fun hasDefaultEntertainmentSettings(): Boolean =
        prefs.getBoolean(KEY_DEFAULT_ENTERTAINMENT_CONFIGURED, false)

    fun hasEntertainmentSettings(): Boolean = hasDefaultEntertainmentSettings()

    fun hasTodayEntertainmentPlan(): Boolean =
        prefs.getBoolean(KEY_TODAY_QUOTA_CONFIRMED, false) &&
            prefs.getString(KEY_TODAY_QUOTA_DATE, null) == today()

    fun setTodayEntertainmentPlan(dailyLimit: Int, durationMinutes: Int): Boolean {
        return confirmTodayEntertainmentPlan(dailyLimit, durationMinutes)
    }

    fun saveEntertainmentSettings(dailyLimit: Int, durationMinutes: Int): Boolean {
        return saveDefaultEntertainmentSettings(dailyLimit, durationMinutes)
    }

    fun confirmTodayEntertainmentPlan(dailyLimit: Int, durationMinutes: Int): Boolean {
        normalizeEntertainmentDate()
        if (hasTodayEntertainmentPlan()) return false
        val cleanLimit = dailyLimit.coerceIn(0, MAX_DAILY_ENTERTAINMENT_LIMIT)
        val cleanDurationMs = durationMinutes.coerceIn(MIN_ENTERTAINMENT_MINUTES, MAX_ENTERTAINMENT_MINUTES) * 60_000L
        prefs.edit()
            .putBoolean(KEY_DEFAULT_ENTERTAINMENT_CONFIGURED, true)
            .putInt(KEY_DEFAULT_ENTERTAINMENT_DAILY_LIMIT, cleanLimit)
            .putLong(KEY_DEFAULT_ENTERTAINMENT_DURATION_MS, cleanDurationMs)
            .putString(KEY_TODAY_QUOTA_DATE, today())
            .putBoolean(KEY_TODAY_QUOTA_CONFIRMED, true)
            .putInt(KEY_TODAY_QUOTA_DAILY_LIMIT, cleanLimit)
            .putLong(KEY_TODAY_QUOTA_DURATION_MS, cleanDurationMs)
            .putString(KEY_ENTERTAINMENT_DATE, today())
            .putInt(KEY_DAILY_ENTERTAINMENT_COUNT, 0)
            .putLong(KEY_ENTERTAINMENT_STARTED_AT, 0L)
            .putLong(KEY_ENTERTAINMENT_UNTIL, 0L)
            .remove(KEY_ENTERTAINMENT_PACKAGE)
            .putString(KEY_PROMPT_FLOW_STATE, PromptFlowState.TODAY_QUOTA_CONFIRMED.name)
            .remove(KEY_PROMPT_PACKAGE)
            .putLong(KEY_PROMPT_UPDATED_AT, System.currentTimeMillis())
            .apply()
        Log.i(
            FocusGateLog.TAG,
            "today quota confirmed date=${today()} todayLimit=$cleanLimit todayDurationMinutes=${cleanDurationMs / 60_000L} savedAsDefault=true"
        )
        return true
    }

    fun saveDefaultEntertainmentSettings(dailyLimit: Int, durationMinutes: Int): Boolean {
        val cleanLimit = dailyLimit.coerceIn(0, MAX_DAILY_ENTERTAINMENT_LIMIT)
        val cleanDurationMs = durationMinutes.coerceIn(MIN_ENTERTAINMENT_MINUTES, MAX_ENTERTAINMENT_MINUTES) * 60_000L
        prefs.edit()
            .putBoolean(KEY_DEFAULT_ENTERTAINMENT_CONFIGURED, true)
            .putInt(KEY_DEFAULT_ENTERTAINMENT_DAILY_LIMIT, cleanLimit)
            .putLong(KEY_DEFAULT_ENTERTAINMENT_DURATION_MS, cleanDurationMs)
            .apply()
        Log.i(
            FocusGateLog.TAG,
            "default entertainment saved defaultLimit=$cleanLimit defaultDurationMinutes=${cleanDurationMs / 60_000L} todayConfirmed=${hasTodayEntertainmentPlan()}"
        )
        return true
    }

    fun appDisplayName(packageName: String): String {
        TargetPlatform.entries.firstOrNull { it.packageName == packageName }?.let { return it.displayName }
        val packageManager = appContext.packageManager
        return runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
            packageManager.getApplicationLabel(info).toString().ifBlank { packageName }
        }.getOrDefault(packageName)
    }

    fun isGlobalGuardEnabled(): Boolean =
        prefs.getBoolean(KEY_DEV_TOTAL_GUARD_ENABLED, true)

    fun setGlobalGuardEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEV_TOTAL_GUARD_ENABLED, enabled).apply()
        Log.i(SearchGateDevModeLog.TAG, "globalGuardEnabled=$enabled")
    }

    fun debugUpdateTodayQuota(dailyLimit: Int, usedCount: Int, durationMinutes: Int, clearConfirmation: Boolean) {
        val cleanLimit = dailyLimit.coerceIn(0, MAX_DAILY_ENTERTAINMENT_LIMIT)
        val cleanUsed = usedCount.coerceIn(0, MAX_DAILY_ENTERTAINMENT_LIMIT)
        val cleanDurationMs = durationMinutes.coerceIn(MIN_ENTERTAINMENT_MINUTES, MAX_ENTERTAINMENT_MINUTES) * 60_000L
        val editor = prefs.edit()
            .putInt(KEY_DAILY_ENTERTAINMENT_COUNT, cleanUsed)
        if (clearConfirmation) {
            editor
                .remove(KEY_TODAY_QUOTA_DATE)
                .remove(KEY_TODAY_QUOTA_CONFIRMED)
                .remove(KEY_TODAY_QUOTA_DAILY_LIMIT)
                .remove(KEY_TODAY_QUOTA_DURATION_MS)
                .putString(KEY_PROMPT_FLOW_STATE, PromptFlowState.TODAY_QUOTA_UNSET.name)
        } else {
            editor
                .putString(KEY_TODAY_QUOTA_DATE, today())
                .putBoolean(KEY_TODAY_QUOTA_CONFIRMED, true)
                .putInt(KEY_TODAY_QUOTA_DAILY_LIMIT, cleanLimit)
                .putLong(KEY_TODAY_QUOTA_DURATION_MS, cleanDurationMs)
                .putString(KEY_PROMPT_FLOW_STATE, PromptFlowState.TODAY_QUOTA_CONFIRMED.name)
        }
        editor
            .remove(KEY_PROMPT_PACKAGE)
            .putLong(KEY_PROMPT_UPDATED_AT, System.currentTimeMillis())
            .apply()
        Log.i(
            SearchGateDevModeLog.TAG,
            "debugUpdateTodayQuota dailyLimit=$cleanLimit usedCount=$cleanUsed durationMinutes=${cleanDurationMs / 60_000L} clearConfirmation=$clearConfirmation debugBuild=true"
        )
    }

    fun debugResetDailyUsage() {
        prefs.edit()
            .putInt(KEY_DAILY_ENTERTAINMENT_COUNT, 0)
            .apply()
        Log.i(SearchGateDevModeLog.TAG, "debugResetDailyUsage")
    }

    fun debugClearEntertainmentSession() {
        endEntertainment(PromptFlowState.ENTERTAINMENT_ENDED_BY_USER)
        Log.i(SearchGateDevModeLog.TAG, "debugClearEntertainmentSession")
    }

    fun debugClearStudyLookupState() {
        clearSearchSession()
        Log.i(SearchGateDevModeLog.TAG, "debugClearStudyLookupState")
    }

    fun debugClearUserTargetLocks() {
        val configs = storedUserTargetConfigs()
            .map { it.copy(addedDate = null, canRemoveAfterDate = null) }
            .map { encodeTargetConfig(it) }
            .toSet()
        prefs.edit().putStringSet(KEY_USER_TARGET_CONFIGS, configs).apply()
        Log.i(SearchGateTargetAppLockLog.TAG, "debugClearUserTargetLocks")
    }

    fun debugClearUserAddedTargets() {
        val userPackages = storedUserTargetConfigs().map { it.packageName }.toSet()
        val remainingCandidates = candidatePackagesWithoutMigration().filter { it !in userPackages }.toSet()
        val remainingTargets = targetPackages().filter { it in DEFAULT_TARGET_PACKAGES }.toSet()
        val editor = prefs.edit()
            .remove(KEY_USER_TARGET_CONFIGS)
            .putStringSet(KEY_CANDIDATE_PACKAGES, remainingCandidates + DEFAULT_TARGET_PACKAGES)
            .putStringSet(KEY_TARGET_PACKAGES, remainingTargets + DEFAULT_TARGET_PACKAGES)
        if (hasTodayControlPlan()) {
            editor.putStringSet(
                KEY_DAILY_CONTROLLED_PACKAGES,
                todayControlledPackages().filter { it in DEFAULT_TARGET_PACKAGES }.toSet()
            )
        }
        editor.apply()
        Log.i(SearchGateTargetAppLockLog.TAG, "debugClearUserAddedTargets removed=$userPackages")
    }

    private fun builtInTargetConfig(platform: TargetPlatform, appType: TargetAppType): TargetAppConfig =
        TargetAppConfig(
            packageName = platform.packageName,
            appName = platform.displayName,
            appType = appType,
            isEnabled = targetPackages().contains(platform.packageName),
            allowStudyLookup = appType == TargetAppType.HYBRID_STUDY_ENTERTAINMENT,
            allowEntertainment = true,
            source = TargetAppSource.BUILT_IN
        )

    private fun storedUserTargetConfigs(): List<TargetAppConfig> =
        prefs.getStringSet(KEY_USER_TARGET_CONFIGS, emptySet()).orEmpty()
            .mapNotNull { decodeTargetConfig(it) }

    private fun saveUserTargetConfig(packageName: String, appName: String, enabled: Boolean) {
        if (packageName in DEFAULT_TARGET_PACKAGES) return
        val previous = storedUserTargetConfigs().firstOrNull { it.packageName == packageName }
        val todayDate = today()
        val tomorrowDate = LocalDate.now().plusDays(1).toString()
        val configs = storedUserTargetConfigs()
            .filterNot { it.packageName == packageName }
            .plus(
                TargetAppConfig(
                    packageName = packageName,
                    appName = appName,
                    appType = TargetAppType.ENTERTAINMENT_ONLY,
                    isEnabled = enabled,
                    allowStudyLookup = false,
                    allowEntertainment = true,
                    source = TargetAppSource.USER_ADDED
                ).withDailyLockIfNewlyEnabled(previous, enabled, todayDate, tomorrowDate)
            )
            .map { encodeTargetConfig(it) }
            .toSet()
        prefs.edit().putStringSet(KEY_USER_TARGET_CONFIGS, configs).apply()
    }

    private fun TargetAppConfig.withDailyLockIfNewlyEnabled(
        previous: TargetAppConfig?,
        finalEnabled: Boolean,
        todayDate: String,
        tomorrowDate: String
    ): TargetAppConfig {
        if (source != TargetAppSource.USER_ADDED) return this
        return when {
            finalEnabled && (previous == null || !previous.isEnabled) -> copy(
                addedDate = todayDate,
                canRemoveAfterDate = tomorrowDate
            )
            previous != null -> copy(
                addedDate = previous.addedDate,
                canRemoveAfterDate = previous.canRemoveAfterDate
            )
            else -> this
        }
    }

    private fun encodeTargetConfig(config: TargetAppConfig): String =
        listOf(
            config.packageName,
            config.appName.replace("|", " "),
            config.appType.name,
            config.isEnabled.toString(),
            config.allowStudyLookup.toString(),
            config.allowEntertainment.toString(),
            config.source.name,
            config.addedDate.orEmpty(),
            config.canRemoveAfterDate.orEmpty()
        ).joinToString("|")

    private fun decodeTargetConfig(raw: String): TargetAppConfig? {
        val parts = raw.split("|")
        if (parts.size < 7) return null
        val appType = runCatching { TargetAppType.valueOf(parts[2]) }.getOrNull() ?: return null
        val source = runCatching { TargetAppSource.valueOf(parts[6]) }.getOrDefault(TargetAppSource.USER_ADDED)
        return TargetAppConfig(
            packageName = parts[0],
            appName = parts[1].ifBlank { parts[0] },
            appType = appType,
            isEnabled = parts[3].toBooleanStrictOrNull() ?: false,
            allowStudyLookup = parts[4].toBooleanStrictOrNull() ?: false,
            allowEntertainment = parts[5].toBooleanStrictOrNull() ?: true,
            source = source,
            addedDate = parts.getOrNull(7)?.ifBlank { null },
            canRemoveAfterDate = parts.getOrNull(8)?.ifBlank { null }
        )
    }

    fun todayEntertainmentLimit(): Int =
        if (hasTodayEntertainmentPlan()) {
            prefs.getInt(KEY_TODAY_QUOTA_DAILY_LIMIT, 0)
        } else {
            0
        }

    fun todayEntertainmentDurationMs(): Long =
        if (hasTodayEntertainmentPlan()) {
            prefs.getLong(KEY_TODAY_QUOTA_DURATION_MS, 0L)
        } else {
            0L
        }

    fun defaultEntertainmentLimit(): Int =
        prefs.getInt(KEY_DEFAULT_ENTERTAINMENT_DAILY_LIMIT, DEFAULT_DAILY_ENTERTAINMENT_LIMIT)

    fun defaultEntertainmentDurationMs(): Long =
        prefs.getLong(KEY_DEFAULT_ENTERTAINMENT_DURATION_MS, DEFAULT_ENTERTAINMENT_MS)

    fun remainingEntertainmentMs(now: Long = System.currentTimeMillis()): Long =
        (prefs.getLong(KEY_ENTERTAINMENT_UNTIL, 0L) - now).coerceAtLeast(0L)

    fun entertainmentSessionStatus(
        targetPackage: String,
        nowMillis: Long = System.currentTimeMillis()
    ): EntertainmentSessionStatus {
        normalizeEntertainmentDate()
        val entertainmentStartAtMillis = prefs.getLong(KEY_ENTERTAINMENT_STARTED_AT, 0L)
        val entertainmentEndAtMillis = prefs.getLong(KEY_ENTERTAINMENT_UNTIL, 0L)
        val sessionPackage = prefs.getString(KEY_ENTERTAINMENT_PACKAGE, null)
        val entertainmentDurationMillis = (entertainmentEndAtMillis - entertainmentStartAtMillis).coerceAtLeast(0L)
        val remainingMillis = (entertainmentEndAtMillis - nowMillis).coerceAtLeast(0L)
        val anomaly = when {
            sessionPackage == targetPackage && entertainmentEndAtMillis <= 0L -> "endTime missing"
            sessionPackage != null && entertainmentEndAtMillis > 0L && entertainmentStartAtMillis <= 0L -> "startTime missing"
            sessionPackage != null && entertainmentEndAtMillis < entertainmentStartAtMillis -> "endTime before startTime"
            sessionPackage != null && entertainmentEndAtMillis > 0L && nowMillis >= entertainmentEndAtMillis -> "expired; clearing persisted session"
            sessionPackage != null && entertainmentDurationMillis in 1L until 1_000L -> "duration suspiciously small; possible seconds/millis mix"
            sessionPackage != null && sessionPackage != targetPackage && nowMillis < entertainmentEndAtMillis -> "active package mismatch"
            else -> null
        }
        if (sessionPackage != null && entertainmentEndAtMillis > 0L && nowMillis >= entertainmentEndAtMillis) {
            normalizeExpiredEntertainment(nowMillis)
        }
        return EntertainmentSessionStatus(
            entertainmentStartAtMillis = entertainmentStartAtMillis,
            entertainmentEndAtMillis = entertainmentEndAtMillis,
            entertainmentDurationMillis = entertainmentDurationMillis,
            nowMillis = nowMillis,
            remainingMillis = remainingMillis,
            packageName = sessionPackage,
            targetPackage = targetPackage,
            isEntertainmentActive = sessionPackage == targetPackage && entertainmentEndAtMillis > nowMillis,
            anomaly = anomaly
        )
    }

    fun isEntertainmentActive(targetPackage: String, nowMillis: Long = System.currentTimeMillis()): Boolean =
        entertainmentSessionStatus(targetPackage, nowMillis).isEntertainmentActive

    fun entertainmentControlPosition(defaultX: Int, defaultY: Int, maxX: Int, maxY: Int): Pair<Int, Int> {
        val x = prefs.getInt(KEY_ENTERTAINMENT_CONTROL_X, defaultX).coerceIn(0, maxX.coerceAtLeast(0))
        val y = prefs.getInt(KEY_ENTERTAINMENT_CONTROL_Y, defaultY).coerceIn(0, maxY.coerceAtLeast(0))
        return x to y
    }

    fun saveEntertainmentControlPosition(x: Int, y: Int) {
        prefs.edit()
            .putInt(KEY_ENTERTAINMENT_CONTROL_X, x)
            .putInt(KEY_ENTERTAINMENT_CONTROL_Y, y)
            .apply()
        Log.i(FocusGateLog.TAG, "entertainment control position saved x=$x y=$y")
    }

    fun isModuleExpanded(moduleKey: String, defaultExpanded: Boolean): Boolean =
        prefs.getBoolean("$KEY_MODULE_EXPANDED_PREFIX$moduleKey", defaultExpanded)

    fun setModuleExpanded(moduleKey: String, expanded: Boolean) {
        prefs.edit().putBoolean("$KEY_MODULE_EXPANDED_PREFIX$moduleKey", expanded).apply()
    }

    fun markPromptShowing(packageName: String, now: Long = System.currentTimeMillis()) {
        markPromptState(PromptFlowState.PROMPT_SHOWING, packageName, now)
    }

    fun markSearchRequested(packageName: String, now: Long = System.currentTimeMillis()) {
        markPromptState(PromptFlowState.SEARCH_REQUESTED, packageName, now)
    }

    fun markPromptClosedByAction(packageName: String?, now: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putString(KEY_PROMPT_FLOW_STATE, PromptFlowState.PROMPT_CLOSED_BY_ACTION.name)
            .apply {
                if (packageName == null) remove(KEY_PROMPT_PACKAGE) else putString(KEY_PROMPT_PACKAGE, packageName)
            }
            .putLong(KEY_PROMPT_UPDATED_AT, now)
            .apply()
    }

    fun clearPromptState(now: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putString(KEY_PROMPT_FLOW_STATE, PromptFlowState.IDLE.name)
            .remove(KEY_PROMPT_PACKAGE)
            .putLong(KEY_PROMPT_UPDATED_AT, now)
            .apply()
    }

    fun isSearchReturnCheckActive(packageName: String, now: Long = System.currentTimeMillis()): Boolean =
        prefs.getString(KEY_SEARCH_RETURN_CHECK_PACKAGE, null) == packageName &&
            now <= prefs.getLong(KEY_SEARCH_RETURN_CHECK_UNTIL, 0L)

    fun clearSearchReturnCheck() {
        prefs.edit()
            .remove(KEY_SEARCH_RETURN_CHECK_PACKAGE)
            .remove(KEY_SEARCH_RETURN_CHECK_UNTIL)
            .apply()
    }

    fun markSearchReturnCheck(packageName: String, now: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putString(KEY_SEARCH_RETURN_CHECK_PACKAGE, packageName)
            .putLong(KEY_SEARCH_RETURN_CHECK_UNTIL, now + SEARCH_RETURN_CHECK_TTL_MS)
            .putString(KEY_PROMPT_FLOW_STATE, PromptFlowState.RETURNED_FROM_SEARCH.name)
            .putString(KEY_PROMPT_PACKAGE, packageName)
            .putLong(KEY_PROMPT_UPDATED_AT, now)
            .apply()
        Log.i(FocusGateLog.TAG, "search return check armed package=$packageName until=${now + SEARCH_RETURN_CHECK_TTL_MS}")
    }

    fun logDecisionSnapshot(packageName: String, pageKind: PageKind?, now: Long = System.currentTimeMillis()) {
        val state = state()
        val dailyLimit = todayEntertainmentLimit()
        val durationMinutes = todayEntertainmentDurationMs() / 60_000L
        val used = state.dailyEntertainmentCount
        Log.i(
            FocusGateLog.TAG,
            "decision date=${today()} package=$packageName page=$pageKind isTarget=${state.targetPackages.contains(packageName)} isXhs=${packageName == TargetPlatform.XHS.packageName} todayQuotaConfirmed=${state.todayQuotaConfirmed} promptState=${state.promptFlowState} promptShowingPackage=${state.promptPackage} todayLimit=$dailyLimit todayDurationMinutes=$durationMinutes defaultLimit=${state.defaultEntertainmentDailyLimit} defaultDurationMinutes=${state.defaultEntertainmentDurationMs / 60_000L} usedCount=$used remaining=${(dailyLimit - used).coerceAtLeast(0)} entertainmentActive=${TimePolicy.isWithinEntertainmentWindow(state, packageName, now)} entertainmentStartedAt=${state.entertainmentStartedAt} entertainmentUntil=${state.entertainmentUntil} searchSession=${state.searchSessionPackage}/${state.lastDeepLinkResult} returnCheckActive=${isSearchReturnCheckActive(packageName, now)} resetDate=${state.entertainmentDate}"
        )
    }

    private fun normalizeEntertainmentDate() {
        val current = today()
        if (prefs.getString(KEY_ENTERTAINMENT_DATE, null) != current) {
            prefs.edit()
                .putString(KEY_ENTERTAINMENT_DATE, current)
                .putInt(KEY_DAILY_ENTERTAINMENT_COUNT, 0)
                .putLong(KEY_ENTERTAINMENT_STARTED_AT, 0L)
                .putLong(KEY_ENTERTAINMENT_UNTIL, 0L)
                .remove(KEY_ENTERTAINMENT_PACKAGE)
                .remove(KEY_TODAY_QUOTA_DATE)
                .remove(KEY_TODAY_QUOTA_CONFIRMED)
                .remove(KEY_TODAY_QUOTA_DAILY_LIMIT)
                .remove(KEY_TODAY_QUOTA_DURATION_MS)
                .putString(KEY_PROMPT_FLOW_STATE, PromptFlowState.TODAY_QUOTA_UNSET.name)
                .remove(KEY_PROMPT_PACKAGE)
                .putLong(KEY_PROMPT_UPDATED_AT, System.currentTimeMillis())
                .apply()
            Log.i(
                FocusGateLog.TAG,
                "entertainment date reset currentDate=$current resetTodayQuota=true resetUsage=true resetDefault=false defaultLimit=${defaultEntertainmentLimit()} defaultDurationMinutes=${defaultEntertainmentDurationMs() / 60_000L}"
            )
        }
    }

    private fun normalizeDailyControlDate() {
        val current = today()
        if (prefs.getString(KEY_DAILY_CONTROL_PLAN_DATE, null) != current) {
            prefs.edit()
                .remove(KEY_DAILY_CONTROL_PLAN_DATE)
                .remove(KEY_DAILY_CONTROLLED_PACKAGES)
                .apply()
        }
    }

    private fun normalizeSearchSession(now: Long = System.currentTimeMillis()) {
        if (prefs.getString(KEY_SEARCH_SESSION_PACKAGE, null) == null) return
        val graceUntil = prefs.getLong(KEY_SEARCH_GRACE_UNTIL, 0L)
        val startedAt = prefs.getLong(KEY_SEARCH_STARTED_AT, graceUntil - SEARCH_GRACE_MS).coerceAtLeast(0L)
        val expiresAt = maxOf(graceUntil, startedAt + SEARCH_SESSION_TTL_MS)
        if (now > expiresAt) {
            prefs.edit()
                .putLong(KEY_SEARCH_GRACE_UNTIL, 0L)
                .remove(KEY_SEARCH_SESSION_PACKAGE)
                .remove(KEY_SEARCH_STARTED_AT)
                .remove(KEY_SEARCH_RETURN_CHECK_PACKAGE)
                .remove(KEY_SEARCH_RETURN_CHECK_UNTIL)
                .putString(KEY_LAST_DEEP_LINK_RESULT, DeepLinkResult.NOT_STARTED.name)
                .apply()
        }
    }

    private fun normalizeExpiredEntertainment(now: Long = System.currentTimeMillis()) {
        val until = prefs.getLong(KEY_ENTERTAINMENT_UNTIL, 0L)
        val packageName = prefs.getString(KEY_ENTERTAINMENT_PACKAGE, null)
        if (packageName != null && until > 0L && now >= until) {
            prefs.edit()
                .putLong(KEY_ENTERTAINMENT_STARTED_AT, 0L)
                .putLong(KEY_ENTERTAINMENT_UNTIL, 0L)
                .remove(KEY_ENTERTAINMENT_PACKAGE)
                .putString(KEY_PROMPT_FLOW_STATE, PromptFlowState.ENTERTAINMENT_ENDED_NATURALLY.name)
                .remove(KEY_PROMPT_PACKAGE)
                .putLong(KEY_PROMPT_UPDATED_AT, now)
                .apply()
            Log.i(FocusGateLog.TAG, "entertainment expired package=$packageName until=$until now=$now")
        }
    }

    private fun migrateEntertainmentQuotaModel() {
        if (prefs.getBoolean(KEY_QUOTA_MODEL_MIGRATED, false)) return

        val legacyConfigured = prefs.getBoolean(KEY_ENTERTAINMENT_SETTINGS_CONFIGURED, false) ||
            prefs.getString(KEY_ENTERTAINMENT_PLAN_DATE, null) != null
        val legacyLimit = prefs.getInt(KEY_ENTERTAINMENT_DAILY_LIMIT, DEFAULT_DAILY_ENTERTAINMENT_LIMIT)
        val legacyDuration = prefs.getLong(KEY_ENTERTAINMENT_DURATION_MS, DEFAULT_ENTERTAINMENT_MS)
        val legacyPlanDate = prefs.getString(KEY_ENTERTAINMENT_PLAN_DATE, null)
        val current = today()

        val editor = prefs.edit()
            .putBoolean(KEY_QUOTA_MODEL_MIGRATED, true)

        if (legacyConfigured) {
            editor
                .putBoolean(KEY_DEFAULT_ENTERTAINMENT_CONFIGURED, true)
                .putInt(KEY_DEFAULT_ENTERTAINMENT_DAILY_LIMIT, legacyLimit)
                .putLong(KEY_DEFAULT_ENTERTAINMENT_DURATION_MS, legacyDuration)
            if (legacyPlanDate == current) {
                editor
                    .putString(KEY_TODAY_QUOTA_DATE, current)
                    .putBoolean(KEY_TODAY_QUOTA_CONFIRMED, true)
                    .putInt(KEY_TODAY_QUOTA_DAILY_LIMIT, legacyLimit)
                    .putLong(KEY_TODAY_QUOTA_DURATION_MS, legacyDuration)
            }
        }

        editor.apply()
        Log.i(
            FocusGateLog.TAG,
            "quota model migrated legacyConfigured=$legacyConfigured legacyPlanDate=$legacyPlanDate today=${today()} defaultLimit=${defaultEntertainmentLimit()} defaultDurationMinutes=${defaultEntertainmentDurationMs() / 60_000L} todayConfirmed=${hasTodayEntertainmentPlan()}"
        )
    }

    private fun markPromptState(state: PromptFlowState, packageName: String?, now: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putString(KEY_PROMPT_FLOW_STATE, state.name)
            .apply {
                if (packageName == null) remove(KEY_PROMPT_PACKAGE) else putString(KEY_PROMPT_PACKAGE, packageName)
            }
            .putLong(KEY_PROMPT_UPDATED_AT, now)
            .apply()
        Log.i(FocusGateLog.TAG, "prompt state=$state package=$packageName")
    }

    private fun today(): String = LocalDate.now().toString()

    private fun usesRepeatedScrollGuard(packageName: String): Boolean =
        packageName == TargetPlatform.DOUYIN.packageName || packageName == TargetPlatform.XHS.packageName

    private fun scrollInterventionKey(packageName: String): String =
        "$KEY_SCROLL_INTERVENTION_UNTIL_PREFIX$packageName"

    companion object {
        const val SEARCH_GRACE_MS = 30_000L
        const val DEEP_LINK_VERIFY_MS = 3_000L
        const val SEARCH_SESSION_TTL_MS = 30 * 60_000L
        const val SEARCH_RETURN_CHECK_TTL_MS = 30 * 60_000L
        const val SEARCH_RETURN_UNKNOWN_RECHECK_MS = 1_500L
        const val DEFAULT_ENTERTAINMENT_MS = 5 * 60_000L
        const val DEFAULT_DAILY_ENTERTAINMENT_LIMIT = 2
        const val MIN_ENTERTAINMENT_MINUTES = 1
        const val MAX_ENTERTAINMENT_MINUTES = 60
        const val MAX_DAILY_ENTERTAINMENT_LIMIT = 10

        private const val PREFS_NAME = "focus_gate_guard"
        private const val KEY_TARGET_PACKAGES = "targetPackages"
        private const val KEY_CANDIDATE_PACKAGES = "candidatePackages"
        private const val KEY_USER_TARGET_CONFIGS = "userTargetConfigsV1"
        private const val KEY_TARGET_DEFAULTS_VERSION = "targetDefaultsVersion"
        private const val KEY_DAILY_CONTROL_PLAN_DATE = "dailyControlPlanDate"
        private const val KEY_DAILY_CONTROLLED_PACKAGES = "dailyControlledPackages"
        private const val KEY_SCROLL_INTERVENTION_UNTIL_PREFIX = "scrollInterventionUntil_"
        private const val KEY_REPEATED_SCROLL_THRESHOLD = "repeatedScrollThreshold"
        private const val KEY_BILI_RECOMMENDATION_JUMP_LIMIT = "biliRecommendationJumpLimit"
        private const val KEY_GUARD_SETTINGS_DATE = "guardSettingsDate"
        private const val KEY_SEARCH_GRACE_UNTIL = "searchGraceUntil"
        private const val KEY_SEARCH_SESSION_PACKAGE = "searchSessionPackage"
        private const val KEY_SEARCH_STARTED_AT = "searchStartedAt"
        private const val KEY_ENTERTAINMENT_UNTIL = "entertainmentUntil"
        private const val KEY_ENTERTAINMENT_STARTED_AT = "entertainmentStartedAt"
        private const val KEY_ENTERTAINMENT_PACKAGE = "entertainmentPackage"
        private const val KEY_ENTERTAINMENT_DATE = "entertainmentDate"
        private const val KEY_ENTERTAINMENT_PLAN_DATE = "entertainmentPlanDate"
        private const val KEY_ENTERTAINMENT_SETTINGS_CONFIGURED = "entertainmentSettingsConfigured"
        private const val KEY_ENTERTAINMENT_DAILY_LIMIT = "entertainmentDailyLimit"
        private const val KEY_ENTERTAINMENT_DURATION_MS = "entertainmentDurationMs"
        private const val KEY_QUOTA_MODEL_MIGRATED = "quotaModelMigratedV3"
        private const val KEY_DEFAULT_ENTERTAINMENT_CONFIGURED = "defaultEntertainmentConfigured"
        private const val KEY_DEFAULT_ENTERTAINMENT_DAILY_LIMIT = "defaultEntertainmentDailyLimit"
        private const val KEY_DEFAULT_ENTERTAINMENT_DURATION_MS = "defaultEntertainmentDurationMs"
        private const val KEY_TODAY_QUOTA_DATE = "todayQuotaDate"
        private const val KEY_TODAY_QUOTA_CONFIRMED = "todayQuotaConfirmed"
        private const val KEY_TODAY_QUOTA_DAILY_LIMIT = "todayQuotaDailyLimit"
        private const val KEY_TODAY_QUOTA_DURATION_MS = "todayQuotaDurationMs"
        private const val KEY_DAILY_ENTERTAINMENT_COUNT = "dailyEntertainmentCount"
        private const val KEY_LAST_TARGET_PACKAGE = "lastTargetPackage"
        private const val KEY_LAST_KEYWORD = "lastKeyword"
        private const val KEY_LAST_PLATFORM = "lastPlatform"
        private const val KEY_LAST_DEEP_LINK_RESULT = "lastDeepLinkResult"
        private const val KEY_LAST_INTERCEPT_TIME = "lastInterceptTime"
        private const val KEY_PROMPT_FLOW_STATE = "promptFlowState"
        private const val KEY_PROMPT_PACKAGE = "promptPackage"
        private const val KEY_PROMPT_UPDATED_AT = "promptUpdatedAt"
        private const val KEY_SEARCH_RETURN_CHECK_PACKAGE = "searchReturnCheckPackage"
        private const val KEY_SEARCH_RETURN_CHECK_UNTIL = "searchReturnCheckUntil"
        private const val KEY_ENTERTAINMENT_CONTROL_X = "entertainmentControlX"
        private const val KEY_ENTERTAINMENT_CONTROL_Y = "entertainmentControlY"
        private const val KEY_MODULE_EXPANDED_PREFIX = "moduleExpanded_"
        private const val KEY_DEV_TOTAL_GUARD_ENABLED = "devTotalGuardEnabled"
        private const val TARGET_DEFAULTS_VERSION = 2
        private const val SCROLL_INTERVENTION_COOLDOWN_MS = 60_000L
        const val DEFAULT_REPEATED_SCROLL_THRESHOLD = 3
        const val MIN_REPEATED_SCROLL_THRESHOLD = 2
        const val MAX_REPEATED_SCROLL_THRESHOLD = 12
        const val DEFAULT_BILI_RECOMMENDATION_JUMP_LIMIT = 2
        const val MIN_BILI_RECOMMENDATION_JUMP_LIMIT = 1
        const val MAX_BILI_RECOMMENDATION_JUMP_LIMIT = 8

        val DEFAULT_TARGET_PACKAGES = setOf(
            TargetPlatform.XHS.packageName,
            TargetPlatform.BILI.packageName,
            TargetPlatform.DOUYIN.packageName
        )
    }
}
