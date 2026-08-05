package com.rootes.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.StringReader
import java.util.concurrent.TimeUnit

import com.rootes.browser2.unit.WebActivity

data class NewsItem(
    val title: String,
    val summary: String,
    val link: String
)

class NewsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                NewsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen() {
    val context = LocalContext.current
    val bgUrl = "https://api.sretna.cn/api/anime.php"
    val isDark = isSystemInDarkTheme()

    var newsList by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val cardBgColor = if (isDark) Color(0xCC1C1C1E) else Color(0xEEFFFFFF)
    val topBarBgColor = if (isDark) Color(0xBB1C1C1E) else Color(0xBBF8F8F8)
    val maskColor = if (isDark) Color(0x66000000) else Color(0x22000000)
    val textPrimaryColor = if (isDark) Color.White else Color.Black
    val textSecondaryColor = if (isDark) Color(0xFF8E8E93) else Color(0xFF636366)

    LaunchedEffect(Unit) {
        isLoading = true
        errorMsg = null
        try {
            val result = withContext(Dispatchers.IO) { fetchDailyNews() }
            if (result.isSuccess) {
                newsList = result.getOrNull() ?: emptyList()
            } else {
                errorMsg = result.exceptionOrNull()?.message ?: "加载失败"
            }
        } catch (e: Exception) {
            errorMsg = e.message ?: "未知错误"
        } finally {
            isLoading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                            "每日新闻",
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
                errorMsg != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = errorMsg!!, color = textSecondaryColor, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isLoading = true
                                        errorMsg = null
                                        try {
                                            val result = withContext(Dispatchers.IO) { fetchDailyNews() }
                                            if (result.isSuccess) {
                                                newsList = result.getOrNull() ?: emptyList()
                                            } else {
                                                errorMsg = result.exceptionOrNull()?.message ?: "加载失败"
                                            }
                                        } catch (e: Exception) {
                                            errorMsg = e.message ?: "未知错误"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = cardBgColor)
                            ) {
                                Text("重试", color = textPrimaryColor)
                            }
                        }
                    }
                }
                newsList.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无新闻", color = textSecondaryColor, fontSize = 16.sp)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(newsList) { news ->
                            NewsCard(
                                news = news,
                                cardBgColor = cardBgColor,
                                textPrimaryColor = textPrimaryColor,
                                textSecondaryColor = textSecondaryColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NewsCard(
    news: NewsItem,
    cardBgColor: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "press_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "press_alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                val intent = Intent(context, WebActivity::class.java).apply {
                    putExtra("url", news.link)
                }
                context.startActivity(intent)
            },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = news.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = textPrimaryColor,
                lineHeight = 22.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = news.summary,
                fontSize = 13.sp,
                color = textSecondaryColor,
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("阅读全文", fontSize = 12.sp, color = textSecondaryColor)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.ArrowForwardIos,
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    tint = textSecondaryColor
                )
            }
        }
    }
}

private suspend fun fetchDailyNews(): Result<List<NewsItem>> {
    return withContext(Dispatchers.IO) {
        try {
        
val date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
// 或自定义格式：DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val url = "https://benzhi.online/api/daily-news?date=$date"

            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}"))
            }

            val xmlString = response.body?.string()
            if (xmlString.isNullOrEmpty()) {
                return@withContext Result.failure(IOException("响应内容为空"))
            }

            val items = parseNewsXml(xmlString)
            Result.success(items)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}

private fun parseNewsXml(xmlString: String): List<NewsItem> {
    val newsList = mutableListOf<NewsItem>()
    val parser = android.util.Xml.newPullParser()
    try {
        parser.setInput(StringReader(xmlString))
        var eventType = parser.eventType
        var currentTag: String? = null
        var title = ""
        var summary = ""
        var link = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag == "item") {
                        title = ""
                        summary = ""
                        link = ""
                    }
                }
                XmlPullParser.TEXT -> {
                    when (currentTag) {
                        "title" -> title = parser.text?.trim() ?: ""
                        "summary" -> summary = parser.text?.trim() ?: ""
                        "link" -> link = parser.text?.trim() ?: ""
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item" && title.isNotEmpty() && link.isNotEmpty()) {
                        newsList.add(NewsItem(title, summary, link))
                    }
                    currentTag = null
                }
            }
            eventType = parser.next()
        }
    } catch (e: XmlPullParserException) {
        e.printStackTrace()
    } catch (e: IOException) {
        e.printStackTrace()
    }
    return newsList
}