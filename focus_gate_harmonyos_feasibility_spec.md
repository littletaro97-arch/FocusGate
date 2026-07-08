# FocusGate 鸿蒙端可行性评估与开发方案草案

> 来源：基于 `focus_gate_android_codex_spec.md` 的目标迁移评估。
> 目标平台：HarmonyOS / HarmonyOS NEXT 手机。
> 目标应用：小红书、哔哩哔哩。
> 目标用途：减少首页信息流浏览，把“打开 App”改造成“先明确资料查找目的，再进入搜索页”的流程。
> 结论状态：可以做原型验证，但不能承诺与 Android 版能力完全等价。

---

## 1. 原 Android 文档是否包含鸿蒙端内容

现有 `focus_gate_android_codex_spec.md` 没有鸿蒙端开发方案。

已检索关键词：

```text
鸿蒙
Harmony
HarmonyOS
HarmonyOS NEXT
ArkTS
DevEco
OpenHarmony
```

结果：没有命中鸿蒙端方案，只出现 Android 的 `AccessibilityService`、`TYPE_APPLICATION_OVERLAY`、`WindowManager`、Kotlin、Jetpack Compose、AndroidManifest 等内容。

因此本文档不是“提取版”，而是鸿蒙端重新评估后的开发草案。

---

## 2. 严格可行性结论

### 2.1 不能直接照搬 Android 方案

Android 版核心依赖：

```text
AccessibilityService
TYPE_APPLICATION_OVERLAY
WindowManager
Intent ACTION_VIEW Deep Link
GLOBAL_ACTION_HOME
SharedPreferences / DataStore
```

鸿蒙端不能假设存在等价能力。

特别是：

- 不能默认存在 Android 式 `TYPE_APPLICATION_OVERLAY`。
- 不能默认普通三方应用可以稳定覆盖其他应用全屏。
- 不能默认可以读取目标 App 页面节点。
- 不能默认可以监听任意目标 App 前台状态。
- 不能默认可以调用类似 `GLOBAL_ACTION_HOME` 的系统 Home 行为。
- 不能默认小红书、B 站鸿蒙原生版沿用 Android 包名和 Deep Link。

### 2.2 原型可行，但主风险很高

鸿蒙端可以尝试做原型，但必须先验证四件事：

1. 能否通过鸿蒙无障碍扩展或官方系统能力感知目标 App 前台状态。
2. 能否在目标 App 前台时显示足够强的全屏提醒层或替代交互。
3. 能否通过 Want / Deep Link 打开小红书、B 站搜索页。
4. 能否识别搜索页、首页、内容详情页，避免搜索后点笔记时误拦截。

如果第 1、2 点失败，鸿蒙原生版不能实现 Android 版同等“强拦截”。只能降级为弱提醒、应用内启动器、桌面快捷入口、通知提醒，或依赖系统屏幕时间/应用限制能力。

---

## 3. 推荐技术栈

| 模块 | 鸿蒙端建议 |
|---|---|
| 主语言 | ArkTS |
| UI | ArkUI |
| IDE | DevEco Studio |
| 应用模型 | Stage Model |
| 主入口 | UIAbility |
| 后台/扩展能力 | AccessibilityExtensionAbility 或其他官方扩展能力，需原型验证 |
| 状态存储 | Preferences / Data Preferences |
| 跨应用跳转 | Want / Deep Linking |
| 日志 | hilog |
| 目标系统 | 优先 HarmonyOS NEXT / API 12+ 或当前 DevEco 稳定 API |

---

## 4. 目标能力拆解

### 4.1 前台应用检测

Android 版通过无障碍服务监听窗口事件。

鸿蒙端候选方案：

```text
AccessibilityExtensionAbility
或系统开放的应用使用限制 / 屏幕时间相关 Kit
或仅做应用内启动器，不监控外部 App
```

必须先做 Spike：

- 安装调试包。
- 开启必要权限。
- 打开小红书和 B 站。
- 记录是否能拿到目标应用前台事件。
- 记录能拿到的信息粒度：bundleName、窗口变化、页面文本、控件类型等。

验收标准：

- 至少能稳定区分 `目标 App 前台` 和 `非目标 App 前台`。
- 不能依赖完整页面文本。
- 日志不得输出隐私内容。

### 4.2 覆盖层 / 拦截层

Android 版使用 `WindowManager` 覆盖目标 App。

鸿蒙端必须验证：

- 普通三方应用是否能跨应用显示全屏交互层。
- 覆盖层能否拦截返回键、手势、点击穿透。
- 覆盖层在系统安全策略下是否会被限制。
- 覆盖层是否需要特殊权限或审核。

如果不能跨应用覆盖，必须降级，不要伪装成强拦截能力。

可接受降级方案：

- FocusGate 启动器版：用户从 FocusGate 内选择“小红书查资料”或“娱乐”。
- 通知提醒版：检测能力不足时只做通知和每日计划提示。
- 系统限制协同版：引导用户使用系统屏幕时间或应用限制能力。

### 4.3 搜索跳转

Android 版尝试 Deep Link，失败后可能用无障碍自动填词。

鸿蒙端应使用 Want / Deep Linking 验证：

```text
小红书：打开搜索页并携带关键词
B 站：打开搜索页并携带关键词
```

注意：成功打开目标 App 不等于成功进入搜索页。必须有页面识别或显式失败提示。

### 4.4 页面识别

需要至少识别：

```text
HOME
SEARCH_PAGE
SEARCH_RESULTS
CONTENT_DETAIL
UNKNOWN
```

必须吸收 Android 版已经暴露的 bug 教训：

- 搜索结果页允许继续操作。
- 内容详情页允许继续操作。
- 搜索会话中的 `UNKNOWN` 不应立刻重新拦截。
- 明确回到首页才重新拦截。

---

## 5. 第一版状态机建议

```text
IDLE
BLOCKING
KEYWORD_INPUT
SEARCH_GRACE
SEARCH_VERIFIED
ENTERTAINMENT_SETUP
ENTERTAINMENT_ALLOWED
PAUSED
```

关键规则：

1. 当天第一次打开目标 App 时，询问今日娱乐次数和每次时长。
2. 当天设置后不可在 App 内修改。
3. 选择查资料后，输入关键词并尝试跳转搜索页。
4. 搜索跳转后进入 `SEARCH_GRACE`，短时间内避免重复拦截。
5. 识别到搜索页或搜索结果页后进入 `SEARCH_VERIFIED`。
6. `SEARCH_VERIFIED` 中打开内容详情页不拦截。
7. 明确识别到首页才重新进入 `BLOCKING`。
8. 娱乐中必须允许提前结束。

---

## 6. 权限与合规边界

必须坚持：

- 不读取、保存、上传完整页面内容。
- 不监控聊天、私信、相册、通讯录等隐私内容。
- 不请求与目标无关的权限。
- 不使用 root、系统签名、Hook、注入、抓屏绕过系统限制。
- 不把“能启动自己的 Ability”写成“能覆盖其他 App”。
- 不在没有真机验证前承诺支持 HarmonyOS NEXT。

---

## 7. 开发阶段建议

### Phase 0：空工程和能力探针

目标：先证明系统能力边界。

产物：

- ArkTS / ArkUI / Stage Model 空工程。
- 首页显示权限状态、目标应用配置、今日娱乐计划、最近 Deep Link 状态。
- 日志面板或 hilog 过滤标签。
- 前台检测 Spike。
- 覆盖层 Spike。
- Deep Link Spike。

### Phase 1：弱可用版本

前提：至少能做目标 App 检测或应用内启动器。

能力：

- 设置目标应用 bundleName。
- 设置每日娱乐次数和时长。
- 输入关键词。
- 调用 Want / Deep Link 打开搜索页。
- 失败时给出明确提示。
- 娱乐可提前结束。

### Phase 2：强拦截版本

前提：真机验证可以跨应用显示可交互全屏层。

能力：

- 目标 App 前台自动弹出询问界面。
- 查资料进入关键词搜索。
- 娱乐进入倒计时。
- 倒计时结束或提前结束后返回限制状态。
- 搜索结果和内容详情不误拦截。

### Phase 3：稳定性迭代

重点：

- 小红书页面分类。
- B 站页面分类。
- Deep Link 失败 fallback。
- 权限关闭后的恢复提示。
- 每日计划跨日期重置。
- 崩溃恢复和状态一致性。

---

## 8. 鸿蒙版目录建议

```text
FocusGateHM/
  AppScope/
  entry/
    src/main/
      module.json5
      ets/
        entryability/
          EntryAbility.ets
        pages/
          Index.ets
          KeywordPage.ets
          PermissionGuidePage.ets
        model/
          GuardState.ets
          DeepLinkResult.ets
          PageKind.ets
        service/
          GuardRepository.ets
          DeepLinkLauncher.ets
          PageDetector.ets
          TimePolicy.ets
          OverlayController.ets
        accessibility/
          FocusAccessibilityExtensionAbility.ets
```

如果无法实现鸿蒙无障碍扩展，则移除 `accessibility/`，改为启动器版。

---

## 9. 给另一个 Codex 对话的开发指令

```text
请开发 FocusGate 的鸿蒙端原型，项目名 FocusGateHM。

不要直接照搬 Android 代码。先做可行性 Spike，再决定是否进入完整实现。

目标：
1. 使用 HarmonyOS / ArkTS / ArkUI / Stage Model。
2. 首先创建可运行空工程，首页显示：
   - 当前权限状态
   - 目标应用配置
   - 今日娱乐计划
   - 最近 Deep Link 状态
3. 优先验证是否能通过 AccessibilityExtensionAbility 或其他官方能力检测小红书 / B 站进入前台。
4. 优先验证是否能在目标 App 前台显示全屏可交互覆盖层。
5. 如果不能跨应用覆盖，不要伪装成功；改为“启动器版 / 弱提醒版”。
6. 目标应用不要默认沿用 Android 包名，必须在设置页支持手动配置 bundleName。
7. 输入关键词后，尝试用 Want / Deep Link 打开目标 App 搜索页。
8. Deep Link 成功启动不等于搜索成功，必须通过页面识别或用户可见提示确认。
9. 实现状态机：
   - BLOCKING
   - SEARCH_GRACE
   - SEARCH_VERIFIED
   - ENTERTAINMENT_ALLOWED
   - PAUSED
10. 搜索会话规则：
   - 搜索结果页放行
   - 内容详情页放行
   - 搜索会话中的 UNKNOWN 不立刻拦截
   - 明确首页重新拦截
11. 娱乐规则：
   - 每天第一次打开目标 App 时设置当天娱乐次数和每次时长
   - 当天设置后不可在 App 内修改
   - 娱乐中可以提前结束
12. 不要读取、保存、上传完整页面内容。
13. 不要请求无关权限。
14. 输出每个 Spike 的验证结果：
   - 可行
   - 不可行
   - 需要真机手动授权
   - 需要降级方案
```

---

## 10. 官方参考入口

- HarmonyOS 应用开发总览：https://developer.huawei.com/consumer/en/doc/harmonyos-guides/application-dev-guide
- ArkTS 说明：https://developer.huawei.com/consumer/en/arkts/
- ArkTS 快速开始：https://developer.huawei.com/consumer/en/doc/harmonyos-guides/arkts-get-started
- ExtensionAbility 概览：https://developer.huawei.com/consumer/en/doc/harmonyos-guides/extensionability-overview
- AccessibilityExtensionAbility 参考：https://developer.huawei.com/consumer/en/doc/harmonyos-references/js-apis-application-accessibilityextensionability
- HarmonyOS 6 新能力说明：https://developer.huawei.com/consumer/en/doc/harmonyos-releases/os-new-feature-600
- ArkTS Deep Linking 相关说明入口：https://developer.huawei.com/consumer/en/doc/harmonyos-guides/arkts-link
