package com.rootes.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Xml
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import com.rootes.browser2.unit.WebActivity
// 已修改为TimeActivity，用于浏览历史页面
class TimeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                HistoryListScreen()
            }
        }
    }
}

// 历史记录数据模型，对应XML中的visit节点
data class HistoryItem(
    val name: String,
    val url: String,
    val time: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryListScreen() {
    val context = LocalContext.current
    val bgUrl = "https://api.sretna.cn/api/anime.php"
    val isDark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()

    // 与原页面保持一致的配色体系
    val cardBgColor = if (isDark) Color(0xCC1C1C1E) else Color(0xEEFFFFFF)
    val topBarBgColor = if (isDark) Color(0xBB1C1C1E) else Color(0xBBF8F8F8)
    val maskColor = if (isDark) Color(0x66000000) else Color(0x22000000)
    val dividerColor = if (isDark) Color(0x33FFFFFF) else Color(0x1A000000)
    val textPrimaryColor = if (isDark) Color.White else Color.Black
    val textSecondaryColor = if (isDark) Color(0xFF8E8E93) else Color(0xFF636366)

    // 历史记录列表状态
    val historyList = remember { mutableStateOf<List<HistoryItem>>(emptyList()) }

    // 页面启动时加载本地XML历史记录
    LaunchedEffect(Unit) {
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                context.loadHistoryFromXml()
            }
            historyList.value = list
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 与原页面一致的背景+模糊+遮罩效果
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
                        Text(
                            "浏览历史",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            color = textPrimaryColor
                        )
                    },
                    // 新增返回按钮，点击关闭当前页面
                    navigationIcon = {
                        IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                            Icon(
                                Icons.Default.ArrowBackIosNew,
                                contentDescription = "返回",
                                tint = textPrimaryColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
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
                // 空状态展示
                if (historyList.value.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "暂无浏览历史",
                                color = textSecondaryColor,
                                fontSize = 15.sp
                            )
                        }
                    }
                } else {
                    // 历史记录列表
                    item {
                        SettingsGroupCard(bgColor = cardBgColor) {
                            historyList.value.forEachIndexed { index, item ->
                                HistoryListItem(
                                    title = item.name,
                                    subtitle = item.time,
                                    isLast = index == historyList.value.size - 1,
                                    dividerColor = dividerColor,
                                    textPrimaryColor = textPrimaryColor,
                                    textSecondaryColor = textSecondaryColor,
                                    onClick = {
                                        // 点击跳转WebActivity，传递url参数
                                        val intent = Intent(context, WebActivity::class.java).apply {
                                            putExtra("url", item.url)
                                        }
                                        context.startActivity(intent)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 复用原页面的卡片容器组件
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

// 适配历史记录的列表项组件，保留原iOS风格按压动画
@Composable
fun HistoryListItem(
    title: String,
    subtitle: String,
    isLast: Boolean,
    dividerColor: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // 原页面同款按压缩放动画
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "ios_bounce_click"
    )
    // 原页面同款按压透明度动画
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
                .padding(20.dp, 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    title,
                    fontSize = 16.sp,
                    color = textPrimaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = textSecondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Icon(
                Icons.Default.ArrowForwardIos,
                contentDescription = null,
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

/**
 * 从本地files目录的WEB_times.xml文件加载并解析历史记录
 * 按时间倒序排列（最新记录在最上方）
 */
private fun Context.loadHistoryFromXml(): List<HistoryItem> {
    val historyList = mutableListOf<HistoryItem>()
    try {
        // 读取files目录下的WEB_times.xml，无需完整路径，Android原生API自动定位
        openFileInput("WEB_times.xml").use { inputStream ->
            val parser = Xml.newPullParser()
            parser.setInput(inputStream, "UTF-8")
            var eventType = parser.eventType

            // 遍历XML节点，解析visit标签
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "visit") {
                            val name = parser.getAttributeValue(null, "name") ?: "未知页面"
                            val url = parser.getAttributeValue(null, "url") ?: ""
                            val time = parser.getAttributeValue(null, "time") ?: "未知时间"
                            // 仅保留有有效url的记录
                            if (url.isNotEmpty()) {
                                historyList.add(HistoryItem(name, url, time))
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        }
        // 倒序排列，最新浏览的记录在最上方
        historyList.reverse()
    } catch (e: Exception) {
        // 文件不存在、解析失败等异常处理，避免崩溃
        e.printStackTrace()
    }
    return historyList
}