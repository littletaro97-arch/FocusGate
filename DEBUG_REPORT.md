# SearchGate Debug Report - v0.5.2 Beta Polish

日期：2026-07-08

## 本轮新增/修复的问题列表

1. 精简主页和目标应用模块的发行版文案。
2. 主界面与“选择限制应用”二级页加入顶部安全区适配。
3. 统一“选择限制应用”页面的搜索、过滤、列表项和底部操作栏。
4. 删除普通 UI 中独立的“高级手动添加包名”入口。
5. 将手动包名能力整合进搜索框的疑似包名结果。
6. 将普通首页的调试日志模块隐藏到开发者模式解锁之后。
7. 更新版本号到 `0.5.2` / `versionCode=9`，生成众测候选 APK。

## 根因分析

### 1. 普通界面说明文案过重

根因：v0.5.1 为了验证功能，把实现说明、版本说明和规则说明直接放在主页或二级页。众测候选版中，这些内容会让界面像调试面板。

修复：删除主页顶部版本说明长句；目标应用模块只显示“当前限制应用数”；今日额度锁定文案删除跨天默认值说明；普通首页不再显示调试日志模块。

### 2. 顶部区域可能贴近状态栏或前置摄像头

根因：主界面和二级页只使用固定 padding，没有使用 Compose 系统安全区。

修复：主界面和二级页外层加入 `statusBarsPadding()`；二级页额外加入 `navigationBarsPadding()`，避免底部操作栏贴近系统导航区域。

### 3. 选择页 UI 不统一

根因：过滤按钮没有等宽布局，搜索框占用偏高；应用行把类型和来源拼成长句，容易被挤压；底部区域含“保存按钮固定”这类开发说明。

修复：过滤按钮等宽；搜索框压缩为 48dp 高；应用行改为图标、名称、包名、横向短标签、勾选框；底部只保留“已选 X”和保存按钮，并用顶部细分隔线处理区域边界。

### 4. 手动包名入口不适合普通用户长期可见

根因：v0.5.1 在二级页单独展示“高级手动添加包名”，普通用户会把它当成功能入口，增加误用风险。

修复：删除独立入口。用户在搜索框输入疑似包名且列表无对应包名时，才显示“添加包名：xxx”。新增应用仍走原 `GuardRepository.addCandidatePackage()`，继续按用户新增纯限制类和今日锁定规则处理。

## 修改过的文件

- `app/build.gradle.kts`
- `app/src/main/java/com/example/focusgate/MainActivity.kt`
- `DEBUG_REPORT.md`

## 核心修改点

### 主页

- 删除顶部说明文字。
- 版本号移动到“高级设置”展开内容。
- 目标应用模块改为：`当前限制应用数：X`。
- 今日额度锁定文案改为：`今日额度已锁定，今天不能再次修改。`
- 普通首页不再展示调试日志模块；解锁开发者模式后才显示。

### 顶部安全区

- 主界面：`statusBarsPadding()` + 少量顶部 padding。
- 选择页：`statusBarsPadding()` + `navigationBarsPadding()`。
- 不改悬浮窗、权限页跳转或服务逻辑。

### 选择限制应用页

- 搜索框 placeholder 改为 `搜索应用`，高度压缩到 48dp。
- 过滤按钮等宽显示：全部 / 已选 / 用户 / 内置。
- 应用行标签改为横向胶囊：`资料+娱乐`、`纯限制`、`内置`、`用户`、`明日可取消`。
- 底部区域删除说明句，只显示 `已选 X` 和 `保存`。
- 保留底部保存按钮固定，不改变原有保存逻辑。

### 包名添加

- 删除独立“高级手动添加包名”输入区。
- 新增 `looksLikePackageName()` 判断。
- 搜索框输入类似 `com.example.app` 且列表没有对应包名时，显示 `添加包名：com.example.app`。

## 构建结果

已运行：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

结果：

- Debug 构建成功。
- Release 构建成功。
- Release 产物为 unsigned APK。

APK：

- Debug：`current\app\build\outputs\apk\debug\app-debug.apk`
- Debug SHA256：`5BDAD1C36B719BF5677F8C86A2B7519A5916F2F741E5C58D4BA368A9BEF0FD99`
- Release unsigned：`current\app\build\outputs\apk\release\app-release-unsigned.apk`
- Release SHA256：`54F6DD9B189CA4DC1E009045345906410FDB9FDFF98508CD491B88FE276DDE95`

## 手动验证步骤

### 主页文案

1. 打开主界面。
2. 确认顶部不显示长版本说明。
3. 确认目标应用模块只显示当前限制应用数和入口。
4. 确认今日额度锁定文案没有跨天默认值说明。
5. 未进入开发者模式时，首页不显示调试日志模块。

### 顶部安全区

1. 在普通直屏手机打开主页。
2. 检查标题不贴状态栏。
3. 打开“选择限制应用”页，检查标题、返回按钮和搜索框不贴状态栏。
4. 若有挖孔屏或水滴屏，检查前置摄像头区域不遮挡标题。
5. 检查底部保存栏不贴系统导航栏。

### 选择限制应用页

1. 进入“选择限制应用”。
2. 检查“内置”标签横向显示。
3. 检查底部只显示“已选 X”和“保存”。
4. 搜索应用名和包名。
5. 切换全部 / 已选 / 用户 / 内置。
6. 搜索框输入疑似包名，例如 `com.example.demo`，确认出现添加包名选项。
7. 点击添加，确认应用进入列表并遵守今日新增锁定。
8. 保存后返回主页，确认限制应用数量刷新。

### 核心功能回归

1. 小红书仍显示资料查找和娱乐。
2. Bilibili 仍显示资料查找和娱乐。
3. 抖音不显示资料查找。
4. 用户新增 App 不显示资料查找。
5. 今日新增 App 当天不能取消。
6. 娱乐倒计时通知和悬浮圆圈不受影响。
7. 长按结束娱乐仍正常。
8. 5 连击 `FocusGate` 标题后，开发者模式仍可进入。

## 众测前注意事项

- 众测 APK 若使用 debug 包，安装时会显示调试签名，不适合作为正式发布包。
- Release 产物当前是 unsigned，若发给普通用户安装，需要用 Android Studio 或签名脚本签名。
- 仍未完成真机多屏幕尺寸视觉验收。
- Android 厂商后台限制、无障碍服务被系统关闭、悬浮窗权限关闭等问题仍需要用户手动处理。
- 固定开发者密钥不是强安全机制，不适合正式公开分发。

## 下一步建议

1. 做一次真机截图验收，覆盖普通屏、挖孔屏、水滴屏。
2. 为众测包增加正式签名流程和固定 APK 命名规则。
3. 若众测反馈 UI 稳定，再考虑清理旧调试文案和遗留技术状态代码。

---

# v0.5.3 应用选择性能、长按与权限异常提醒

日期：2026-07-10

## 截图问题定位

- 截图中的“全 / 已 / 用 / 内”对应 `TargetFilterButton`。四个按钮等分一行后，Material Button 默认水平内边距继续占宽，两个汉字只能逐字换行。
- 底部浅紫色矩形来自保存栏外层 `Surface(tonalElevation = 2.dp)` 的主题色调，不是 Checkbox、空 Button 或截图伪影。
- 修复方式：筛选按钮使用 6dp 水平内容内边距并强制单行；删除保存栏外层色调 Surface，保留原有固定布局、已选数量和保存按钮。

## 二级页性能根因与修复

- 根因是 `TargetAppPickerScreen` 首次组合时同步调用 `installedLaunchableApps()`，在主线程查询 PackageManager、读取 112 个应用标签并加载全部图标，页面框架必须等待这些工作完成。
- 页面现在先用空列表或内存元数据缓存渲染标题、搜索框、筛选栏和保存栏。
- PackageManager 查询、标签读取和排序在 `Dispatchers.IO` 执行；图标只为 LazyColumn 当前可见项异步加载，并按包名做进程内缓存。
- 每次进入仍在后台刷新元数据，因此安装/卸载应用不会被永久缓存遮蔽；二次进入先显示缓存，再接收刷新结果。
- 搜索、筛选和行合并结果使用稳定 key 与 `remember`，不再因无关重组重复构建整表。

真机 PKT110，112 个 Launcher 应用：

- 冷首次进入：页面框架 206ms，完整列表 448ms；PackageManager 查询 10ms，映射与排序 229ms，总后台读取 240ms。
- 同进程第二次进入：页面框架 132ms，完整列表 157ms；后台刷新 22ms。
- 首屏图标缓存汇总：6 次 miss、6 个缓存项；没有逐行刷日志。

## 长按结束娱乐

- `ENTERTAINMENT_END_HOLD_MS` 从 1500ms 改为 800ms。
- 触发判断和圆环进度仍共用同一常量；拖动、移出、抬手会取消进度，完成后只触发一次。
- `EntertainmentEndCircleView.performClick()` 已补齐，普通短按和无障碍点击继续只提示“长按结束娱乐”。

## 权限异常提醒

- 新增 `PermissionAlertCoordinator`，检查无障碍、悬浮窗、应用级通知总开关/运行时权限和前台服务运行状态；电池优化白名单只记为建议状态。
- 使用持久化异常签名做状态迁移与防重复；恢复正常后取消异常通知，后续再次关闭可重新提醒。
- 通知可用时提交合并异常通知，点击后打开主界面并自动展开异常权限模块。
- 通知权限关闭时不调用普通 `NotificationManager.notify()`；应用内显示红色说明，并仅在前台服务已经运行时尽力更新其通知内容。日志明确标记 `deliveryNotGuaranteed=true`。
- `PermissionUtils.canPostNotifications()` 同时检查 Android 13+ 运行时权限和 `NotificationManager.areNotificationsEnabled()`，覆盖 Android 12 及以下或厂商系统的应用级总开关。
- 检测点包括 Activity 恢复、通知授权结果、无障碍连接/中断/销毁、前台服务销毁，以及无障碍事件期间每 10 秒至多一次的低频检查。

真机边界测试：

- 关闭系统“允许通知”后，主界面显示红色降级说明。
- 日志记录 `ordinary notification blocked`、`inAppRedState=true`、`deliveryNotGuaranteed=true`。
- 再次启动同一异常记录 `duplicateSkipped=true`，没有重复提交。
- 测试结束后已恢复“允许通知”，运行时权限为 `granted=true`。

## 构建、Lint 与测试

- `:app:assembleDebug`：成功。
- `:app:assembleRelease`：成功，产物未签名。
- `:app:lintDebug`：成功，0 error、9 个既有 warning；同时修复两个 minSdk 26 下调用 API 28 Handler 重载的历史错误。
- `:app:testDebugUnitTest`：NO-SOURCE，项目仍无单元测试源码。
- Debug APK SHA256：`2223183A3090FC9781BC3BC3274307BE8886CDEA39F9024C1F298CAF6F057FC6`。
- Release unsigned APK SHA256：`9B23D414BAB161C505A09437E22FA0E8C4B864FB74B8445524BEAFA558A1F79E`。

## 尚需人工回归

- 800ms 长按的手感、半途松手、快速拖动和只触发一次，需要在真实娱乐倒计时状态逐项操作。
- 小红书/Bilibili 资料查找、抖音纯限制、跨日取消锁和娱乐倒计时需要完整业务回归；本轮未修改这些状态机。
- Release APK 未签名，不能作为正式分发包直接安装。
# SearchGate Debug Report - v0.5.4 Quota Picker and Persistent Developer Mode

日期：2026-07-11

## 本轮结果

- 今日额度次数和单次时长改为整数滚轮；滚动仅修改临时界面状态，确认后才写入今日快照。
- 开发者模式增加持久化的次数上限（1-100，默认 10）和分钟上限（1-180，默认 15）。普通额度滚轮按该上限动态收缩，历史默认值安全截断；已确认的当天快照不被自动改写。
- 今日额度页增加“添加更多应用”，复用已有选择页；返回后保留未确认的滚轮值，保存应用与确认额度仍是两次独立操作。
- 开发者模式改为 SharedPreferences 单一持久状态，只有主动退出才关闭；主页面和应用选择页标题右侧显示小型状态标签。
- 今日额度页面改为次数、时长、限制应用、确认四个轻量模块，压缩说明文字。
- 娱乐结束浮窗使用透明 68dp 根容器、48dp 渐变圆体、抗锯齿 Canvas、圆头进度环；长按视觉缩放提高到 1.18。拖动仍只更新 WindowManager 坐标，位置仍在抬手后保存。
- 保留通知权限关闭时不发送普通异常通知的边界，只依赖应用内红色状态、已有前台通知的尽力更新和再次打开应用后的提示。

## 自动验证

- `:app:testDebugUnitTest`：通过，11 项测试。
- `:app:assembleDebug`：通过。
- `:app:lintDebug`：通过，0 error、9 warning（均为既有本地化/SDK 建议类警告）。
- `:app:assembleRelease`：通过；产物为 unsigned APK。
- 中文项目路径会导致本机 JUnit worker 扫描到测试类却无法加载；通过只指向原工程的 `C:\Users\LittleTaro\codex-searchgate-ascii` 目录联接完成测试，没有复制第二份源码。

## APK

- Debug SHA256：`653CC864040EF739EA80567D3A18E292A01A9A793A6B5FBC84A503831B2C0870`
- Release unsigned SHA256：`68F10D0FEF5C944CEA768F5BFA55ABBC58DECF7186419ADA15E5F0C848CB9997`

## 必须真机验证

- 滚轮中心高亮、吸附手感、小屏/大字体布局、进入选择页再返回后的临时值显示。
- Home 键、锁屏、系统杀进程、手机重启后的开发者模式恢复与标签显示。
- 浮窗圆边锯齿、方形边界、圆环裁剪、1.18 倍长按反馈、长按/短按/拖动互斥、拖动帧率、横竖屏和手势区遮挡。
- 通知权限关闭/恢复、权限红色状态、前台服务既有通知更新、娱乐倒计时和长按结束完整链路。
- 小红书/Bilibili 的资料+娱乐、抖音/用户新增应用的纯限制，以及今日新增应用次日才可取消。

---
# SearchGate Debug Report - v0.6.0 Developer Center Refactor

日期：2026-07-12

## 结构性改动

- 将开发者功能从“高级设置”渲染路径中拆出，通过 FocusGate 右侧可点击状态标签进入独立“开发者”页面。
- 页面分为只读“状态”和“功能调整”；状态包含权限、构建、今日额度、状态机、查询、应用分类和最近关键事件。
- 功能调整按开关、额度、状态重置、工具分类。原有总限制、额度上限、今日额度/已用次数、娱乐/查询/弹窗清理、应用锁和用户新增应用清理能力均已迁移。
- 总限制和额度数值在每次明确点击后立即写入 Repository，并在成功后刷新；失败时重新读取持久状态并显示错误，不再需要统一保存按钮。
- 危险重置统一使用二次确认对话框。

## 滚轮重构

- 两个独立 120dp 滚轮卡片合并为并排紧凑额度卡片，单个滚轮视觉区为 104dp。
- 保留原生 NumberPicker 整数吸附，单位改为滚轮下方固定显示，避免每项重复单位。
- 新增 GuardedNumberPicker：中央 12%-88% 为有效起手区；超过 touchSlop 后，纵向手势归滚轮，横向手势归页面；ACTION_UP、ACTION_CANCEL 和 View 分离都会释放父级拦截锁。
- 临时额度仍由 rememberSaveable 保留，只在确认今日额度时写入正式快照。

## 自动验证

- testDebugUnitTest：15 项通过，0 失败。
- assembleDebug：通过。
- lintDebug：通过，0 error、9 warning；警告均为既有依赖更新、绘制分配、SDK 判断及本地化建议。
- assembleRelease：通过；产物为 unsigned APK。
- 本轮未连接设备、未启动 adb、未进行真机或模拟人工操作。

## APK

- Debug SHA256：`BF34F056074105DF874267AD48E2C0744AA2B4341CAB5AA214A7C9B86398C2ED`
- Release unsigned SHA256：`8ADD7D177E3FDC4A2DFC35E14B97E793AB1F6F840FE884077037713A66496CF2`

## 用户自行真机验证

- 点击开发者标签是否进入独立页面，退出后入口是否隐藏，重启后模式和设置是否保持。
- 状态卡片在权限异常、娱乐进行中和应用数量变化后是否正确刷新。
- 所有立即生效操作、错误提示、危险重置二次确认是否符合预期。
- 小屏和大字体下开发者页面是否溢出或卡顿。
- 页面滚动经过滚轮边缘时是否误改值；从滚轮中央纵向拖动时页面是否保持不动。
- NumberPicker 吸附、快速滑动、双滚轮互不干扰，以及应用选择返回后的临时值保留。

---
# SearchGate Debug Report - v0.6.1 Back Navigation and Motion

日期：2026-07-13

## 返回逻辑

- 新增可单元测试的 UiBackStateResolver，统一优先级为：弹层、编辑态、最近展开模块、二级页面、根页面退出。
- 系统返回与页面软件返回按钮共用同一个 handleBack，不再分别修改页面布尔状态。
- 首页保留多模块展开能力，并维护最近展开历史；每次返回只收起一个最近模块。
- 强制展开的权限、未确认额度和娱乐状态模块不会被返回动作伪收起。
- 开发者中心返回先收起最近的状态/功能分组，再回首页；不会关闭 developerModeEnabled。
- 应用选择页搜索框聚焦时，第一次返回只清焦点和键盘；下一次才放弃未保存选择并回来源页，不自动保存。

## 动画

- 统一常量：短动画 160ms、模块动画 220ms、页面动画 260ms。
- 首页及开发者中心全部可折叠卡片使用 AnimatedVisibility 的高度和透明度过渡，并同步旋转展开箭头。
- HOME 与二级页使用 AnimatedContent 的轻量水平位移和淡入淡出；返回方向与进入方向相反。
- 业务状态在动画开始前更新，不依赖动画结束回调，不执行 IO、图标加载或逐帧日志。
- 未引入实验性预测性返回 API；继续使用 OnBackPressedDispatcher/BackHandler，保证系统返回手势逻辑一致，不提供完整预测性视觉预览。

## 自动验证

- testDebugUnitTest：23 项通过，0 失败。
- assembleDebug：通过。
- lintDebug：通过，0 error、9 warning。
- assembleRelease：通过；产物为 unsigned APK。
- 本轮未连接设备、未启动 adb、未进行真机或模拟人工操作。

## APK

- Debug SHA256：`9DC200781709CD709E387C416D5AE35CEC29FAE5E6C5F7EC99F44F7BAB66CA06`
- Release unsigned SHA256：`B8F901BA32947E9DEECA11E40EEF3BC4ECFFBD4FCB0636EB56FABEA0E5230B4C`

## 公开仓库风险

- 发行版开发者入口继续使用项目原有硬编码固定密钥；该值已经存在于公开历史中，不是安全边界。本轮按约束未删除或改造，但正式公开发行前应迁移为更安全的授权机制。
- `.gitignore` 已覆盖 local.properties、构建目录、APK、captures 和 IDE 缓存；本轮 diff 未加入 token、私钥、日志样本或新的个人绝对路径。

---
# SearchGate Debug Report - v0.6.2 Smooth Collapse Tail

日期：2026-07-13

## 根因

- 首页 CollapsibleSection 外层 Column 使用 `spacedBy(10.dp)`，开发者卡片外层使用 `spacedBy(9.dp)`；标题行和 AnimatedVisibility 是两个子项。
- 收起过程中 AnimatedVisibility 高度平滑降为 0，但父 Column 的固定子项间距仍存在；退出动画结束、内容真正离开组合时，该 9–10dp 间距才瞬间移除，形成稳定可复现的末帧二次高度跳变。
- 项目不存在 animateContentSize 与 shrinkVertically 的高度动画叠加；没有 spring、finishedListener、LaunchedEffect(expanded)、末尾数据清空或自动滚动。
- 模块持久化使用 SharedPreferences.apply()，不是同步 commit；它不等待动画完成，也不是末尾卡顿根因。

## 修复

- 新增公共 SmoothCollapsibleContent，首页和开发者中心共用同一 AnimatedVisibility。
- 外层标题容器不再使用固定 spacedBy；标题—内容间距移动到 AnimatedVisibility 内部，间距与内容一起从完整高度连续收缩到 0。
- 同一高度只由 expandVertically/shrinkVertically 控制；删除首页折叠内容额外的 slideIn/slideOut，保留不参与布局的淡入淡出。
- 展开 240ms、收起 200ms、箭头 200ms、淡出 160ms，均使用确定时长 tween；fadeOut 不长于高度收起。
- 内存展开状态仍先更新，持久化随后调用异步 apply；业务逻辑不等待动画完成。

## 自动验证

- testDebugUnitTest：25 项通过，0 失败。
- assembleDebug：通过。
- lintDebug：通过，0 error、9 warning。
- assembleRelease：通过；产物为 unsigned APK。
- 新增目标模块隔离和快速反向最终状态测试；既有返回优先级、开发者持久化、额度和应用锁测试继续通过。
- 本轮未连接设备、未启动 adb、未进行真机或模拟人工测试。

## APK

- Debug SHA256：`27056E08862546BDEBE2547E6558F71799CC9D8412726E38F10F58A77A5C3A3B`
- Release unsigned SHA256：`55531939D47CA826A6546B0032AA201DD6A537C4565CA8FCCAC48B4D0C2FF6FE`

---

# SearchGate Debug Report - v0.6.3 Daily Plan Overlay Height Fix

日期：2026-07-13

## 设置今日额度页面严重布局问题

### 截图观察与精确代码定位

- 应用列表无限向下铺开：截图对应 `OverlayController.showDailyEntertainmentPlanSetup()`。原实现为每个目标应用直接 `root.addView(CheckBox)`，并显示“应用名 + 换行 + 包名”；没有应用容器上限。
- 顶部说明被挤压：标题、长说明、全部应用、两组说明与滚轮都被连续插入同一个 `baseLayout()`。
- 两个滚轮裁切或重叠：`baseLayout()` 是 `gravity = CENTER` 的普通纵向 `LinearLayout`，没有外层滚动；两个原生 `NumberPicker` 纵向连续加入，窗口高度不足时没有独立最小可操作视口。
- 确认按钮被推到屏外：确认与返回按钮同样是普通纵列末尾子项，没有固定操作区和内容底部预留。
- 安全区不足：日计划悬浮窗沿用 `FLAG_LAYOUT_NO_LIMITS`，没有专用 WindowInsets 处理。
- 该截图**不是** Compose 首页 `MainActivity` 的问题，而是无障碍服务悬浮窗的 View 路径。

### 修改后的页面层级

```text
FrameLayout（安全区适配）
├── 外层 NestedScrollView
│   └── 内容 LinearLayout
│       ├── 顶部提示卡
│       ├── 受控制的应用卡：限高内部滚动的 ChipFlow
│       └── 今日额度卡：同一行的两个 GuardedNumberPicker
└── 固定底部操作栏：确认今天规则 + 返回桌面
```

## 已选应用模块

- 大模块：圆角轻量卡片，标题为“受控制的应用”，显示当前选中数量。
- 小模块：`CompactChipFlowLayout` 生成只显示应用名称的单行 Chip；不显示包名、路径、类型说明或内部信息。超长名称省略，最大宽度 220dp。
- 选择逻辑保留：点击 Chip 仍切换今天的受控应用集合；`confirmTodayEntertainmentPlan`、`setTodayControlledPackages`、当天额度锁和新增 App 次日可取消规则未改动。
- 最大高度：`DailyPlanOverlayLayoutPolicy.SELECTED_APP_MAX_HEIGHT_DP = 180`。内容不足时 `BoundedNestedScrollView` 按内容高度显示；超过上限时仅该模块内部滚动。
- 嵌套滚动：外层和内部均使用 `NestedScrollView`，内部启用 nested scrolling；内层滚到边缘后由嵌套滚动机制交还外层。

## 双滚轮布局

- 横向布局：同一“今日额度”卡片的 `LinearLayout.HORIZONTAL`，两个子列以相同 weight 平分可用宽度，中间使用窄分隔线。
- 每个滚轮高度：112dp；标题和单位固定在各自子列顶部，滚轮项只显示数字，避免每项重复单位。
- 触摸：日计划悬浮窗从普通 `NumberPicker` 改为既有 `GuardedNumberPicker`。中央起手区、touchSlop、纵向归滚轮、横向/非交互起手归页面、ACTION_UP/ACTION_CANCEL 释放父级拦截规则均保留。
- 两个滚轮各自独立；不在滚轮滚动时写入 Repository，仍只在“确认今天规则”后生成当天快照。

## 顶部提示与底部确认

- 原结构：悬空标题加长说明文字。
- 新结构：紧凑标题卡，仅保留“设置今日规则”和一行副标题；当天额度已锁定时显示“额度已确认，可调整控制应用”。
- 固定底栏：`FrameLayout` 底部承载确认和返回操作，确认按钮不属于滚动内容。
- 安全区与内容预留：日计划悬浮窗不再使用 `FLAG_LAYOUT_NO_LIMITS`；WindowInsets 更新顶部与导航栏内边距，滚动内容按底栏**实际测量高度**加间距预留空间，没有写死设备屏幕高度。

## 自动验证与构建

- `:app:compileDebugKotlin`：通过。
- `:app:testDebugUnitTest`：27 项通过，0 失败；新增标签区高度上限、内部滚动阈值、双滚轮槽位和底栏空间测试。
- `:app:lintDebug`：通过，0 error、9 warning（既有依赖更新、SDK/本地化建议）。
- `:app:assembleDebug`：通过。
- `:app:assembleRelease`：通过，产物为 unsigned APK。
- 本轮未连接 adb、未启动模拟器、未进行真机或远程设备测试。

## APK

- Debug：`app/build/outputs/apk/debug/app-debug.apk`
- Debug SHA256：`5AD222CF1B480819A167E70CB19B357D3194AB5C028269D868B9BA719A58096C`
- Release unsigned：`app/build/outputs/apk/release/app-release-unsigned.apk`
- Release SHA256：`BB3DE4CE0B8E11E889A1EFEE52DACE3E349F76DF85F4E84FE291A6DEBF474B75`

## 用户自行真机验证

1. 分别选择 1、5、10 和 20 个以上应用，确认应用区域不会无限增高。
2. 在应用区域内滑动，确认超过两到四行后内部可滚；滑到边缘后外层页面可继续滚动。
3. 确认 Chip 只显示名称，超长名称省略而不越界。
4. 确认两个滚轮同一行、均完整显示；操作左侧不会改动右侧。
5. 从滚轮中央纵向滑动时页面不跟随；在滚轮外滑动页面时数值不改变。
6. 检查底部“确认今天规则”始终可见，且不被手势导航栏、三键导航栏或软键盘遮挡。
7. 在字体放大、小屏、挖孔屏/水滴屏和横屏下检查文字、Chip 和滚轮是否裁切。
8. 确认当天额度确认后仍不可再次修改，今日新增应用仍要到下一本地日期才能取消。

## 剩余风险

- 大量 Chip 的内部与外层嵌套滚动手感需要真实触控验证。
- 220dp 超长名称上限、系统字体放大和极窄屏幕可能需要后续视觉微调。
- 两个并排滚轮在不同 OEM NumberPicker 实现、WindowInsets 策略和导航模式下仍需人工回归。
- Release APK 未签名，不能作为正式公开分发包。
