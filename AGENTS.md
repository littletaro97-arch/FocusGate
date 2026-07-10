# SearchGate Current 工作规则

这是当前 Android 工程目录。开始修改前，请先阅读项目总目录中的：

```text
..\AGENTS.md
..\CURRENT_STATUS.md
..\README_VERSION_RULES.md
..\changelog.md
```

强制规则：

- 所有代码修改在本目录进行。
- 每轮修改完成后，必须在 `..\versions` 中新增版本归档。
- 不得覆盖或删除旧版本。
- 每次向用户汇报成果必须带当前版本号。
- 构建命令：

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
```

