# Android 自控式搜索拦截器开发方案

> 交付对象：Codex / Android 开发助手  
> 目标平台：Android 手机  
> 目标应用：小红书、哔哩哔哩  
> 目标用途：减少首页信息流浏览，把“打开 App”改造成“先明确资料查找目的，再进入搜索页”的流程  
> 第一版定位：自用 / 侧载优先，不承诺应用商店上架

---

## 1. 项目目标

开发一个 Android 原生应用。当用户打开小红书或哔哩哔哩时，本应用通过无障碍服务检测目标 App 前台状态，并使用 `TYPE_APPLICATION_OVERLAY` 拦截层覆盖目标 App，阻止用户直接进入首页信息流。

注意：不要把“后台直接启动全屏 Activity”作为唯一拦截方案。Android 新版本和国产 ROM 对后台启动 Activity 有限制，第一版应以 Overlay 拦截层作为主方案，Activity 主要负责设置、权限引导和关键词输入。

用户需要选择：

1. **资料查找**：在本应用内输入关键词，随后尝试跳转到小红书或哔哩哔哩的搜索结果页。
2. **娱乐 5 分钟**：短时间允许进入目标应用，时间结束后重新拦截。
3. **返回桌面**：调用系统 Home 行为，让目标 App 离开前台。

核心目标：

```text
打开小红书 / B站
↓
无障碍服务检测目标 App 前台
↓
Overlay 拦截层覆盖目标 App
↓
用户输入明确关键词
↓
尝试直接进入搜索结果页
↓
识别搜索页 / 首页状态
↓
回到明显首页后再次拦截
```

---

## 2. 非目标范围

第一版不要做这些功能：

- 云同步
- 登录系统
- 复杂统计图表
- 家长控制模式
- 防卸载
- root / Xposed / Magisk 模块
- AI 判断用户是否在学习
- 绕过系统权限限制
- 监控聊天、通讯录、短信、相册等隐私内容
- 承诺兼容所有 Android 版本和所有国产 ROM 后台策略
- 承诺上架 Google Play 或国内应用商店

第一版只做“前台检测、Overlay 拦截、关键词输入、Deep Link 搜索尝试、基础首页识别、娱乐限时、失败可见”。

---

## 3. 推荐技术栈

| 模块 | 技术 |
|---|---|
| 主语言 | Kotlin |
| UI | Jetpack Compose |
| 前台应用检测 | AccessibilityService |
| 主拦截层 | `TYPE_APPLICATION_OVERLAY` + `WindowManager` |
| 辅助页面 | Activity，用于设置、权限引导、关键词输入 |
| 状态存储 | DataStore 优先，SharedPreferences 可用于 MVP |
| 日志 | Logcat，自定义 tag：`FocusGate` |
| 构建工具 | Android Gradle Plugin |
| 最低 Android 版本 | Android 8.0+ 可先支持 |
| 目标 Android 版本 | 使用当前稳定 Android Studio 默认 targetSdk，并按新系统后台限制设计 |

---

## 4. 目标应用包名

先按以下包名处理：

```text
小红书：com.xingin.xhs
哔哩哔哩：tv.danmaku.bili
```

但是必须支持在设置页中手动修改或添加包名，因为国内不同渠道版可能存在差异。

ADB 检查命令：

```bat
adb shell pm list packages | findstr /i "xhs xingin bili bilibili"
```

---

## 5. 必需权限、系统设置与合规边界

### 5.1 AndroidManifest.xml 关键权限

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

说明：

- `SYSTEM_ALERT_WINDOW` 用于显示 Overlay 拦截层，是第一版主拦截方案的关键权限。
- `POST_NOTIFICATIONS` 仅在需要常驻通知、状态提醒或前台服务提示时请求；如果第一版没有通知，不要默认请求。
- 不要请求通讯录、短信、相册、位置、录音等无关权限。

无障碍服务需要在 `service` 中声明：

```xml
<service
    android:name=".FocusAccessibilityService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>

    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

### 5.2 用户需要手动开启的权限

App 首次启动时做权限引导页，引导用户开启：

1. 无障碍服务
2. 悬浮窗权限
3. 后台运行 / 自启动权限
4. 电池优化白名单
5. 通知权限，可选，仅在确实使用通知时出现

国产系统尤其需要提示：

```text
OPPO / ColorOS：
- 允许自启动
- 允许后台运行
- 关闭电池优化
- 锁定后台
- 开启悬浮窗
- 开启无障碍服务
```

### 5.3 合规边界

第一版按自用 / 侧载设计。如果未来考虑上架应用商店，必须补充：

- 无障碍服务用途的显著披露。
- 用户主动同意流程。
- 隐私政策。
- 不收集页面内容、不上传无障碍节点、不监控聊天和隐私内容的明确声明。
- Google Play Accessibility API 声明表单或国内应用商店对应说明。

官方参考：

- Android 后台启动限制：https://developer.android.com/guide/components/activities/secure-bal
- AccessibilityService 文档：https://developer.android.com/guide/topics/ui/accessibility/service
- Google Play Accessibility API 政策：https://support.google.com/googleplay/android-developer/answer/10964491

---

## 6. 无障碍服务配置

创建：

```text
app/src/main/res/xml/accessibility_service_config.xml
```

示例：

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowsChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    android:notificationTimeout="100"
    android:description="@string/accessibility_service_description" />
```

第一版不应读取和持久化完整无障碍节点树。仅在本地内存中做当前页面粗略判断，并在日志中避免打印敏感页面文本。

---

## 7. 核心状态机设计

建议使用以下状态：

```kotlin
enum class GuardMode {
    BLOCKING,              // 默认拦截
    SEARCH_GRACE,          // 刚发起搜索跳转，短暂放行目标 App 加载搜索页
    ENTERTAINMENT_ALLOWED, // 娱乐限时放行
    PAUSED                 // 用户手动暂停保护
}
```

需要保存的数据：

```kotlin
data class GuardState(
    val targetPackages: Set<String>,
    val searchGraceUntil: Long,
    val searchSessionPackage: String?,
    val entertainmentUntil: Long,
    val entertainmentPackage: String?,
    val entertainmentDate: String,
    val dailyEntertainmentCount: Int,
    val lastTargetPackage: String?,
    val lastKeyword: String?,
    val lastPlatform: String?,
    val lastDeepLinkResult: DeepLinkResult?,
    val lastInterceptTime: Long
)

enum class DeepLinkResult {
    NOT_STARTED,
    STARTED,
    FAILED_TO_START,
    STARTED_BUT_NOT_VERIFIED
}
```

关键点：

- 不使用全局 `allowUntil = 10min` 作为搜索放行。它太宽，会让用户在 10 分钟内自由返回首页刷信息流。
- 搜索跳转只给 `searchGraceUntil = now + 15-30s` 的短暂加载窗口。
- 放行状态必须绑定具体包名，避免从小红书搜索跳转后误放行 B站。
- 娱乐次数必须保存日期字段，第二天按本地日期重置。

建议时间参数：

```text
拦截防抖：800-1500 ms
搜索跳转 grace window：15-30 秒
娱乐放行时间：5 分钟
每日娱乐次数上限：2 次
```

---

## 8. 应用流程

### 8.1 用户打开目标 App

```text
AccessibilityService 监听窗口事件
↓
读取 event.packageName
↓
如果包名属于目标应用
↓
读取当前 rootInActiveWindow 做基础页面识别
↓
检查娱乐窗口、搜索 grace window、PageDetector 结果、防抖
↓
需要拦截时显示 Overlay 拦截层
```

### 8.2 Overlay 拦截层

Overlay 全屏覆盖目标 App，建议提供三个按钮：

```text
你打开了：小红书 / 哔哩哔哩

[ 资料查找 ]
[ 娱乐 5 分钟 ]
[ 返回桌面 ]
```

按钮逻辑：

| 按钮 | 行为 |
|---|---|
| 资料查找 | 移除 Overlay，进入 KeywordActivity |
| 娱乐 5 分钟 | 设置 `entertainmentUntil = now + 5min` 和 `entertainmentPackage`，移除 Overlay |
| 返回桌面 | 执行 `GLOBAL_ACTION_HOME`，移除 Overlay |

不要承诺“关闭目标 App”。普通 Android 应用不能可靠关闭另一个 App，只能让目标 App 离开前台。

### 8.3 KeywordActivity 页面

字段：

```text
资料关键词：[              ]
目标平台：  [ 小红书 ] [ B站 ]

[ 开始搜索 ]
```

点击开始搜索：

1. 校验关键词非空。
2. 写入 `lastKeyword`、`lastPlatform`、`searchSessionPackage`。
3. 设置 `searchGraceUntil = now + 30s`。
4. 调用对应平台 Deep Link。
5. 记录 `DeepLinkResult`。
6. 关闭当前 Activity 或回到后台。
7. 由无障碍服务在目标 App 前台后验证是否进入明显搜索页；若失败，后续重新拦截并展示失败提示。

---

## 9. 搜索跳转方案

### 9.1 小红书搜索 Deep Link

优先尝试：

```text
xhsdiscover://search/result?keyword=<encodedKeyword>&target_search=notes
```

Kotlin 示例：

```kotlin
fun openXhsSearch(context: Context, keyword: String): DeepLinkResult {
    val encoded = Uri.encode(keyword)
    val uri = Uri.parse("xhsdiscover://search/result?keyword=$encoded&target_search=notes")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        setPackage("com.xingin.xhs")
    }

    return try {
        context.startActivity(intent)
        DeepLinkResult.STARTED
    } catch (e: ActivityNotFoundException) {
        DeepLinkResult.FAILED_TO_START
    } catch (e: SecurityException) {
        DeepLinkResult.FAILED_TO_START
    }
}
```

### 9.2 B站搜索 Deep Link

优先尝试：

```text
bilibili://search?keyword=<encodedKeyword>
```

Kotlin 示例：

```kotlin
fun openBiliSearch(context: Context, keyword: String): DeepLinkResult {
    val encoded = Uri.encode(keyword)
    val uri = Uri.parse("bilibili://search?keyword=$encoded")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        setPackage("tv.danmaku.bili")
    }

    return try {
        context.startActivity(intent)
        DeepLinkResult.STARTED
    } catch (e: ActivityNotFoundException) {
        DeepLinkResult.FAILED_TO_START
    } catch (e: SecurityException) {
        DeepLinkResult.FAILED_TO_START
    }
}
```

### 9.3 Deep Link 失败处理

Deep Link 不是稳定公开契约，不能假设永远可用。第一版必须实现失败可见：

```text
Deep Link 无法启动
↓
记录 lastDeepLinkResult = FAILED_TO_START
↓
显示 Overlay 提示：“当前版本无法直接打开搜索页，请手动搜索或等待后续 fallback。”
↓
提供 [返回桌面] 和 [允许手动打开搜索] 两个选择
```

如果 Deep Link 能启动但 30 秒内没有识别到明显搜索页：

```text
记录 lastDeepLinkResult = STARTED_BUT_NOT_VERIFIED
不要静默认为成功
后续如果识别到明显首页，立即重新拦截
```

### 9.4 无障碍自动填词 fallback

后续版本可以补充：

```text
打开目标 App
↓
查找搜索按钮或搜索框
↓
点击搜索入口
↓
查找 EditText
↓
ACTION_SET_TEXT 输入关键词
↓
点击搜索按钮或发送回车
```

注意：兜底方案脆弱，目标 App 更新 UI 后可能失效。第一版可以预留接口，但不要把它写成已承诺能力。

---

## 10. 首页重新拦截策略

难点：准确判断用户是否“退出搜索页回到首页”。

第一版必须包含基础 PageDetector，否则“返回首页后重新拦截”的验收条件无法成立。

### 10.1 第一版策略

```text
用户从本应用发起搜索后：
1. 设置 searchGraceUntil = now + 15-30s
2. grace window 内允许目标 App 加载
3. grace window 之后：
   - 如果识别为明显搜索页：继续放行
   - 如果识别为明显首页：重新拦截
   - 如果无法识别：默认重新拦截，或显示“不确定页面，是否继续搜索？”提示
```

不建议使用“10 分钟内不拦截目标 App”的策略。它会让用户在 10 分钟内返回首页刷信息流，违背项目目标。

### 10.2 基础 PageDetector

判断目标：

```text
小红书明显首页特征：
- 底部导航存在“首页”
- 页面存在推荐流或关注/发现类入口
- 不存在明显搜索结果页输入框

小红书明显搜索页特征：
- 存在搜索关键词或搜索框
- 存在“综合 / 笔记 / 用户”等搜索结果分类文本

B站明显首页特征：
- 底部导航存在“首页”
- 页面存在“推荐”等首页内容区
- 不存在搜索结果页顶部搜索框

B站明显搜索页特征：
- 存在搜索关键词或搜索框
- 存在“综合 / 视频 / 番剧 / 用户”等搜索结果分类文本
```

页面识别不要写死单一控件 ID。应优先组合判断：

```text
包名 + 多个文本节点 + 是否存在 EditText + 当前状态 + 防抖时间
```

伪代码：

```kotlin
enum class PageKind {
    HOME,
    SEARCH_RESULTS,
    UNKNOWN
}

fun shouldInterceptTargetApp(
    packageName: String,
    root: AccessibilityNodeInfo?,
    now: Long
): Boolean {
    if (!isTargetPackage(packageName)) return false
    if (isWithinEntertainmentWindow(packageName, now)) return false

    val pageKind = detectPageKind(packageName, root)

    if (isWithinSearchGrace(packageName, now)) {
        return pageKind == PageKind.HOME
    }

    return when (pageKind) {
        PageKind.SEARCH_RESULTS -> false
        PageKind.HOME -> true
        PageKind.UNKNOWN -> true
    }
}
```

---

## 11. 防止循环弹窗

必须处理这些情况：

1. 用户正在本应用内，不要再次拦截。
2. Overlay 已经显示时，不要重复添加新的 Overlay。
3. 刚从关键词页跳转搜索，不要立刻拦截。
4. 目标 App 和本应用切换时不要形成死循环。
5. Deep Link 失败提示不要和普通拦截提示互相覆盖。

建议逻辑：

```kotlin
private const val INTERCEPT_DEBOUNCE_MS = 1200L

fun canShowOverlay(
    packageName: String,
    root: AccessibilityNodeInfo?,
    now: Long
): Boolean {
    if (isOurAppInForeground()) return false
    if (overlayController.isShowing()) return false
    if (now - lastInterceptTime < INTERCEPT_DEBOUNCE_MS) return false
    if (isWithinEntertainmentWindow(packageName, now)) return false

    val pageKind = detectPageKind(packageName, root)
    if (isWithinSearchGrace(packageName, now) && pageKind != PageKind.HOME) {
        return false
    }

    return pageKind != PageKind.SEARCH_RESULTS
}
```

显示拦截层：

```kotlin
overlayController.show(
    targetPackage = packageName,
    reason = InterceptReason.TARGET_APP_HOME_OR_UNKNOWN
)
```

Activity 只用于设置和关键词输入。不要依赖后台直接启动 `InterceptActivity` 来完成主拦截。

---

## 12. 项目文件结构建议

```text
app/
  src/main/
    AndroidManifest.xml
    java/com/example/focusgate/
      MainActivity.kt
      KeywordActivity.kt
      FocusAccessibilityService.kt
      OverlayController.kt
      GuardRepository.kt
      DeepLinkLauncher.kt
      PageDetector.kt
      PermissionGuideActivity.kt
      TimePolicy.kt
      DeepLinkResult.kt
    res/xml/
      accessibility_service_config.xml
    res/values/
      strings.xml
```

各文件职责：

| 文件 | 职责 |
|---|---|
| `MainActivity.kt` | 首页、权限状态展示、设置入口 |
| `KeywordActivity.kt` | 输入关键词并选择平台 |
| `FocusAccessibilityService.kt` | 前台 App 监听与拦截触发 |
| `OverlayController.kt` | 创建、更新、移除全屏 Overlay 拦截层 |
| `GuardRepository.kt` | DataStore / SharedPreferences 状态读写 |
| `DeepLinkLauncher.kt` | 小红书 / B站搜索跳转与失败捕获 |
| `PageDetector.kt` | 首页 / 搜索页 / 未知页粗略识别 |
| `PermissionGuideActivity.kt` | 权限引导 |
| `TimePolicy.kt` | 娱乐限时、搜索 grace window、防抖逻辑 |
| `DeepLinkResult.kt` | Deep Link 启动结果枚举 |

---

## 13. Codex 开发指令

将下面这段完整交给 Codex：

```text
请开发一个 Android 原生 Kotlin 应用，项目名 FocusGate，用于自控式拦截小红书和哔哩哔哩主页。第一版按自用 / 侧载设计，不承诺应用商店上架。

核心需求：
1. 使用 Kotlin + Jetpack Compose。
2. 创建 AccessibilityService，监听前台窗口变化。
3. 目标包名默认包括：
   - com.xingin.xhs
   - tv.danmaku.bili
4. 当检测到目标应用进入前台时，不要依赖后台启动 Activity 作为主拦截。使用 TYPE_APPLICATION_OVERLAY + WindowManager 显示全屏 Overlay 拦截层。
5. Overlay 显示：
   - 当前打开的目标应用名称
   - “资料查找”按钮
   - “娱乐 5 分钟”按钮
   - “返回桌面”按钮
6. 点击“资料查找”后移除 Overlay，进入 KeywordActivity，用户输入关键词并选择小红书或 B站。
7. 点击“开始搜索”后：
   - 设置 searchGraceUntil = 当前时间 + 30 秒
   - 保存 searchSessionPackage、lastKeyword、lastPlatform
   - 小红书尝试使用 xhsdiscover://search/result?keyword=<encoded>&target_search=notes
   - B站尝试使用 bilibili://search?keyword=<encoded>
   - 使用 Intent.ACTION_VIEW 打开目标 App
   - 捕获 ActivityNotFoundException 和 SecurityException
   - 记录 DeepLinkResult，不要静默失败
8. 点击“娱乐 5 分钟”后：
   - 设置 entertainmentUntil = 当前时间 + 5 分钟
   - 保存 entertainmentPackage
   - 每天最多允许 2 次娱乐
   - 使用本地日期字段 entertainmentDate 做每日重置
9. 点击“返回桌面”后：
   - 调用无障碍 GLOBAL_ACTION_HOME
   - 不要承诺关闭目标 App
10. 使用 DataStore 或 SharedPreferences 保存：
   - targetPackages
   - searchGraceUntil
   - searchSessionPackage
   - entertainmentUntil
   - entertainmentPackage
   - entertainmentDate
   - dailyEntertainmentCount
   - lastKeyword
   - lastPlatform
   - lastDeepLinkResult
   - lastInterceptTime
11. 实现基础 PageDetector：
   - 能粗略识别明显首页、明显搜索结果页、未知页
   - 识别明显首页时重新拦截
   - 识别明显搜索页时放行
   - 未知页默认重新拦截或显示“不确定页面，是否继续搜索？”提示
12. 防止循环弹窗：
   - 本应用在前台时不要拦截
   - Overlay 已显示时不要重复显示
   - 刚跳转搜索页时不要拦截
   - 1200ms 内不要重复显示 Overlay
13. 增加权限引导页：
   - 无障碍服务
   - 悬浮窗权限
   - 电池优化白名单提示
   - 后台运行提示
   - 通知权限仅在确实使用通知时请求
14. 增加 Logcat 日志，tag 使用 FocusGate。不要打印完整页面内容或隐私文本。
15. 第一版必须做到失败可见。Deep Link 无法启动或无法验证进入搜索页时，显示明确提示，并提供“返回桌面”和“允许手动打开搜索”选择。
16. 请输出完整 Android 项目代码，并保证可以在 Android Studio 中直接构建运行。
```

---

# 测试方法

## 14. 测试前准备

### 14.1 安装工具

需要：

- Android Studio
- 一台安卓手机
- USB 数据线
- 手机开启开发者选项
- 手机开启 USB 调试
- 已安装小红书和哔哩哔哩

### 14.2 确认 ADB 连接

```bat
adb devices
```

正常情况会看到类似：

```text
List of devices attached
xxxxxxxx    device
```

如果显示 `unauthorized`，在手机上确认 USB 调试授权。

---

## 15. 构建与安装测试

### 15.1 Android Studio 构建

在 Android Studio 中：

```text
File → Open → 选择项目目录
↓
等待 Gradle Sync 完成
↓
Build → Make Project
↓
Run → 选择手机设备
```

### 15.2 ADB 安装 APK

如果 Codex 输出了 APK，可用：

```bat
adb install -r app-debug.apk
```

---

## 16. 权限测试

首次打开 FocusGate 后，逐项检查：

| 测试项 | 期望结果 |
|---|---|
| 无障碍服务 | 能跳转系统设置页，并能开启 FocusGate 服务 |
| 悬浮窗权限 | 能跳转系统设置页，并能允许显示在其他应用上层 |
| 电池优化 | 能提示用户关闭电池优化 |
| 后台运行 | 能提示 OPPO / ColorOS 等系统手动允许后台运行 |
| 通知权限 | 只有使用通知时才请求 |

ADB 检查无障碍服务是否开启：

```bat
adb shell settings get secure enabled_accessibility_services
```

如果列表中出现 FocusGate 的无障碍服务组件名，说明已开启。

---

## 17. 包名测试

确认手机上的小红书和 B站包名：

```bat
adb shell pm list packages | findstr /i "xhs xingin bili bilibili"
```

期望至少出现：

```text
package:com.xingin.xhs
package:tv.danmaku.bili
```

如果包名不同，需要在 App 设置页中添加实际包名。

---

## 18. 拦截功能测试

### 18.1 小红书拦截测试

步骤：

```text
1. 确认 FocusGate 无障碍服务已开启
2. 确认悬浮窗权限已开启
3. 回到桌面
4. 点击打开小红书
```

期望结果：

```text
小红书刚打开后，FocusGate Overlay 拦截层出现
页面显示：资料查找 / 娱乐 5 分钟 / 返回桌面
用户不能直接浏览小红书首页信息流
```

### 18.2 B站拦截测试

步骤：

```text
1. 回到桌面
2. 点击打开哔哩哔哩
```

期望结果：

```text
B站刚打开后，FocusGate Overlay 拦截层出现
用户不能直接进入 B站推荐首页
```

### 18.3 ADB 快速启动目标 App

小红书：

```bat
adb shell monkey -p com.xingin.xhs 1
```

B站：

```bat
adb shell monkey -p tv.danmaku.bili 1
```

---

## 19. 资料查找流程测试

### 19.1 小红书搜索测试

步骤：

```text
1. 打开小红书
2. FocusGate Overlay 出现
3. 点击“资料查找”
4. 输入关键词，例如：材料力学 应力应变
5. 选择“小红书”
6. 点击“开始搜索”
```

期望结果：

```text
App 尝试进入小红书搜索结果页
如果成功，搜索词已经填入，页面展示相关结果
如果失败，FocusGate 明确提示 Deep Link 不可用或未验证成功
期间不会刚跳转就被 FocusGate 拦回
```

### 19.2 B站搜索测试

步骤：

```text
1. 打开 B站
2. FocusGate Overlay 出现
3. 点击“资料查找”
4. 输入关键词，例如：高等数学 曲线积分
5. 选择“B站”
6. 点击“开始搜索”
```

期望结果：

```text
App 尝试进入 B站搜索结果页
如果成功，搜索词已经填入，页面展示相关视频结果
如果失败，FocusGate 明确提示 Deep Link 不可用或未验证成功
```

### 19.3 Deep Link 单独测试

小红书：

```bat
adb shell am start -a android.intent.action.VIEW -d "xhsdiscover://search/result?keyword=材料力学&target_search=notes" com.xingin.xhs
```

B站：

```bat
adb shell am start -a android.intent.action.VIEW -d "bilibili://search?keyword=高等数学" tv.danmaku.bili
```

如果这些命令本身都无法打开搜索页，说明 Deep Link 在当前 App 版本中不可用，需要后续开发无障碍自动点击搜索框的 fallback。

---

## 20. 返回首页再拦截测试

步骤：

```text
1. 通过 FocusGate 输入关键词并进入搜索页
2. 在搜索结果页浏览一会儿
3. 按返回键，直到回到目标 App 首页
```

期望结果：

```text
如果 PageDetector 识别为明显首页，FocusGate 重新弹出 Overlay
如果识别为明显搜索页，FocusGate 不拦截
如果无法识别，FocusGate 默认重新拦截或提示“不确定页面，是否继续搜索？”
```

如果没有重新拦截，检查：

```text
1. searchGraceUntil 是否还没过期
2. PageDetector 是否没有识别到首页
3. AccessibilityService 是否收到了窗口变化事件
4. 目标 App 的页面文本是否发生变化
```

---

## 21. 娱乐模式测试

步骤：

```text
1. 打开小红书或 B站
2. FocusGate Overlay 出现
3. 点击“娱乐 5 分钟”
4. 进入目标 App
5. 在 5 分钟内浏览
6. 5 分钟后继续停留或重新打开目标 App
```

期望结果：

```text
5 分钟内不拦截对应目标 App
5 分钟后重新拦截
每天最多允许 2 次娱乐
第二天按本地日期自动重置次数
超过次数后，“娱乐 5 分钟”按钮不可用或提示今日次数已用完
```

建议测试时把娱乐时长临时改成 30 秒，方便快速验证。

---

## 22. 返回桌面测试

步骤：

```text
1. 打开小红书或 B站
2. FocusGate Overlay 出现
3. 点击“返回桌面”
```

期望结果：

```text
手机回到桌面
目标 App 不再显示在前台
FocusGate 不出现循环弹窗
```

---

## 23. 循环弹窗测试

重点测试场景：

```text
1. 打开目标 App → 拦截 → 资料查找 → 搜索页
2. 立即返回 FocusGate
3. 快速切换后台任务
4. 快速连续打开小红书和 B站
5. 横竖屏切换
6. 锁屏再解锁
7. 杀掉 FocusGate 后重新打开
8. Deep Link 失败后重复点击资料查找
```

期望结果：

```text
不会连续显示多个 Overlay
不会在 FocusGate 自己的页面上再次拦截
不会因为状态错乱导致无法进入搜索页
Deep Link 失败提示不会被普通拦截提示覆盖
```

---

## 24. Logcat 调试方法

查看 FocusGate 日志：

```bat
adb logcat | findstr FocusGate
```

建议 Codex 在关键位置打印日志：

```text
FocusGate: event package = com.xingin.xhs
FocusGate: target app detected
FocusGate: show overlay reason = HOME_OR_UNKNOWN
FocusGate: search grace until = xxxxx
FocusGate: entertainment allowed until = xxxxx
FocusGate: skip intercept because app is self
FocusGate: skip intercept because overlay showing
FocusGate: skip intercept because debounce
FocusGate: deep link result = STARTED
FocusGate: deep link result = FAILED_TO_START
FocusGate: page kind = SEARCH_RESULTS
FocusGate: page kind = HOME
FocusGate: page kind = UNKNOWN
```

不要把完整无障碍节点文本打印到日志中，避免泄露隐私页面内容。

---

## 25. 常见问题与排查

### 25.1 打开目标 App 没有拦截

检查：

```text
1. 无障碍服务是否开启
2. 悬浮窗权限是否开启
3. 目标包名是否正确
4. FocusGate 是否被系统杀后台
5. OPPO / ColorOS 是否限制后台运行
6. 是否在 searchGraceUntil 或 entertainmentUntil 放行时间内
7. Overlay 是否已显示但被系统限制
```

ADB：

```bat
adb shell dumpsys window | findstr mCurrentFocus
adb shell settings get secure enabled_accessibility_services
adb logcat | findstr FocusGate
```

---

### 25.2 一点击搜索就被重新拦截

可能原因：

```text
1. searchGraceUntil 设置太晚
2. searchGraceUntil 没有持久化保存
3. searchSessionPackage 没有绑定目标包名
4. 防抖时间太短
5. AccessibilityService 在跳转瞬间重复触发
```

修复：

```text
必须先写入 searchGraceUntil 和 searchSessionPackage，再 startActivity 打开 Deep Link。
```

---

### 25.3 小红书可以跳搜索，B站不行

可能原因：

```text
B站当前版本不支持 bilibili://search?keyword=xxx
```

处理：

```text
1. 先用 ADB 单独测试 Deep Link
2. 如果失败，第一版必须明确提示失败
3. 后续版本再开发无障碍 fallback
```

---

### 25.4 回到首页后没有重新拦截

可能原因：

```text
1. searchGraceUntil 时间还没过
2. 首页识别逻辑太弱
3. 目标 App 首页文本发生变化
4. 只监听了 TYPE_WINDOW_STATE_CHANGED，没有监听内容变化
```

处理：

```text
1. 临时把 searchGraceUntil 改成 5 秒测试
2. 增加 TYPE_WINDOW_CONTENT_CHANGED
3. 只打印脱敏后的节点摘要，重新调整 PageDetector
```

---

### 25.5 Overlay 无法显示到最前面

可能原因：

```text
1. 悬浮窗权限未开
2. 系统限制后台弹窗或悬浮窗
3. 国产 ROM 额外限制后台显示
4. WindowManager.LayoutParams 类型或 flag 配置错误
```

处理：

```text
1. 检查 SYSTEM_ALERT_WINDOW 是否已授予
2. 使用 TYPE_APPLICATION_OVERLAY
3. 引导用户开启悬浮窗权限和后台运行权限
4. 不要退回到单纯后台启动 Activity 作为唯一方案
```

---

## 26. 验收标准

第一版验收条件：

| 编号 | 验收项 | 通过标准 |
|---|---|---|
| 1 | 打开小红书自动拦截 | 1 秒内出现 FocusGate Overlay |
| 2 | 打开 B站自动拦截 | 1 秒内出现 FocusGate Overlay |
| 3 | 小红书资料查找 | 输入关键词后尝试进入搜索结果页；失败时明确提示 |
| 4 | B站资料查找 | 输入关键词后尝试进入搜索结果页；失败时明确提示 |
| 5 | Deep Link 失败可见 | 无法启动或无法验证搜索页时，不静默失败 |
| 6 | 搜索短暂放行 | 跳转后不会立刻被 FocusGate 拦回，grace window 结束后按页面识别处理 |
| 7 | 返回首页再拦截 | 识别为明显首页后重新出现 FocusGate Overlay |
| 8 | 搜索页放行 | 识别为明显搜索结果页时不拦截 |
| 9 | 娱乐限时 | 5 分钟后重新拦截 |
| 10 | 娱乐次数 | 每日最多 2 次，第二天重置 |
| 11 | 返回桌面 | 点击后回到桌面 |
| 12 | 无循环弹窗 | 不重复显示多个 Overlay |

---

## 27. 建议迭代路线

### v0.1

```text
无障碍监听 + Overlay 主拦截 + 关键词输入 + Deep Link 搜索尝试 + 失败可见 + 基础 PageDetector
```

### v0.2

```text
增加 B站和小红书的无障碍自动填词 fallback
```

### v0.3

```text
增强 PageDetector，减少未知页误拦截和搜索页误拦截
```

### v0.4

```text
增加每日统计：拦截次数、搜索次数、娱乐次数
```

### v0.5

```text
增加白名单时段、夜间禁止娱乐、自定义娱乐次数
```

### v1.0 前置条件

```text
如果考虑发布到应用商店，必须补齐无障碍用途披露、隐私政策、用户同意、数据最小化说明和应用商店审核材料。
```

---

## 28. 最重要的实现原则

1. **Overlay 是主拦截方案**：不要依赖后台直接启动 Activity。
2. **先做最小可用版本**：能检测、能拦截、能输入关键词、能尝试跳搜索页、失败可见。
3. **先 Deep Link，后无障碍自动填词**：Deep Link 更干净，但不稳定；自动填词更脆弱，放到后续。
4. **搜索只给短暂 grace window**：不要用 10 分钟全局放行。
5. **PageDetector 必须进入 v0.1**：否则无法验收“返回首页再拦截”。
6. **所有状态先写入，再跳转目标 App**：避免刚跳转就被自己拦回。
7. **状态要绑定包名和日期**：避免跨 App 误放行，确保每日次数能重置。
8. **必须有防抖和 Overlay 去重**：防止连续弹窗。
9. **国产系统要做权限引导**：否则后台服务和 Overlay 容易失效。
10. **允许失败可见**：Deep Link 失败时明确提示，不要静默失败。
11. **不要请求无关权限**：只使用实现目标所需的权限。
12. **不要持久化隐私页面内容**：无障碍节点只用于本地即时判断。
