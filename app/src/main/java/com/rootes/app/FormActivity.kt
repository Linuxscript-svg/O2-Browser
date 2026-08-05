package com.rootes.app

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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer
import java.io.File

import com.rootes.browser2.unit.WebActivity

class FormActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                FavoritesScreen()
            }
        }
    }
}

// 收藏项数据模型
data class FavoriteItem(
    val name: String,
    val url: String
)

// 列表项数据模型
data class MenuItem(
    val title: String,
    val onClick: () -> Unit,
    val onLongClick: () -> Unit
)

data class MenuGroup(
    val groupName: String?,
    val items: List<MenuItem>
)

/**
 * 收藏夹XML解析扩展函数
 */
private fun Context.parseFavoritesXml(): List<FavoriteItem> {
    val favoritesFile = File(filesDir, "favorites.xml")
    if (!favoritesFile.exists() || !favoritesFile.canRead()) {
        return emptyList()
    }

    val favoriteList = mutableListOf<FavoriteItem>()
    try {
        favoritesFile.inputStream().use { inputStream ->
            val parser = Xml.newPullParser()
            parser.setInput(inputStream, "UTF-8")
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "item") {
                            val name = parser.getAttributeValue(null, "name")
                            val url = parser.getAttributeValue(null, "url")
                            if (!name.isNullOrEmpty() && !url.isNullOrEmpty()) {
                                favoriteList.add(FavoriteItem(name, url))
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return emptyList()
    }
    return favoriteList
}

/**
 * 删除收藏项核心函数
 */
private suspend fun deleteFavoriteItem(context: Context, itemToDelete: FavoriteItem): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val favoritesFile = File(context.filesDir, "favorites.xml")
            val currentFavorites = context.parseFavoritesXml()
            val newFavorites = currentFavorites.filterNot { 
                it.name == itemToDelete.name && it.url == itemToDelete.url 
            }

            // 重新写入XML，保持原格式
            favoritesFile.outputStream().use { outputStream ->
                val serializer: XmlSerializer = Xml.newSerializer()
                serializer.setOutput(outputStream, "UTF-8")
                serializer.startDocument("UTF-8", true)
                serializer.startTag(null, "favorites")
                
                newFavorites.forEach { item ->
                    serializer.startTag(null, "item")
                    serializer.attribute(null, "name", item.name)
                    serializer.attribute(null, "url", item.url)
                    serializer.endTag(null, "item")
                }
                
                serializer.endTag(null, "favorites")
                serializer.endDocument()
                serializer.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

// 全局Toast工具
private fun showToast(context: Context, msg: String) {
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen() {
    val context = LocalContext.current
    val bgUrl = "https://api.sretna.cn/api/anime.php"
    val isDark = isSystemInDarkTheme()

    // 状态管理
    var favoriteList by remember { mutableStateOf<List<FavoriteItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<FavoriteItem?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // 修复1：获取Compose协程作用域，替代lifecycleScope，解决未解析引用+挂起函数调用问题
    val coroutineScope = rememberCoroutineScope()

    // 配色体系
    val cardBgColor = if (isDark) Color(0xCC1C1C1E) else Color(0xEEFFFFFF)
    val topBarBgColor = if (isDark) Color(0xBB1C1C1E) else Color(0xBBF8F8F8)
    val maskColor = if (isDark) Color(0x66000000) else Color(0x22000000)
    val dividerColor = if (isDark) Color(0x33FFFFFF) else Color(0x1A000000)
    val textPrimaryColor = if (isDark) Color.White else Color.Black
    val textSecondaryColor = if (isDark) Color(0xFF8E8E93) else Color(0xFF636366)

    // 页面启动/刷新时解析XML
    LaunchedEffect(refreshTrigger) {
        isLoading = true
        withContext(Dispatchers.IO) {
            favoriteList = context.parseFavoritesXml()
        }
        isLoading = false
    }

    // 构建菜单分组
    val menuGroups = remember(favoriteList) {
        listOf(
            MenuGroup(
                groupName = "我的收藏",
                items = favoriteList.map { item ->
                    MenuItem(
                        title = item.name,
                        onClick = {
                            // 点击跳转逻辑不变
                            val intent = Intent(context, WebActivity::class.java).apply {
                                putExtra("url", item.url)
                            }
                            context.startActivity(intent)
                        },
                        onLongClick = {
                            // 长按触发删除对话框
                            itemToDelete = item
                            showDeleteDialog = true
                        }
                    )
                }
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景效果
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
                            "收藏夹",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            color = textPrimaryColor
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { (context as ComponentActivity).finish() }) {
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
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = textSecondaryColor)
                    }
                }
                favoriteList.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无收藏的网址",
                            color = textSecondaryColor,
                            fontSize = 16.sp
                        )
                    }
                }
                else -> {
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

                            SettingsGroupCard(bgColor = cardBgColor) {
                                group.items.forEachIndexed { index, item ->
                                    MenuListItem(
                                        title = item.title,
                                        isLast = index == group.items.size - 1,
                                        dividerColor = dividerColor,
                                        textPrimaryColor = textPrimaryColor,
                                        textSecondaryColor = textSecondaryColor,
                                        onClick = item.onClick,
                                        onLongClick = item.onLongClick
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 删除确认对话框
        if (showDeleteDialog && itemToDelete != null) {
            AlertDialog(
                onDismissRequest = { 
                    showDeleteDialog = false
                    itemToDelete = null
                },
                title = { Text("删除收藏", color = textPrimaryColor) },
                text = { Text("确定要删除「${itemToDelete!!.name}」吗？删除后无法恢复", color = textSecondaryColor) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val currentItem = itemToDelete!!
                            showDeleteDialog = false
                            itemToDelete = null

                            // 修复2：使用Compose协程作用域调用挂起函数，解决协程调用错误
                            coroutineScope.launch {
                                val isSuccess = deleteFavoriteItem(context, currentItem)
                                if (isSuccess) {
                                    showToast(context, "删除成功")
                                    refreshTrigger++
                                } else {
                                    showToast(context, "删除失败，请重试")
                                }
                            }
                        }
                    ) {
                        Text("确定", color = Color(0xFFFF3B30))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showDeleteDialog = false
                        itemToDelete = null
                    }) {
                        Text("取消", color = textSecondaryColor)
                    }
                },
                containerColor = cardBgColor
            )
        }
    }
}

// 私有卡片组件，避免包内冲突
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

// 修复3：添加实验性API注解，解决combinedClickable编译警告/错误
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MenuListItem(
    title: String,
    isLast: Boolean,
    dividerColor: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // 保留原有按压动画
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
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick
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
