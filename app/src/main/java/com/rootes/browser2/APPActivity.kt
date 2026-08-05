package com.rootes.browser2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.rootes.app.FormActivity
import com.rootes.app.TimeActivity

class AppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                IosStyleCardListScreen()
            }
        }
    }
}

// 数据模型
data class MenuItem(
    val title: String,
    val onClick: () -> Unit
)

data class MenuGroup(
    val groupName: String?,
    val items: List<MenuItem>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosStyleCardListScreen() {
    val context = LocalContext.current
    val bgUrl = "https://api.sretna.cn/api/anime.php"
    val isDark = isSystemInDarkTheme()

    // 和设置页保持一致的配色
    val cardBgColor = if (isDark) Color(0xCC1C1C1E) else Color(0xEEFFFFFF)
    val topBarBgColor = if (isDark) Color(0xBB1C1C1E) else Color(0xBBF8F8F8)
    val maskColor = if (isDark) Color(0x66000000) else Color(0x22000000)
    val dividerColor = if (isDark) Color(0x33FFFFFF) else Color(0x1A000000)
    val textPrimaryColor = if (isDark) Color.White else Color.Black
    val textSecondaryColor = if (isDark) Color(0xFF8E8E93) else Color(0xFF636366)

    val menuGroups = remember {
        listOf(
            MenuGroup(
                groupName = null,
                items = listOf(
                    MenuItem("历史") {
                        context.startActivity(Intent(context, TimeActivity::class.java))
                    },
                    MenuItem("收藏夹") {
                        context.startActivity(Intent(context, FormActivity::class.java))
                    },
                )
            ),
            MenuGroup(
                groupName = "游戏",
                items = listOf(
                    MenuItem("Minecraft") { showToast(context, "Minecraft") },
                    MenuItem("原神") { showToast(context, "原神") },
                )
            ),
            MenuGroup(
                groupName = "个性化服务",
                items = listOf(
                    MenuItem("音乐") { showToast(context, "音乐") },
                    MenuItem("FPS") { showToast(context, "FPS") },
                    MenuItem("倒计时") { showToast(context, "倒计时") },
                    MenuItem("悬浮窗浏览器") { showToast(context, "悬浮窗浏览器") },
                )
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景 + 模糊 + 遮罩（和设置页完全一致）
        Image(
            painter = rememberAsyncImagePainter(model = bgUrl),
            contentDescription = "背景",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(radius = 12.dp)
        )
        Box(modifier = Modifier.fillMaxSize().background(maskColor))

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text("轻览", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = textPrimaryColor)
                    },
                    colors = TopAppBarDefaults.smallTopAppBarColors(
                        containerColor = topBarBgColor,
                        titleContentColor = textPrimaryColor
                    ),
                    modifier = Modifier.statusBarsPadding()
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                items(menuGroups) { group ->
                    group.groupName?.let {
                        Text(
                            text = it,
                            color = textSecondaryColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp)
                        )
                    }

                    // 用和设置页一样的卡片容器
                    SettingsGroupCard(bgColor = cardBgColor) {
                        group.items.forEachIndexed { index, item ->
                            MenuListItem(
                                title = item.title,
                                isLast = index == group.items.size - 1,
                                dividerColor = dividerColor,
                                textPrimaryColor = textPrimaryColor,
                                textSecondaryColor = textSecondaryColor,
                                onClick = item.onClick
                            )
                        }
                    }
                }
            }
        }
    }
}

// 复用设置页的卡片组件
@Composable
private fun SettingsGroupCard(
    bgColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        content()
    }
}

@Composable
fun MenuListItem(
    title: String,
    isLast: Boolean,
    dividerColor: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "ios_bounce_click"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ios_click_alpha"
    )

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .padding(20.dp, 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                title,
                fontSize = 16.sp,
                color = textPrimaryColor
            )
            Icon(
                Icons.Default.ArrowForwardIos,
                null,
                modifier = Modifier.size(12.dp),
                tint = textSecondaryColor
            )
        }
        if (!isLast) {
            Divider(
                modifier = Modifier.padding(start = 20.dp),
                thickness = 0.5.dp,
                color = dividerColor
            )
        }
    }
}

fun showToast(context: Context, msg: String) {
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}