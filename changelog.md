# 检索门 SearchGate Changelog

## v0.5.0_20260708_App分类应用勾选开发者模式

### 本版目标

本轮不继续投入抖音资料查询返回首页问题的修复成本，而是调整产品逻辑：将目标 App 分为“资料 + 娱乐”与“纯限制”两类，抖音改为纯限制类；同时把用户新增 App 从手动输入包名改为系统已安装应用勾选，并增加 Debug build 开发者调试模式。

### 新增内容

- 新增 `TargetAppConfig`、`TargetAppType`、`TargetAppSource`、`InstalledAppInfo`。
- 新增目标 App 类型日志 `SearchGateAppTypeDebug`。
- 新增应用选择器日志 `SearchGateAppPickerDebug`。
- 新增开发者模式日志 `SearchGateDevModeDebug`。
- 新增系统 Launcher 应用读取和勾选保存逻辑。
- 新增 Debug build 隐藏开发者模式：连续点击 `FocusGate` 标题 5 次后输入密钥。

### 修改内容

- Android 工程 `versionCode` 从 `6` 更新为 `7`。
- Android 工程 `versionName` 从 `0.4.1` 更新为 `0.5.0`。
- 抖音 `com.ss.android.ugc.aweme` 改为 `ENTERTAINMENT_ONLY`，不再显示“资料查找”入口。
- 用户新增 App 默认保存为 `ENTERTAINMENT_ONLY`。
- 询问页根据 `TargetAppConfig` 决定是否展示“资料查找”按钮。
- `KeywordActivity` 和 `DeepLinkLauncher` 增加纯限制类兜底，阻止抖音资料查询深链。
- 无障碍服务会清理纯限制类 App 的旧资料查询豁免状态。
- 主界面“目标应用”模块改为应用列表勾选，手动包名入口降级为高级调试用途。
- `AndroidManifest.xml` 增加 Launcher intent query，用于 Android 11+ 包可见性下读取可启动应用。

### 删除内容

- 未删除旧版本。
- 未删除小红书、Bilibili 的资料查找逻辑。
- 未删除娱乐额度快照、娱乐倒计时通知、悬浮圆圈、前台服务或 Bilibili 专项日志。

### 构建与测试

- 已在 `current` 下运行：

```powershell
.\gradlew.bat assembleDebug
```

- 构建结果：成功。
- APK：`current/app/build/outputs/apk/debug/app-debug.apk`。
- APK 元数据：`versionCode=7`，`versionName=0.5.0`。
- 未完成真实手机端完整手动回归；需要验证小红书/Bilibili 仍显示资料查找，抖音和用户新增 App 不显示资料查找。

### 已知问题

- 固定开发者密钥安全性弱，只能用于本地 Debug 构建。
- Android 11+ 包可见性可能导致部分非 Launcher 应用不显示。
- 抖音旧资料查询状态机仍保留为兼容清理路径，后续可单独清理。
- 厂商 ROM 仍可能限制无障碍服务、前台服务和悬浮窗。

### 回滚说明

如需恢复上一版：

1. 备份当前 `current` 为 `current_backup_before_rollback_YYYYMMDD_HHMMSS`。
2. 将 `versions/v0.4.1_20260707_娱乐计时抖音悬浮性能` 中的工程文件复制到 `current`。
3. 不复制 `.gradle`、`app/build` 缓存。
4. 回滚后重新构建并追加 changelog 记录。

## v0.1.0_20260706_引入版本管理前原始快照

### 本版目标

保存引入版本管理前的原始 Android 工程状态，建立后续版本归档和回档基准。

### 新增内容

- 创建 `versions` 目录。
- 创建 `current` 目录，并将当前 Android 工程复制为后续开发入口。
- 创建本版本归档目录 `versions/v0.1.0_20260706_引入版本管理前原始快照`。
- 创建版本说明文件 `VERSION.md` 和 `README.md`。
- 创建根目录版本规则文档 `README_VERSION_RULES.md`。

### 修改内容

- 将原本直接位于项目根目录的 Android 工程迁移到 `current`。
- 版本归档和 `current` 均排除 `.gradle`、`app/build` 等本地缓存。

### 删除内容

- 未删除任何 `versions` 旧版本。
- 已清理根目录旧工程副本，避免根目录和 `current` 同时作为开发入口。
- 被清理的旧工程内容已保存在 `versions/v0.1.0_20260706_引入版本管理前原始快照` 和 `current` 中。

### 当前功能状态

- Android Compose 应用，包名 `com.example.focusgate`。
- 支持小红书、B 站、抖音检测。
- 支持悬浮窗拦截、资料查找、每日控制策略、娱乐次数和时长设置。
- 支持连续上滑守卫和 B 站推荐详情连跳守卫。

### 已知问题

- 搜索页连续滑动守卫的完整自动化实机链路此前受手机输入法和系统限制影响，未能完全自动化验证。
- 目标 App 页面识别依赖无障碍节点，存在随目标 App 更新而失效的风险。

### 构建与测试

- 已在 `current` 下运行：

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
```

- 构建结果：成功。
- 本次任务未继续做业务功能开发。

### 回档说明

如需恢复本版本：

1. 备份当前 `current` 为 `current_backup_before_rollback_YYYYMMDD_HHMMSS`。
2. 将 `versions/v0.1.0_20260706_引入版本管理前原始快照` 中的工程文件复制到 `current`。
3. 不复制 `.gradle`、`app/build` 缓存。
4. 回档后重新构建并在 `changelog.md` 追加回档记录。

## v0.1.1_20260706_新增接手与汇报规则

### 本版目标

补齐后续 AI 接手机制，明确每轮汇报必须带版本号，并确保每轮更新后旧版本继续保留。

### 新增内容

- 新增项目根目录 `AGENTS.md`，作为后续 AI 接手项目的强制入口。
- 新增项目根目录 `CURRENT_STATUS.md`，记录当前版本、开发入口、功能概况和已知风险。
- 新增 `current/AGENTS.md`，提醒进入 Android 工程的接手者先阅读根目录规则。
- 在版本规则中新增“接手机制”和“汇报规则”。

### 修改内容

- 将 Android 工程 `versionCode` 从 `1` 更新为 `2`。
- 将 Android 工程 `versionName` 从 `0.1.0` 更新为 `0.1.1`。

### 删除内容

- 无。

### 已知问题

- 新对话不能自动继承旧聊天上下文；本版只能通过项目内固定文件让后续 AI 快速接手，不能保证对方完全不读文件就知道状态。
- PowerShell 默认编码显示中文 Markdown 时可能出现乱码；后续接手应优先使用支持 UTF-8 的编辑器或命令读取。

### 构建与测试

- 已在 `current` 下运行：

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
```

- 构建结果：成功。
- 本轮是版本机制和文档规则更新，没有执行手机端业务流程测试。

### 回档说明

如需恢复本版本：

1. 备份当前 `current` 为 `current_backup_before_rollback_YYYYMMDD_HHMMSS`。
2. 将 `versions/v0.1.1_20260706_新增接手与汇报规则` 中的工程文件复制到 `current`。
3. 不复制 `.gradle`、`app/build` 缓存。
4. 回档后重新构建并追加 changelog 记录。

## v0.4.1_20260707_娱乐计时抖音悬浮性能

### 本版目标

只聚焦三个高优先级 bug：娱乐倒计时提前失效、抖音资料查询返回首页不弹窗、娱乐圆形悬浮窗拖动不跟手。

### 新增内容

- 新增 `SearchGateTimerDebug` 日志 tag。
- 新增 `SearchGateFloatPerf` 日志 tag。
- 新增 `EntertainmentSessionStatus`，统一持久化娱乐状态读取。
- 扩展抖音状态：`DOUYIN_VIDEO_FROM_SEARCH`、`DOUYIN_RECHECK_PENDING`、`DOUYIN_PROMPT_SHOWING`、`DOUYIN_ENTERTAINMENT_ACTIVE`、`DOUYIN_COOLDOWN`、`DOUYIN_QUOTA_EXHAUSTED`。
- 抖音返回复查增加第 4 次 2500ms 复查。
- 悬浮圆圈拖动使用 Choreographer 每帧最多更新一次窗口位置。

### 修改内容

- Android 工程 `versionCode` 从 `5` 更新为 `6`。
- Android 工程 `versionName` 从 `0.4.0` 更新为 `0.4.1`。
- `FocusAccessibilityService.showIntercept()` 增加最终娱乐状态保护，娱乐 active 时禁止实际弹窗。
- 连续滑动守卫在娱乐 active 时不再结束娱乐状态。
- 抖音页面识别增加 `homeScore/searchScore/detailScore` 调试信号。
- 抖音首页推荐流优先识别为 `DOUYIN_HOME_RECOMMEND`，避免被当成资料查询视频详情。
- 悬浮圆圈长按进度延迟启动；拖动开始后取消长按并按帧更新位置。

### 删除内容

- 未删除旧版本。
- 未删除额度快照、娱乐倒计时通知、Bilibili 日志、小红书流程或主界面模块化 UI。

### 构建与测试

- 已在 `current` 下运行：

```powershell
.\gradlew.bat assembleDebug
```

- 构建结果：成功。
- APK：`current/app/build/outputs/apk/debug/app-debug.apk`。
- APK 元数据：`versionCode=6`，`versionName=0.4.1`。
- 未完成真实手机端完整手动回归；需要用 logcat 验证 `SearchGateTimerDebug`、`SearchGateDouyinDebug`、`SearchGateFloatPerf`。

### 已知问题

- 抖音首页识别仍依赖无障碍节点和 Activity 名称，实机样本不足时仍可能误判。
- 如果抖音搜索结果视频也运行在 `.main.` Activity 且缺少搜索特征，可能被误判为返回首页。
- Android 厂商 ROM 仍可能回收无障碍服务或限制前台服务，App 不能 100% 保证后台不被杀。

### 回档说明

如需恢复上一版：

1. 备份当前 `current` 为 `current_backup_before_rollback_YYYYMMDD_HHMMSS`。
2. 将 `versions/v0.4.0_20260706_悬浮圆圈模块UI抖音复查` 中的工程文件复制到 `current`。
3. 不复制 `.gradle`、`app/build` 缓存。
4. 回档后重新构建并追加 changelog 记录。

## v0.4.0_20260706_悬浮圆圈模块UI抖音复查

### 本版目标

在 `v0.3.0_20260706_额度通知B站排查` 基础上继续迭代：缩小娱乐结束控件、改造主界面为模块化可收缩 UI，并重点排查抖音从 FocusGate 资料查询流程返回首页后不弹询问页的问题。

### 新增内容

- 新增 `EntertainmentEndCircleView`，用于娱乐状态下的小型“结束”圆形悬浮控件。
- 新增悬浮圆圈位置持久化：`entertainmentControlX`、`entertainmentControlY`。
- 新增主界面模块收缩状态持久化：`moduleExpanded_*`。
- 新增 `DouyinPageState`。
- 新增 `SearchGateDouyinDebug` 日志 tag。
- 新增抖音 300ms / 800ms / 1500ms 有限返回复查。

### 修改内容

- `OverlayController.showEntertainmentControl()` 从大块 `LinearLayout + Button + ProgressBar` 改为 60dp 可拖动圆形悬浮控件。
- 主界面增加 `CollapsibleSection`，拆分权限、目标应用、今日额度、当前状态、调试日志和高级设置。
- `PageDetector` 增加抖音页面状态识别。
- `FocusAccessibilityService` 将抖音资料查询返回流程从通用 search return check 中分离，避免资料查询豁免长期残留。
- 抖音 debounce 分支增加专项日志，避免“被防抖吞掉”时无排查依据。
- Android 版本号更新为 `versionCode=5`、`versionName=0.4.0`。

### 删除内容

- 未删除历史版本。
- 未删除额度快照、娱乐倒计时通知、Bilibili 专项日志或小红书状态机逻辑。

### 构建与测试

- 已在 `current` 下运行：

```powershell
.\gradlew.bat assembleDebug
```

- 构建结果：成功。
- 生成 APK：`current\app\build\outputs\apk\debug\app-debug.apk`。
- 本轮未连接真实手机执行完整手动回归；抖音页面状态仍需通过 `SearchGateDouyinDebug` 实机日志校准。

### 已知问题

- 抖音页面识别依赖无障碍节点文本与控件类名，目标 App 更新或不同版本 UI 会影响准确性。
- 最终 unknown 状态不会盲弹，会清除资料查询豁免等待后续事件；极端情况下仍可能需要用户再次触发一次页面变化。
- Android 厂商 ROM 仍可能回收无障碍服务或限制前台服务，App 不能 100% 保证后台不被杀。

### 回档说明

如需恢复上一版：

1. 备份当前 `current` 为 `current_backup_before_rollback_YYYYMMDD_HHMMSS`。
2. 将 `versions/v0.3.0_20260706_额度通知B站排查` 中的工程文件复制到 `current`。
3. 不复制 `.gradle`、`app/build` 缓存。
4. 回档后重新构建并追加 changelog 记录。

## v0.3.0_20260706_额度通知B站排查

### 本版目标

继续修复 `v0.2.0` 后的逻辑漏洞：当天额度可被主界面修改绕过、权限入口常态展示、娱乐通知缺少倒计时、结束娱乐误触、Bilibili 推荐连跳缺少来源状态和专项日志。

### 新增内容

- 新增 `BiliPageState` 和 `BiliRecommendationDecision`。
- 新增 `SearchGateBiliDebug` 日志 tag。
- 新增当天额度快照 key：`todayQuotaDate`、`todayQuotaConfirmed`、`todayQuotaDailyLimit`、`todayQuotaDurationMs`。
- 新增长期默认额度 key：`defaultEntertainmentDailyLimit`、`defaultEntertainmentDurationMs`。
- 新增娱乐开始时间 `entertainmentStartedAt`。
- 新增长按结束娱乐进度反馈。

### 修改内容

- Android 工程 `versionCode` 从 `3` 更新为 `4`。
- Android 工程 `versionName` 从 `0.2.0` 更新为 `0.3.0`。
- 执行逻辑只读取当天额度快照，不再直接读取长期默认额度。
- 主界面按今日额度状态切换权限入口展示。
- 娱乐倒计时整合进已有 `StabilityForegroundService` 前台服务通知。
- Bilibili 推荐连跳从单纯详情签名计数改为页面状态和来源状态判断。

### 删除内容

- 未删除旧版本。
- 未删除核心拦截、资料查找、娱乐限制或权限恢复入口。

### 构建与测试

- 已在 `current` 下运行：

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
```

- 构建结果：成功。
- APK：`current/app/build/outputs/apk/debug/app-debug.apk`。
- APK 元数据：`versionCode=4`，`versionName=0.3.0`。
- 未完成手机端业务流程、Bilibili 多页面和跨天实测。

### 回档说明

如需恢复上一版本：

1. 备份当前 `current` 为 `current_backup_before_rollback_YYYYMMDD_HHMMSS`。
2. 将 `versions/v0.2.0_20260706_后台配置询问流程修复` 中的工程文件复制到 `current`。
3. 不复制 `.gradle`、`app/build` 缓存。
4. 回档后重新构建并追加 changelog 记录。

## v0.2.0_20260706_后台配置询问流程修复

### 本版目标

针对后台稳定性、娱乐配置持久化、小红书询问页闪烁、小红书资料查找返回漏弹窗四个已观测问题做 Debug 迭代。

### 新增内容

- 新增项目根目录 `DEBUG_REPORT.md`。
- 新增 `PromptFlowState`，记录询问页、资料查找、娱乐放行等流程状态。
- 新增 `StabilityForegroundService`，通过可见且可停止的前台通知提高进程稳定性。
- 新增通知权限检测和主界面通知权限入口。
- 新增主界面长期娱乐额度设置入口。

### 修改内容

- Android 工程 `versionCode` 从 `2` 更新为 `3`。
- Android 工程 `versionName` 从 `0.1.1` 更新为 `0.2.0`。
- 统一日志 tag 为 `SearchGateDebug`。
- 将娱乐次数和单次时长改为长期用户配置，跨天只重置当天使用记录。
- 调整无障碍服务决策顺序，资料返回复查可绕过普通拦截防抖。
- 自家悬浮窗获得焦点时不再误删询问页。
- 小红书已验证搜索会话返回 UNKNOWN 时不再长期刷新放行，而是进入复查和重新弹窗路径。

### 删除内容

- 未删除旧版本。
- 未删除核心拦截、资料查找或娱乐限制逻辑。

### 构建与测试

- 已在 `current` 下运行：

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
```

- 构建结果：成功。
- APK：`current/app/build/outputs/apk/debug/app-debug.apk`。
- 已完成静态版本号、APK 元数据、核心日志点检查。
- 未完成真机锁屏长时间后台、跨天和小红书多机型实测。

### 回档说明

如需恢复上一版本：

1. 备份当前 `current` 为 `current_backup_before_rollback_YYYYMMDD_HHMMSS`。
2. 将 `versions/v0.1.1_20260706_新增接手与汇报规则` 中的工程文件复制到 `current`。
3. 不复制 `.gradle`、`app/build` 缓存。
4. 回档后重新构建并追加 changelog 记录。
## v0.5.1_20260708_应用选择二级页今日锁定

### 本版目标

在 v0.5.0 稳定基线上继续迭代：发布版保留隐藏开发者调试入口，目标应用选择改为二级页，并实现“今日新增应用明日才可取消”的本地日期锁。

### 新增内容

- 新增目标应用二级选择页 `TargetAppPickerScreen`。
- 新增 `TargetAppConfig.addedDate` 和 `TargetAppConfig.canRemoveAfterDate`。
- 新增 `TargetAppSaveResult`，用于保存后反馈被锁定阻止取消的应用。
- 新增日志 tag：`SearchGateTargetAppLockDebug`。
- 开发者模式新增“解除今日新增应用锁”和“清空用户新增应用”调试动作。

### 修改内容

- Android 工程 `versionCode` 从 `7` 更新为 `8`。
- Android 工程 `versionName` 从 `0.5.0` 更新为 `0.5.1`。
- 移除开发者入口的 `BuildConfig.DEBUG` 限定，Debug/Release 构建都可通过 5 连击标题和固定密钥进入。
- 主界面目标应用模块不再内联显示长列表，只保留摘要和“选择限制应用”入口。
- 应用选择页使用 `LazyColumn` 展示应用列表，底部保存栏固定。
- 保存目标应用时，仓库层会强制保留当天新增且尚未到 `canRemoveAfterDate` 的用户应用。
- 旧 7 字段用户应用配置仍可解析；升级前已有用户应用不会在升级当天被误锁。

### 删除内容

- 未删除旧版本。
- 未删除额度快照、娱乐倒计时、前台服务通知、Bilibili 日志、小红书状态机或抖音纯限制逻辑。

### 构建与测试

- 已在 `current` 下运行：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

- 构建结果：成功。
- Debug APK：`current\app\build\outputs\apk\debug\app-debug.apk`。
- Release unsigned APK：`current\app\build\outputs\apk\release\app-release-unsigned.apk`。
- Debug SHA256：`4EA7BFB0666596F603FF46A0792829EC304FC7CB3C7231D4FF7C3662A6B3A902`。
- Release SHA256：`2B183C57C2645BCC81AAD018EE19140A385AC0A086589EFC0BD91D5B548C6A71`。
- 静态核查：源码中无 `BuildConfig.DEBUG` 阻断和旧 `v0.5.0` 版本号残留。
- 未完成真实手机跨天验证和小屏 UI 验证。

### 回档说明

如需恢复上一版本：

1. 备份当前 `current` 为 `current_backup_before_rollback_YYYYMMDD_HHMMSS`。
2. 将 `versions\v0.5.0_20260708_App分类应用勾选开发者模式` 或本轮预修改备份复制到 `current`。
3. 不复制 `.gradle`、`app/build` 缓存。
4. 回档后重新构建并追加 changelog 记录。
## v0.5.2_20260708_beta_polish

### 本版目标

在 v0.5.1 稳定逻辑上做众测前 UI 文案和布局打磨，不重构核心拦截、额度、通知、悬浮窗或目标应用状态逻辑。

### 新增内容

- 主界面与选择限制应用页增加 Compose 安全区 padding。
- 选择页搜索框支持疑似包名添加入口，例如 `com.example.demo`。
- 应用行增加横向短标签样式，减少长句和挤压。

### 修改内容

- Android 工程 `versionCode` 从 `8` 更新为 `9`。
- Android 工程 `versionName` 从 `0.5.1` 更新为 `0.5.2`。
- 删除主页顶部版本说明长句。
- 目标应用模块精简为当前限制应用数和入口。
- 今日额度锁定文案删除跨天默认值说明。
- 普通首页不再显示调试日志模块；解锁开发者模式后才显示。
- 选择页底部只显示已选数量和保存按钮。
- 删除独立“高级手动添加包名”入口。

### 删除内容

- 未删除旧版本。
- 未删除小红书、Bilibili、抖音、用户新增 App 分类逻辑。
- 未删除今日新增应用锁定、娱乐倒计时、前台服务通知、悬浮圆圈或开发者模式。

### 构建与测试

- 已在 `current` 下运行：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

- 构建结果：成功。
- Debug APK：`current\app\build\outputs\apk\debug\app-debug.apk`。
- Release unsigned APK：`current\app\build\outputs\apk\release\app-release-unsigned.apk`。
- Debug SHA256：`5BDAD1C36B719BF5677F8C86A2B7519A5916F2F741E5C58D4BA368A9BEF0FD99`。
- Release SHA256：`54F6DD9B189CA4DC1E009045345906410FDB9FDFF98508CD491B88FE276DDE95`。
- 未完成真机多屏幕截图验收和签名 release 包验证。

### 回档说明

如需恢复上一版本：

1. 备份当前 `current` 为 `current_backup_before_rollback_YYYYMMDD_HHMMSS`。
2. 将 `versions\v0.5.1_20260708_应用选择二级页今日锁定` 或本轮预修改备份复制到 `current`。
3. 不复制 `.gradle`、`app/build` 缓存。
4. 回档后重新构建并追加 changelog 记录。
