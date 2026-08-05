package com.rootes.app

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class WelcomeActivity : ComponentActivity() {
    // 和StartActivity完全一致的配置，保证状态互通
    private val PRIVACY_PREF_NAME = "privacy_policy_pref"
    private val KEY_HAS_AGREED = "has_agreed_privacy_policy"
    private lateinit var sharedPreferences: SharedPreferences
    // 接收从启动页传递过来的目标主页
    private var targetHomeClassName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // PixelOS 全屏渲染，兼容Android 11+，低版本自动跳过不报错
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
        sharedPreferences = getSharedPreferences(PRIVACY_PREF_NAME, MODE_PRIVATE)

        // 保留原有兜底逻辑，彻底解决找不到类的报错
        targetHomeClassName = intent.getStringExtra("TARGET_HOME_ACTIVITY")
        if (targetHomeClassName.isNullOrEmpty()) {
            targetHomeClassName = "com.rootes.app.MainActivity"
        }

        setContent {
            MaterialTheme {
                WelcomeScreen(
                    onAgree = {
                        sharedPreferences.edit().putBoolean(KEY_HAS_AGREED, true).apply()
                        goToTargetHome()
                    },
                    onRefuse = {
                        finish()
                    }
                )
            }
        }
    }

    // 跳转目标主页，清空返回栈，禁止用户返回欢迎页
    private fun goToTargetHome() {
        try {
            val targetClass = Class.forName(targetHomeClassName)
            startActivity(Intent(this, targetClass).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        } catch (e: Exception) {
            // 异常兜底，直接跳配置的默认主页
            try {
                val fallbackClass = Class.forName("com.rootes.app.MainActivity")
                startActivity(Intent(this, fallbackClass).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            } catch (e: Exception) {
                finish()
            }
        }
    }
}

// Google PixelOS 原生风格欢迎界面（修复编译报错版）
@Composable
fun WelcomeScreen(onAgree: () -> Unit, onRefuse: () -> Unit) {
    var isAgreeChecked by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // PixelOS 原生柔和渐变背景（符合Material You规范）
    val backgroundGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFFF8FAFC),
            Color(0xFFF1F5F9)
        )
    )

    // Pixel 官方核心配色
    val pixelPrimaryBlue = Color(0xFF1A73E8)
    val pixelNeutralDark = Color(0xFF0F172A)
    val pixelNeutralMedium = Color(0xFF334155)
    val pixelNeutralLight = Color(0xFF64748B)
    val pixelCardBg = Color(0xFFF1F5F9)
    val pixelOutlineColor = Color(0xFFCBD5E1)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
            // 修复1：替换systemBarsPadding为通用方案，兼容所有Compose版本，解决找不到引用报错
            .windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .padding(top = 72.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部：Pixel 风格图标+标题区域
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Pixel 原生圆形应用图标（可替换为你的App Icon）
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            color = pixelPrimaryBlue,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "R",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "欢迎使用",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = pixelNeutralDark,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "请先阅读并同意服务政策",
                    fontSize = 16.sp,
                    color = pixelNeutralLight,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }

            // 中间：隐私政策内容卡片
            PrivacyPolicyContent(
                scrollState = scrollState,
                cardBg = pixelCardBg,
                textDark = pixelNeutralDark,
                textMedium = pixelNeutralMedium
            )

            // 底部：合规勾选+操作按钮区域
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 合规勾选框
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Checkbox(
                        checked = isAgreeChecked,
                        onCheckedChange = { isAgreeChecked = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = pixelPrimaryBlue,
                            uncheckedColor = pixelNeutralLight,
                            checkmarkColor = Color.White
                        ),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "我已阅读并同意个人信息保护与用户同意政策",
                        fontSize = 14.sp,
                        color = pixelNeutralMedium,
                        lineHeight = 20.sp
                    )
                }

                // Pixel 风格双按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 次要按钮：不同意
                    OutlinedButton(
                        onClick = onRefuse,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(28.dp),
                        // 修复2：修正BorderStroke参数错误，把color改为brush，解决找不到参数报错
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            width = 1.dp,
                            brush = SolidColor(pixelOutlineColor)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = pixelNeutralMedium
                        )
                    ) {
                        Text(
                            text = "不同意",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // 主要按钮：同意并进入
                    Button(
                        onClick = onAgree,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        enabled = isAgreeChecked,
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = pixelPrimaryBlue,
                            disabledContainerColor = Color(0xFFE2E8F0),
                            contentColor = Color.White,
                            disabledContentColor = Color(0xFF94A3B8)
                        )
                    ) {
                        Text(
                            text = "同意并进入",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// 隐私政策内容组件
@Composable
private fun PrivacyPolicyContent(
    scrollState: ScrollState,
    cardBg: Color,
    textDark: Color,
    textMedium: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(
                color = cardBg,
                shape = RoundedCornerShape(24.dp)
            )
            .padding(20.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "个人信息保护与用户同意政策",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = textDark
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "为切实保障用户合法权益，严格遵循个人信息保护相关法律法规，本应用制定本政策，全程规范个人信息收集、使用及权限管理等行为，杜绝各类违规操作。\n\n" +
                    "本应用隐私政策内容清晰规范，全面明示个人信息收集范围、使用目的、存储期限及保护措施，无模糊隐瞒、晦涩难懂条款。用户同意政策采用自主勾选模式，绝不设置默认勾选同意选项，充分保障用户自主选择权。\n\n" +
                    "我们严格依规使用用户个人信息，仅用于承诺的服务场景，绝不违规泄露、滥用或私自共享给第三方。权限申请遵循最小必要原则，仅申请服务必需权限，明确标注每项权限申请目的，拒绝强制、频繁、过度索取权限，用户拒绝非必要权限不影响核心功能使用。\n\n" +
                    "应用推广全程合规，无虚假宣传、欺骗误导用户下载的行为，不强制用户使用定向推送功能，提供便捷的推送关闭渠道，充分尊重用户意愿。应用分发时，主动明示权限、隐私政策、服务资费等全部关键信息，保障用户知情权与选择权。\n\n" +
                    "本应用坚守合规运营底线，切实守护用户个人信息安全。",
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = textMedium
        )
    }
}
