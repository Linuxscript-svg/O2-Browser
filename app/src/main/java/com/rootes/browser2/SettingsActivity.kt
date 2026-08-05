package com.rootes.browser2

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties
import kotlin.text.Regex

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.composed

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.compose.setContent

// 新增 import：用于日期判断
import java.util.Calendar

// 全局 iOS 风格点击动效
fun Modifier.iOSBounceClick(
    enabled: Boolean = true,
    scaleRatio: Float = 0.97f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleRatio else 1f,
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

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

enum class ThemeMode(val displayName: String, val value: String) {
    FOLLOW_SYSTEM("跟随系统", "follow_system"),
    LIGHT("浅色模式", "light"),
    DARK("深色模式", "dark")
}

// 应用设置数据类
data class AppSettings(
    val webJs: Boolean = true,
    val webSsl: Boolean = false,
    val download: Boolean = true,
    val downloadFiles: String = "/sdcard/Download",
    val apps: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    val isPd: Boolean = false,
    val hasCustomHome: Boolean = false,
    val useCustomHome: Boolean = false,
    val hasCustomLinkHome: Boolean = false,
    val useCustomLinkHome: Boolean = false,
    val customLinkUrl: String = ""
)

data class SearchEngine(
    val displayName: String,
    val value: String
)

val searchEngineList = listOf(
    SearchEngine("百度搜索", "baidu"),
    SearchEngine("必应搜索", "bing"),
    SearchEngine("Google搜索", "google")
)

// 设置ViewModel
class SettingsViewModel(
    private val filesDir: File,
    private val onImportHtml: () -> Unit
) : ViewModel() {
    var settings by mutableStateOf(AppSettings())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var isClearing by mutableStateOf(false)
        private set

    var currentSearchEngine by mutableStateOf("bing")
        private set

    private val settingsFile = File(filesDir, "settings.txt")
    private val launchWebFile = File(filesDir, "launch_web.txt")

    // 本地自定义主页文件
    private val customHomeDir = File(filesDir, "home")
    private val customHomeFile = File(customHomeDir, "index.html")
    private val customHomeFileOff = File(customHomeDir, "index.html_off")

    // 自定义链接主页文件
    private val customLinkFile = File(filesDir, "web_online.txt")
    private val customLinkFileOff = File(filesDir, "web_online.txt1")

    init {
        customHomeDir.mkdirs()
        loadAllSettings()
    }

    fun triggerImportHtml() {
        onImportHtml()
    }

    private fun loadAllSettings() {
        viewModelScope.launch {
            isLoading = true
            settings = loadBaseSettings()
            currentSearchEngine = loadSearchEngine()
            isLoading = false
        }
    }

    private suspend fun loadBaseSettings(): AppSettings = withContext(Dispatchers.IO) {
        try {
            if (!settingsFile.exists()) return@withContext AppSettings()
            val properties = Properties()
            settingsFile.inputStream().use { properties.load(it) }

            val (hasHome, useHome) = checkCustomHomeStatus()
            val (hasLinkHome, useLinkHome) = checkCustomLinkHomeStatus()
            val customLinkUrl = if (customLinkFile.exists()) {
                try {
                    customLinkFile.readText().trim()
                } catch (e: Exception) {
                    ""
                }
            } else ""

            AppSettings(
                webJs = properties.getProperty("webJs")?.toBooleanStrictOrNull() ?: true,
                webSsl = properties.getProperty("webSsl")?.toBooleanStrictOrNull() ?: false,
                download = properties.getProperty("download")?.toBooleanStrictOrNull() ?: true,
                downloadFiles = properties.getProperty("downloadFiles") ?: "/sdcard/Download",
                apps = properties.getProperty("apps")?.toBooleanStrictOrNull() ?: true,
                themeMode = when (properties.getProperty("themeMode")) {
                    "light" -> ThemeMode.LIGHT
                    "dark" -> ThemeMode.DARK
                    else -> ThemeMode.FOLLOW_SYSTEM
                },
                isPd = properties.getProperty("is_pd")?.toBooleanStrictOrNull() ?: false,
                hasCustomHome = hasHome,
                useCustomHome = useHome,
                hasCustomLinkHome = hasLinkHome,
                useCustomLinkHome = useLinkHome,
                customLinkUrl = customLinkUrl
            )
        } catch (e: Exception) {
            e.printStackTrace()
            AppSettings()
        }
    }

    private fun checkCustomHomeStatus(): Pair<Boolean, Boolean> {
        val hasFile = customHomeFile.exists()
        return Pair(hasFile, hasFile)
    }

    private fun checkCustomLinkHomeStatus(): Pair<Boolean, Boolean> {
        val hasFile = customLinkFile.exists()
        val url = if (hasFile) {
            try {
                customLinkFile.readText().trim()
            } catch (e: Exception) {
                ""
            }
        } else ""
        val isValid = hasFile && (url.startsWith("http://") || url.startsWith("https://"))
        return Pair(isValid, hasFile)
    }

    private suspend fun loadSearchEngine(): String = withContext(Dispatchers.IO) {
        try {
            if (!launchWebFile.exists()) return@withContext "bing"
            val content = launchWebFile.readText().trim()
            val regex = Regex("launch_web=\"([a-zA-Z]+)\"")
            val matchResult = regex.find(content)
            val engineValue = matchResult?.groupValues?.get(1) ?: "bing"
            return@withContext if (engineValue in searchEngineList.map { it.value }) engineValue else "bing"
        } catch (e: Exception) {
            "bing"
        }
    }

    private fun saveBaseSettings(newSettings: AppSettings) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val properties = Properties()
                properties.setProperty("webJs", newSettings.webJs.toString())
                properties.setProperty("webSsl", newSettings.webSsl.toString())
                properties.setProperty("download", newSettings.download.toString())
                properties.setProperty("downloadFiles", newSettings.downloadFiles)
                properties.setProperty("apps", newSettings.apps.toString())
                properties.setProperty("themeMode", newSettings.themeMode.value)
                properties.setProperty("is_pd", newSettings.isPd.toString())
                settingsFile.outputStream().use { properties.store(it, "App Settings") }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateIsPd(enabled: Boolean) {
        settings = settings.copy(isPd = enabled)
        saveBaseSettings(settings)
    }

    fun updateThemeMode(mode: ThemeMode) {
        settings = settings.copy(themeMode = mode)
        saveBaseSettings(settings)
    }

    fun updateSearchEngine(engineValue: String) {
        currentSearchEngine = engineValue
        viewModelScope.launch(Dispatchers.IO) {
            launchWebFile.writeText("launch_web=\"$engineValue\"")
        }
    }

    fun updateWebJs(enabled: Boolean) {
        settings = settings.copy(webJs = enabled)
        saveBaseSettings(settings)
    }

    fun updateWebSsl(enabled: Boolean) {
        settings = settings.copy(webSsl = enabled)
        saveBaseSettings(settings)
    }

    fun updateDownload(enabled: Boolean) {
        settings = settings.copy(download = enabled)
        saveBaseSettings(settings)
    }

    fun updateDownloadFiles(path: String) {
        settings = settings.copy(downloadFiles = path)
        saveBaseSettings(settings)
    }

    fun updateApps(enabled: Boolean) {
        settings = settings.copy(apps = enabled)
        saveBaseSettings(settings)
    }

    // 自定义主页开关（带互斥逻辑）
    fun toggleCustomHome(enable: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (enable) {
                    if (customHomeFileOff.exists()) {
                        customHomeFileOff.renameTo(customHomeFile)
                    }
                    // 互斥：关闭自定义链接主页
                    if (settings.useCustomLinkHome) {
                        toggleCustomLinkHome(false)
                    }
                } else {
                    if (customHomeFile.exists()) {
                        customHomeFile.renameTo(customHomeFileOff)
                    }
                }
                val (hasHome, useHome) = checkCustomHomeStatus()
                settings = settings.copy(hasCustomHome = hasHome, useCustomHome = useHome)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 自定义链接主页开关（带互斥逻辑）
    fun toggleCustomLinkHome(enable: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (enable) {
                    if (customLinkFileOff.exists()) {
                        customLinkFileOff.renameTo(customLinkFile)
                    }
                    // 互斥：关闭自定义主页
                    if (settings.useCustomHome) {
                        toggleCustomHome(false)
                    }
                } else {
                    if (customLinkFile.exists()) {
                        customLinkFile.renameTo(customLinkFileOff)
                    }
                }
                val (hasLinkHome, useLinkHome) = checkCustomLinkHomeStatus()
                val customLinkUrl = if (customLinkFile.exists()) {
                    try {
                        customLinkFile.readText().trim()
                    } catch (e: Exception) {
                        ""
                    }
                } else ""
                settings = settings.copy(
                    hasCustomLinkHome = hasLinkHome,
                    useCustomLinkHome = useLinkHome,
                    customLinkUrl = customLinkUrl
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 更新自定义链接URL
    fun updateCustomLinkUrl(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                customLinkFile.writeText(url.trim())
                val (hasLinkHome, useLinkHome) = checkCustomLinkHomeStatus()
                settings = settings.copy(
                    hasCustomLinkHome = hasLinkHome,
                    useCustomLinkHome = useLinkHome,
                    customLinkUrl = url.trim()
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 导入自定义HTML
    fun importCustomHomeHtml(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                customHomeFile.delete()
                customHomeFileOff.delete()

                context.contentResolver.openInputStream(uri)?.use { input ->
                    customHomeFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val (hasHome, useHome) = checkCustomHomeStatus()
                // 导入成功后自动关闭自定义链接主页
                if (useHome && settings.useCustomLinkHome) {
                    toggleCustomLinkHome(false)
                }
                settings = settings.copy(hasCustomHome = hasHome, useCustomHome = useHome)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "导入失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 清理缓存数据
    fun clearSelected(
        context: Context,
        clearCookies: Boolean,
        clearWebStorage: Boolean,
        clearFormData: Boolean,
        clearWebCache: Boolean,
        clearAppCache: Boolean
    ) {
        viewModelScope.launch {
            isClearing = true
            withContext(Dispatchers.Main) {
                try {
                    if (clearCookies) {
                        val cm = CookieManager.getInstance()
                        cm.removeAllCookies(null)
                        cm.flush()
                    }
                    if (clearWebStorage) {
                        WebStorage.getInstance().deleteAllData()
                    }
                    if (clearFormData) {
                        WebView(context).clearFormData()
                    }
                    if (clearWebCache) {
                        WebView(context).clearCache(true)
                    }
                    if (clearAppCache) {
                        withContext(Dispatchers.IO) {
                            context.cacheDir.deleteRecursively()
                            context.externalCacheDir?.deleteRecursively()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isClearing = false
                }
            }
        }
    }
}

// ViewModel工厂
class SettingsViewModelFactory(
    private val filesDir: File,
    private val onImportHtml: () -> Unit
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(filesDir, onImportHtml) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// 主Activity
class SettingsActivity : ComponentActivity() {
    private lateinit var importHtmlLauncher: ActivityResultLauncher<String>
    private lateinit var viewModel: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 注册文件选择器
        importHtmlLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                viewModel.importCustomHomeHtml(this, it)
            }
        }

        // 初始化ViewModel
        viewModel = ViewModelProvider(
            this,
            SettingsViewModelFactory(filesDir) {
                importHtmlLauncher.launch("text/html")
            }
        )[SettingsViewModel::class.java]

        setContent {
            MaterialTheme {
                SettingsScreen(viewModel = viewModel)
            }
        }
    }
}

// 设置页面主UI
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val settings = viewModel.settings
    val isLoading = viewModel.isLoading
    val scrollState = rememberScrollState()

    var showSearchEngineDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    var clearCookies by remember { mutableStateOf(true) }
    var clearWebStorage by remember { mutableStateOf(true) }
    var clearFormData by remember { mutableStateOf(true) }
    var clearWebCache by remember { mutableStateOf(true) }
    var clearAppCache by remember { mutableStateOf(true) }

    // 主题适配
    val isDark = when (settings.themeMode) {
        ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val cardBgColor = if (isDark) Color(0xCC1C1C1E) else Color(0xEEFFFFFF)
    val topBarBgColor = if (isDark) Color(0xBB1C1C1E) else Color(0xBBF8F8F8)
    val maskColor = if (isDark) Color(0x66000000) else Color(0x22000000)
    val dividerColor = if (isDark) Color(0x33FFFFFF) else Color(0x1A000000)
    val textPrimaryColor = if (isDark) Color.White else Color.Black
    val textSecondaryColor = if (isDark) Color(0xFF8E8E93) else Color(0xFF636366)
    val iosGreen = Color(0xFF34C759)

    // 新增：判断是否已到当年7月1日
    val isAfterJulyFirst = remember {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val julyFirst = Calendar.getInstance().apply {
            set(year, Calendar.JULY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        now >= julyFirst
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景图
        Image(
            painter = rememberAsyncImagePainter(
                model = "https://api.sretna.cn/api/anime.php",
                error = painterResource(id = android.R.drawable.ic_menu_gallery)
            ),
            contentDescription = "背景",
            modifier = Modifier.fillMaxSize().blur(12.dp),
            contentScale = ContentScale.Crop
        )
        // 背景遮罩
        Box(modifier = Modifier.fillMaxSize().background(maskColor))

        // 主页面结构
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "设置",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            color = textPrimaryColor
                        )
                    },
                    colors = TopAppBarDefaults.smallTopAppBarColors(
                        containerColor = topBarBgColor,
                        titleContentColor = textPrimaryColor
                    ),
                    modifier = Modifier.statusBarsPadding()
                )
            }
        ) { innerPadding ->
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = iosGreen)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // 显示模式与搜索引擎组
                    SettingsGroupCard(bgColor = cardBgColor) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .iOSBounceClick { showThemeDialog = true }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("显示模式", fontSize = 16.sp, color = textPrimaryColor)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    settings.themeMode.displayName,
                                    fontSize = 15.sp,
                                    color = textSecondaryColor,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Icon(
                                    Icons.Default.ArrowForwardIos,
                                    null,
                                    tint = textSecondaryColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Divider(
                            modifier = Modifier.padding(start = 16.dp),
                            thickness = 0.5.dp,
                            color = dividerColor
                        )
                        val currentEngine = searchEngineList.first { it.value == viewModel.currentSearchEngine }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .iOSBounceClick { showSearchEngineDialog = true }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("默认搜索引擎", fontSize = 16.sp, color = textPrimaryColor)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    currentEngine.displayName,
                                    fontSize = 15.sp,
                                    color = textSecondaryColor,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Icon(
                                    Icons.Default.ArrowForwardIos,
                                    null,
                                    tint = textSecondaryColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // 自定义HTML主页组
                    SettingsGroupCard(bgColor = cardBgColor) {
                        SwitchSettingItem(
                            title = "启用自定义主页",
                            description = if (settings.hasCustomHome) "已检测到HTML" else "未导入主页",
                            checked = settings.useCustomHome,
                            onCheckedChange = { viewModel.toggleCustomHome(it) },
                            showDivider = true,
                            dividerColor = dividerColor,
                            textPrimaryColor = textPrimaryColor,
                            textSecondaryColor = textSecondaryColor,
                            iosGreen = iosGreen
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .iOSBounceClick { viewModel.triggerImportHtml() }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("导入自定义主页HTML", fontSize = 16.sp, color = textPrimaryColor)
                            Icon(
                                Icons.Default.ArrowForwardIos,
                                null,
                                tint = textSecondaryColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // 自定义链接主页组
                    SettingsGroupCard(bgColor = cardBgColor) {
                        SwitchSettingItem(
                            title = "启用自定义链接主页",
                            description = if (settings.hasCustomLinkHome) "已设置有效链接" else "未设置有效链接",
                            checked = settings.useCustomLinkHome,
                            onCheckedChange = { viewModel.toggleCustomLinkHome(it) },
                            showDivider = true,
                            dividerColor = dividerColor,
                            textPrimaryColor = textPrimaryColor,
                            textSecondaryColor = textSecondaryColor,
                            iosGreen = iosGreen
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                "自定义主页链接",
                                fontSize = 16.sp,
                                color = textPrimaryColor,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            OutlinedTextField(
                                value = settings.customLinkUrl,
                                onValueChange = { viewModel.updateCustomLinkUrl(it) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = {
                                    Text(
                                        "请输入以http://或https://开头的链接",
                                        color = textSecondaryColor
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = androidx.compose.material3.TextFieldDefaults.outlinedTextFieldColors(
                                    containerColor = if (isDark) Color(0x22FFFFFF) else Color(0x11000000),
                                    focusedTextColor = textPrimaryColor,
                                    unfocusedTextColor = textPrimaryColor,
                                    focusedBorderColor = iosGreen,
                                    unfocusedBorderColor = dividerColor
                                )
                            )
                        }
                    }

                    // 网页设置组
                    SettingsGroupCard(bgColor = cardBgColor) {
                        SwitchSettingItem(
                            title = "网页JS加载",
                            description = "关闭后不加载JS脚本",
                            checked = settings.webJs,
                            onCheckedChange = viewModel::updateWebJs,
                            showDivider = true,
                            dividerColor = dividerColor,
                            textPrimaryColor = textPrimaryColor,
                            textSecondaryColor = textSecondaryColor,
                            iosGreen = iosGreen
                        )
                        SwitchSettingItem(
                            title = "跳过SSL证书验证",
                            description = "忽略HTTPS证书错误",
                            checked = settings.webSsl,
                            onCheckedChange = viewModel::updateWebSsl,
                            showDivider = false,
                            dividerColor = dividerColor,
                            textPrimaryColor = textPrimaryColor,
                            textSecondaryColor = textSecondaryColor,
                            iosGreen = iosGreen
                        )
                    }

                    // 剪贴板设置组
                    SettingsGroupCard(bgColor = cardBgColor) {
                        SwitchSettingItem(
                            title = "禁止使用剪贴板",
                            description = "开启后APP无法读取或写入剪贴板",
                            checked = settings.isPd,
                            onCheckedChange = viewModel::updateIsPd,
                            showDivider = false,
                            dividerColor = dividerColor,
                            textPrimaryColor = textPrimaryColor,
                            textSecondaryColor = textSecondaryColor,
                            iosGreen = iosGreen
                        )
                    }

                    // 下载设置组
                    SettingsGroupCard(bgColor = cardBgColor) {
                        SwitchSettingItem(
                            title = "允许网页下载文件",
                            description = "允许网页触发文件下载",
                            checked = settings.download,
                            onCheckedChange = viewModel::updateDownload,
                            showDivider = true,
                            dividerColor = dividerColor,
                            textPrimaryColor = textPrimaryColor,
                            textSecondaryColor = textSecondaryColor,
                            iosGreen = iosGreen
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                "默认下载目录",
                                fontSize = 16.sp,
                                color = textPrimaryColor,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            OutlinedTextField(
                                value = settings.downloadFiles,
                                onValueChange = viewModel::updateDownloadFiles,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = androidx.compose.material3.TextFieldDefaults.outlinedTextFieldColors(
                                    containerColor = if (isDark) Color(0x22FFFFFF) else Color(0x11000000),
                                    focusedTextColor = textPrimaryColor,
                                    unfocusedTextColor = textPrimaryColor,
                                    focusedBorderColor = iosGreen,
                                    unfocusedBorderColor = dividerColor
                                )
                            )
                        }
                    }

                    // 外部应用设置组
                    SettingsGroupCard(bgColor = cardBgColor) {
                        SwitchSettingItem(
                            title = "允许打开外部应用",
                            description = "允许网页唤起其他APP",
                            checked = settings.apps,
                            onCheckedChange = viewModel::updateApps,
                            showDivider = false,
                            dividerColor = dividerColor,
                            textPrimaryColor = textPrimaryColor,
                            textSecondaryColor = textSecondaryColor,
                            iosGreen = iosGreen
                        )
                    }

                    // ========== 新增：下载新预览版 ==========
                    SettingsGroupCard(bgColor = cardBgColor) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .iOSBounceClick(enabled = isAfterJulyFirst) {
                                    try {
                                        val intent = Intent(context, Class.forName("com.rootes.browser.unit.WebActivity"))
                                        intent.putExtra("url", "https://browser.rootes.top/update_beta.apk")
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "无法打开下载页面", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "下载新预览版",
                                fontSize = 16.sp,
                                color = if (isAfterJulyFirst) textPrimaryColor else textSecondaryColor.copy(alpha = 0.5f)
                            )
                            if (isAfterJulyFirst) {
                                Icon(
                                    Icons.Default.ArrowForwardIos,
                                    null,
                                    tint = textSecondaryColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                Text(
                                    "2026年7月开放",
                                    fontSize = 13.sp,
                                    color = textSecondaryColor.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    // 清理缓存组
                    SettingsGroupCard(bgColor = cardBgColor) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .iOSBounceClick { showClearDialog = true }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("清理缓存数据", fontSize = 16.sp, color = textPrimaryColor)
                            if (viewModel.isClearing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = iosGreen,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.ArrowForwardIos,
                                    null,
                                    tint = textSecondaryColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // 关于我们组
                    SettingsGroupCard(bgColor = cardBgColor) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .iOSBounceClick {
                                    try {
                                        val intent = Intent(context, Class.forName("com.rootes.browser.unit.WebActivity"))
                                        intent.putExtra("url", "https://browser.rootes.top/about.html")
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "页面加载失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("关于我们", fontSize = 16.sp, color = textPrimaryColor)
                            Icon(
                                Icons.Default.ArrowForwardIos,
                                null,
                                tint = textSecondaryColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }

        // 主题选择弹窗
        if (showThemeDialog) {
            AlertDialog(
                onDismissRequest = { showThemeDialog = false },
                title = {
                    Text(
                        "选择显示模式",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimaryColor
                    )
                },
                text = {
                    Column {
                        ThemeMode.values().forEachIndexed { i, mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .iOSBounceClick {
                                        viewModel.updateThemeMode(mode)
                                        showThemeDialog = false
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    mode.displayName,
                                    color = if (mode == settings.themeMode) iosGreen else textPrimaryColor,
                                    fontWeight = if (mode == settings.themeMode) FontWeight.Medium else FontWeight.Normal
                                )
                            }
                            if (i != ThemeMode.values().size - 1) {
                                Divider(thickness = 0.5.dp, color = dividerColor)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        modifier = Modifier.fillMaxWidth().iOSBounceClick {},
                        onClick = { showThemeDialog = false }
                    ) {
                        Text("取消", fontWeight = FontWeight.SemiBold, color = textPrimaryColor)
                    }
                },
                containerColor = cardBgColor,
                shape = RoundedCornerShape(14.dp)
            )
        }

        // 搜索引擎选择弹窗
        if (showSearchEngineDialog) {
            AlertDialog(
                onDismissRequest = { showSearchEngineDialog = false },
                title = {
                    Text(
                        "选择搜索引擎",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimaryColor
                    )
                },
                text = {
                    Column {
                        searchEngineList.forEachIndexed { i, engine ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .iOSBounceClick {
                                        viewModel.updateSearchEngine(engine.value)
                                        showSearchEngineDialog = false
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    engine.displayName,
                                    color = if (engine.value == viewModel.currentSearchEngine) iosGreen else textPrimaryColor,
                                    fontWeight = if (engine.value == viewModel.currentSearchEngine) FontWeight.Medium else FontWeight.Normal
                                )
                            }
                            if (i != searchEngineList.size - 1) {
                                Divider(thickness = 0.5.dp, color = dividerColor)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        modifier = Modifier.fillMaxWidth().iOSBounceClick {},
                        onClick = { showSearchEngineDialog = false }
                    ) {
                        Text("取消", fontWeight = FontWeight.SemiBold, color = textPrimaryColor)
                    }
                },
                containerColor = cardBgColor,
                shape = RoundedCornerShape(14.dp)
            )
        }

        // 清理数据弹窗
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = {
                    Text(
                        "清理数据",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimaryColor
                    )
                },
                text = {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { clearCookies = !clearCookies }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = clearCookies,
                                onCheckedChange = { clearCookies = it },
                                colors = CheckboxDefaults.colors(checkedColor = iosGreen)
                            )
                            Text("清理 Cookie", color = textPrimaryColor, fontSize = 16.sp)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { clearWebStorage = !clearWebStorage }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = clearWebStorage,
                                onCheckedChange = { clearWebStorage = it },
                                colors = CheckboxDefaults.colors(checkedColor = iosGreen)
                            )
                            Text("清理网页存储", color = textPrimaryColor, fontSize = 16.sp)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { clearFormData = !clearFormData }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = clearFormData,
                                onCheckedChange = { clearFormData = it },
                                colors = CheckboxDefaults.colors(checkedColor = iosGreen)
                            )
                            Text("清理表单数据", color = textPrimaryColor, fontSize = 16.sp)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { clearWebCache = !clearWebCache }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = clearWebCache,
                                onCheckedChange = { clearWebCache = it },
                                colors = CheckboxDefaults.colors(checkedColor = iosGreen)
                            )
                            Text("清理网页缓存", color = textPrimaryColor, fontSize = 16.sp)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { clearAppCache = !clearAppCache }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = clearAppCache,
                                onCheckedChange = { clearAppCache = it },
                                colors = CheckboxDefaults.colors(checkedColor = iosGreen)
                            )
                            Text("清理应用缓存", color = textPrimaryColor, fontSize = 16.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        modifier = Modifier.iOSBounceClick {},
                        onClick = {
                            viewModel.clearSelected(
                                context = context,
                                clearCookies = clearCookies,
                                clearWebStorage = clearWebStorage,
                                clearFormData = clearFormData,
                                clearWebCache = clearWebCache,
                                clearAppCache = clearAppCache
                            )
                            showClearDialog = false
                        }
                    ) {
                        Text("开始清理", color = Color.Red, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(
                        modifier = Modifier.iOSBounceClick {},
                        onClick = { showClearDialog = false }
                    ) {
                        Text("取消", fontWeight = FontWeight.SemiBold, color = textPrimaryColor)
                    }
                },
                containerColor = cardBgColor,
                shape = RoundedCornerShape(14.dp)
            )
        }
    }
}

// 设置卡片组件
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
private fun SwitchSettingItem(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean,
    dividerColor: Color,
    textPrimaryColor: Color,
    textSecondaryColor: Color,
    iosGreen: Color
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .iOSBounceClick { onCheckedChange(!checked) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    color = textPrimaryColor
                )
                description?.let {
                    Text(
                        text = it,
                        fontSize = 13.sp,
                        color = textSecondaryColor,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = iosGreen,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFE5E5EA),
                    uncheckedBorderColor = Color.Transparent
                ),
                thumbContent = { Box(modifier = Modifier.size(20.dp)) }
            )
        }
        if (showDivider) {
            Divider(
                modifier = Modifier.padding(start = 16.dp),
                thickness = 0.5.dp,
                color = dividerColor
            )
        }
    }
}