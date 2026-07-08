# SearchGate 当前状态

更新时间：2026-07-08

## 当前版本

当前版本：`v0.5.2_20260708_beta_polish`

上一基准版本：`v0.5.1_20260708_应用选择二级页今日锁定`

## 当前开发入口

```text
C:\Users\LittleTaro\Desktop\课外项目\检索门 SearchGate\current
```

所有 Android 代码修改、构建和测试都在 `current` 中进行。

## 当前功能概况

- Android Compose 应用，包名 `com.example.focusgate`。
- 支持无障碍服务、悬浮窗询问页、娱乐倒计时前台服务通知、娱乐结束悬浮圆圈。
- 支持长期默认额度、当天额度快照、当天使用状态三层分离。
- 支持主界面模块化和模块收缩状态持久化。
- 支持目标 App 分类：
  - 小红书、Bilibili：`HYBRID_STUDY_ENTERTAINMENT`，保留资料查找和娱乐额度。
  - 抖音：`ENTERTAINMENT_ONLY`，不显示资料查找入口。
  - 用户新增 App：默认 `ENTERTAINMENT_ONLY`。
- 目标应用选择位于二级页，支持搜索、过滤、疑似包名添加和固定底部保存栏。
- 用户当天新增或重新启用的限制应用，当天不能取消，明天本地日期后可取消。
- 主界面和选择页已加入顶部安全区适配。
- 众测候选版普通 UI 已精简说明文案，调试日志模块只在开发者模式解锁后显示。
- 开发者调试模式仍可通过隐藏入口进入：5 连击 `FocusGate` 标题后输入固定密钥。

## 最近构建

```powershell
cd "C:\Users\LittleTaro\Desktop\课外项目\检索门 SearchGate\current"
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

结果：成功。

- Debug APK：`current\app\build\outputs\apk\debug\app-debug.apk`
- Debug SHA256：`5BDAD1C36B719BF5677F8C86A2B7519A5916F2F741E5C58D4BA368A9BEF0FD99`
- Release unsigned APK：`current\app\build\outputs\apk\release\app-release-unsigned.apk`
- Release SHA256：`54F6DD9B189CA4DC1E009045345906410FDB9FDFF98508CD491B88FE276DDE95`
- `versionCode = 9`
- `versionName = 0.5.2`

## 已知风险

- Release APK 当前未签名，不能直接作为正式分发包。
- 真机 UI 适配尚未覆盖所有屏幕尺寸，尤其是挖孔屏、水滴屏和横屏。
- Android 11+ 包可见性可能导致非 Launcher 应用不出现在目标应用列表中。
- 固定开发者密钥发布版可用只适合本地调试，不是强安全机制。
- Android 和厂商 ROM 仍可限制无障碍服务、前台服务、后台运行、自启动和电池白名单，App 无法 100% 保证后台不被杀。
- 悬浮窗不能阻止 Home、任务切换、系统设置或强杀 App。

## 汇报要求

任何后续 AI 每轮汇报都必须带上当前版本号，并说明本轮是否生成新版本、版本目录、构建状态、测试状态、APK 位置和未完成验证。
