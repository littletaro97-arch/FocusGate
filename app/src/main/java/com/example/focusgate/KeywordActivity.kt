package com.example.focusgate

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class KeywordActivity : ComponentActivity() {
    private lateinit var repository: GuardRepository
    private var targetPackage: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = GuardRepository(this)
        targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty()
        val initialPlatform = TargetPlatform.fromPackageOrNull(targetPackage)
        val initialConfig = repository.targetAppConfig(targetPackage)
        Log.i(FocusGateLog.TAG, "keyword activity opened target=$targetPackage")

        setContent {
            AppSurface {
                if (initialPlatform == null || initialConfig?.allowStudyLookup != true) {
                    UnsupportedSearchScreen(
                        appName = repository.appDisplayName(targetPackage),
                        onCancel = {
                            Log.i(FocusGateLog.TAG, "keyword action=cancel target=$targetPackage")
                            repository.markSearchReturnCheck(targetPackage)
                            finish()
                        }
                    )
                } else {
                    KeywordScreen(
                        initialPlatform = initialPlatform,
                        platforms = TargetPlatform.entries.filter {
                            repository.targetAppConfig(it.packageName)?.allowStudyLookup == true
                        },
                        onSearch = { keyword, platform ->
                            Log.i(FocusGateLog.TAG, "keyword action=search target=${platform.packageName} keyword=$keyword")
                            repository.startSearch(platform.packageName, keyword, platform.displayName)
                            val result = DeepLinkLauncher.openSearch(this, platform, keyword)
                            repository.saveDeepLinkResult(result)
                            if (result == DeepLinkResult.STARTED) {
                                finish()
                            } else if (DeepLinkFailureOverlay.show(this, platform.packageName, repository)) {
                                finish()
                            } else {
                                setContent {
                                    AppSurface {
                                        DeepLinkFailureScreen(
                                            platform = platform,
                                            onHome = { goHome() },
                                            onManual = {
                                                Log.i(FocusGateLog.TAG, "keyword failure action=manual_open target=${platform.packageName}")
                                                repository.allowManualOpen(platform.packageName)
                                                DeepLinkLauncher.openPackage(this, platform.packageName)
                                                finish()
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        onCancel = {
                            Log.i(FocusGateLog.TAG, "keyword action=cancel target=$targetPackage")
                            repository.markSearchReturnCheck(targetPackage)
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun goHome() {
        Log.i(FocusGateLog.TAG, "keyword action=home target=$targetPackage")
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "targetPackage"
    }
}

@Composable
private fun UnsupportedSearchScreen(
    appName: String,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("资料查找", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("$appName 不支持资料查找入口。纯限制类 App 只能使用娱乐额度，不能通过资料查找绕过限制。")
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("取消")
        }
    }
}

@Composable
private fun KeywordScreen(
    initialPlatform: TargetPlatform,
    platforms: List<TargetPlatform>,
    onSearch: (String, TargetPlatform) -> Unit,
    onCancel: () -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf(initialPlatform) }
    var error by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("资料查找", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = keyword,
            onValueChange = {
                keyword = it
                error = ""
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("资料关键词") },
            singleLine = true
        )
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)

        Text("目标平台", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            platforms.forEach { item ->
                if (item == platform) {
                    Button(onClick = { platform = item }) { Text(item.displayName) }
                } else {
                    OutlinedButton(onClick = { platform = item }) { Text(item.displayName) }
                }
            }
        }

        Button(
            onClick = {
                val cleanKeyword = keyword.trim()
                if (cleanKeyword.isEmpty()) {
                    error = "关键词不能为空"
                } else {
                    onSearch(cleanKeyword, platform)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("开始搜索")
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("取消")
        }
    }
}

@Composable
private fun DeepLinkFailureScreen(
    platform: TargetPlatform,
    onHome: () -> Unit,
    onManual: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("无法直接打开搜索页", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("${platform.displayName} 当前版本可能不支持这条 Deep Link。你可以返回桌面，或短暂允许手动打开后自行搜索。")
        Button(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
            Text("允许手动打开搜索")
        }
        OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) {
            Text("返回桌面")
        }
    }
}
