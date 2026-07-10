package com.example.focusgate

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object DeepLinkFailureOverlay {
    fun show(context: Context, targetPackage: String, repository: GuardRepository): Boolean {
        val appContext = context.applicationContext
        if (!Settings.canDrawOverlays(appContext)) return false

        val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        var currentView: View? = null

        fun dismiss() {
            currentView?.let { runCatching { windowManager.removeView(it) } }
            currentView = null
        }

        fun dp(value: Int): Int = (value * appContext.resources.displayMetrics.density).toInt()

        fun title(text: String): TextView = TextView(appContext).apply {
            this.text = text
            setTextColor(Color.rgb(25, 31, 36))
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        }

        fun body(text: String): TextView = TextView(appContext).apply {
            this.text = text
            setTextColor(Color.rgb(55, 64, 70))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(20))
        }

        fun button(text: String, action: () -> Unit): Button = Button(appContext).apply {
            this.text = text
            textSize = 17f
            isAllCaps = false
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            ).apply {
                setMargins(0, dp(8), 0, dp(8))
            }
        }

        val platformName = when (targetPackage) {
            TargetPlatform.XHS.packageName -> TargetPlatform.XHS.displayName
            TargetPlatform.BILI.packageName -> TargetPlatform.BILI.displayName
            else -> targetPackage
        }

        val root = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                keyCode == KeyEvent.KEYCODE_BACK &&
                    (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP)
            }
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.rgb(247, 247, 242))
            addView(title("无法直接打开搜索页"))
            addView(body("$platformName 当前版本可能不支持这条 Deep Link。你可以返回桌面，或短暂允许手动打开后自行搜索。"))
            addView(button("允许手动打开搜索") {
                repository.allowManualOpen(targetPackage)
                DeepLinkLauncher.openPackage(appContext, targetPackage)
                dismiss()
            })
            addView(button("返回桌面") {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                appContext.startActivity(intent)
                dismiss()
            })
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        return runCatching {
            windowManager.addView(root, params)
            currentView = root
            root.requestFocus()
            true
        }.getOrDefault(false)
    }
}
