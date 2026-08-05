package com.rootes.browser2.unit

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlSerializer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

import com.rootes.app.FormActivity
import com.rootes.app.TimeActivity
// 剪贴板权限常量兼容
private const val RESOURCE_CLIPBOARD_READ = "android.webkit.resource.CLIPBOARD_READ"
private const val ERROR_PAGE_URL = "file:///android_asset/NoWIFI.html"

// SSL 状态
sealed class SslState {
    object Loading : SslState()
    object Valid : SslState()  // HTTPS安全链接
    object Invalid : SslState() // HTTP不安全链接
}

class WebActivity : ComponentActivity() {
    private var mWebView: WebView? = null
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var downloadManager: DownloadManager
    private lateinit var browserConfig: SettingsData

    // 权限申请启动器
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "权限已授予，重新点击下载即可", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "权限被拒绝，无法完成下载", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        sharedPrefs = getSharedPreferences("webview_prefs", Context.MODE_PRIVATE)
        browserConfig = loadSettings(this)
        val startUrl = intent.getStringExtra("url") ?: "https://m.bilibili.com"

        setContent {
            val isDarkMode = isSystemDarkTheme(this)
            MaterialTheme(
                colorScheme = if (isDarkMode) darkColorScheme() else lightColorScheme()
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WebBrowserScreen(
                        startUrl = startUrl,
                        config = browserConfig,
                        activity = this,
                        sharedPrefs = sharedPrefs,
                        onWebViewCreated = { mWebView = it },
                        onDownloadTrigger = { url, userAgent, contentDisposition, mimeType, contentLength ->
                            startDownload(url, userAgent, contentDisposition, mimeType, contentLength)
                        }
                    )
                }
            }
        }
    }

    // ========== 收藏夹功能 ==========
    fun addToFavorites(name: String, url: String) {
        Thread {
            try {
                val favoritesFile = File(filesDir, "favorites.xml")
                val favorites = mutableListOf<Pair<String, String>>()

                if (favoritesFile.exists()) {
                    val factory = XmlPullParserFactory.newInstance()
                    val parser = factory.newPullParser()
                    parser.setInput(FileInputStream(favoritesFile), "UTF-8")
                    var eventType = parser.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                            val itemName = parser.getAttributeValue(null, "name") ?: ""
                            val itemUrl = parser.getAttributeValue(null, "url") ?: ""
                            favorites.add(itemName to itemUrl)
                        }
                        eventType = parser.next()
                    }
                }

                favorites.add(name to url)

                val serializer = XmlPullParserFactory.newInstance().newSerializer()
                serializer.setOutput(FileOutputStream(favoritesFile), "UTF-8")
                serializer.startDocument("UTF-8", true)
                serializer.startTag(null, "favorites")
                for ((n, u) in favorites) {
                    serializer.startTag(null, "item")
                    serializer.attribute(null, "name", n)
                    serializer.attribute(null, "url", u)
                    serializer.endTag(null, "item")
                }
                serializer.endTag(null, "favorites")
                serializer.endDocument()

                runOnUiThread {
                    Toast.makeText(this, "已收藏：$name", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "收藏失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // ========== 访问记录 ==========
    fun recordVisit(name: String, url: String) {
        val noTimeFile = File(filesDir, "notime.txt")
        if (noTimeFile.exists()) return

        Thread {
            try {
                val historyFile = File(filesDir, "WEB_times.xml")
                val visits = mutableListOf<Triple<String, String, String>>()

                if (historyFile.exists()) {
                    val factory = XmlPullParserFactory.newInstance()
                    val parser = factory.newPullParser()
                    parser.setInput(FileInputStream(historyFile), "UTF-8")
                    var eventType = parser.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG && parser.name == "visit") {
                            val visitName = parser.getAttributeValue(null, "name") ?: ""
                            val visitUrl = parser.getAttributeValue(null, "url") ?: ""
                            val visitTime = parser.getAttributeValue(null, "time") ?: ""
                            visits.add(Triple(visitName, visitUrl, visitTime))
                        }
                        eventType = parser.next()
                    }
                }

                val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                visits.add(Triple(name, url, timeStamp))

                val serializer = XmlPullParserFactory.newInstance().newSerializer()
                serializer.setOutput(FileOutputStream(historyFile), "UTF-8")
                serializer.startDocument("UTF-8", true)
                serializer.startTag(null, "history")
                for ((n, u, t) in visits) {
                    serializer.startTag(null, "visit")
                    serializer.attribute(null, "name", n)
                    serializer.attribute(null, "url", u)
                    serializer.attribute(null, "time", t)
                    serializer.endTag(null, "visit")
                }
                serializer.endTag(null, "history")
                serializer.endDocument()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun loadSettings(context: Context): SettingsData {
        val settingsFile = File(context.filesDir, "settings.txt")
        val defaultConfig = SettingsData()

        if (!settingsFile.exists()) return defaultConfig

        return try {
            val configMap = mutableMapOf<String, String>()
            settingsFile.forEachLine { line ->
                val trimmedLine = line.trim()
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) return@forEachLine
                val parts = trimmedLine.split("=", limit = 2)
                if (parts.size == 2) {
                    configMap[parts[0].trim()] = parts[1].trim()
                }
            }

            SettingsData(
                webJs = configMap["webJs"]?.toBooleanStrictOrNull() ?: defaultConfig.webJs,
                webSsl = configMap["webSsl"]?.toBooleanStrictOrNull() ?: defaultConfig.webSsl,
                download = configMap["download"]?.toBooleanStrictOrNull() ?: defaultConfig.download,
                downloadFiles = configMap["downloadFiles"] ?: defaultConfig.downloadFiles,
                apps = configMap["apps"]?.toBooleanStrictOrNull() ?: defaultConfig.apps,
                isPd = configMap["is_pd"]?.toBooleanStrictOrNull() ?: defaultConfig.isPd
            )
        } catch (e: Exception) {
            defaultConfig
        }
    }

    fun initSslSkip() {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            })

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getFileName(url: String, contentDisposition: String?, mimeType: String?): String {
        contentDisposition?.let {
            val regex = Regex("filename=\"?([^\"]+)\"?")
            val matchResult = regex.find(it)
            if (matchResult != null) return matchResult.groupValues[1]
        }
        val uri = Uri.parse(url)
        val lastPathSegment = uri.lastPathSegment
        if (!lastPathSegment.isNullOrEmpty()) return lastPathSegment
        return "download_${System.currentTimeMillis()}"
    }

    private fun startDownload(
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long
    ) {
        val fileName = getFileName(url, contentDisposition, mimeType)
        android.app.AlertDialog.Builder(this)
            .setTitle("下载确认")
            .setMessage("确定要下载文件：\n$fileName")
            .setPositiveButton("下载") { _, _ ->
                realDownload(url, userAgent, contentDisposition, mimeType, contentLength)
            }
            .setNegativeButton("取消") { _, _ -> }
            .show()
    }

    private fun realDownload(
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long
    ) {
        val neededPermissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (neededPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(neededPermissions.toTypedArray())
            return
        }

        val fileName = getFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            addRequestHeader("User-Agent", userAgent)
            setMimeType(mimeType)
            setTitle(fileName)
            setDescription("正在下载...")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)

            val downloadDir = File(browserConfig.downloadFiles)
            if (!downloadDir.exists()) downloadDir.mkdirs()
            val targetFile = File(downloadDir, fileName)
            setDestinationUri(Uri.fromFile(targetFile))

            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            val cookie = CookieManager.getInstance().getCookie(url)
            if (!cookie.isNullOrEmpty()) addRequestHeader("Cookie", cookie)
        }

        try {
            downloadManager.enqueue(request)
            Toast.makeText(this, "开始下载：$fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "下载失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        val webView = mWebView
        when {
            webView != null && webView.canGoBack() -> webView.goBack()
            else -> finish()
        }
    }

    private fun isSystemDarkTheme(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode
        return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}

// 工具函数
private fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } else {
        @Suppress("DEPRECATION")
        val networkInfo = connectivityManager.activeNetworkInfo
        networkInfo != null && networkInfo.isConnected
    }
}

private fun isUrl(input: String): Boolean {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return false
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return true
    val domainRegex = Regex("^([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(/.*)?$")
    return domainRegex.matches(trimmed)
}

private fun isClipboardBlocked(sharedPrefs: SharedPreferences, host: String): Boolean {
    return sharedPrefs.getBoolean("clipboard_block_$host", false)
}
private fun saveClipboardBlocked(sharedPrefs: SharedPreferences, host: String) {
    sharedPrefs.edit().putBoolean("clipboard_block_$host", true).apply()
}

private fun getSearchEngine(context: Context): String {
    val file = File(context.filesDir, "launch_web.txt")
    if (!file.exists()) return "https://www.baidu.com/s?wd="

    return try {
        val content = InputStreamReader(FileInputStream(file)).use { it.readText() }
        val regex = Regex("launch_web=\"([^\"]+)\"")
        val matchResult = regex.find(content)
        val engine = matchResult?.groupValues?.get(1)?.trim()?.lowercase() ?: "baidu"

        when (engine) {
            "bing" -> "https://m.bing.com/search?q="
            "google" -> "https://www.google.com/search?q="
            "baidu" -> "https://www.baidu.com/s?wd="
            else -> "https://www.baidu.com/s?wd="
        }
    } catch (e: Exception) {
        "https://www.baidu.com/s?wd="
    }
}

private fun checkNoPrompt(sharedPrefs: SharedPreferences, host: String): Boolean {
    return sharedPrefs.getBoolean("no_prompt_$host", false)
}
private fun saveNoPrompt(sharedPrefs: SharedPreferences, host: String) {
    sharedPrefs.edit().putBoolean("no_prompt_$host", true).apply()
}

private fun handleUrlLoading(
    view: WebView?,
    url: String,
    config: SettingsData,
    context: Context,
    sharedPrefs: SharedPreferences,
    onShowDialog: (String, String) -> Unit
): Boolean {
    if (!url.startsWith("http")) {
        if (!config.apps) {
            Toast.makeText(context, "跳转外部应用已被禁用", Toast.LENGTH_SHORT).show()
            return true
        }
        val uri = Uri.parse(url)
        val host = uri.host ?: "unknown"
        if (checkNoPrompt(sharedPrefs, host)) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                return true
            } catch (e: Exception) {
                return false
            }
        }
        onShowDialog(url, host)
        return true
    }

    when {
        url.contains("www.bilibili.com") -> {
            view?.loadUrl(url.replace("www.bilibili.com", "m.bilibili.com"))
            return true
        }
        url.contains("www.bing.com") -> {
            view?.loadUrl(url.replace("www.bing.com", "m.bing.com"))
            return true
        }
    }

    if (!config.apps) {
        view?.loadUrl(url)
        return true
    }
    return false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebBrowserScreen(
    startUrl: String,
    config: SettingsData,
    activity: WebActivity,
    sharedPrefs: SharedPreferences,
    onWebViewCreated: (WebView) -> Unit,
    onDownloadTrigger: (String, String, String?, String?, Long) -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    var webTitle by remember { mutableStateOf("加载中...") }
    var loadProgress by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var webView: WebView? by remember { mutableStateOf(null) }
    var currentUrl by remember { mutableStateOf(startUrl) }

    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    var showDialog by remember { mutableStateOf(false) }
    var targetUrl by remember { mutableStateOf("") }
    var dialogHost by remember { mutableStateOf("") }

    var showClipboardDialog by remember { mutableStateOf(false) }
    var pendingClipboardRequest by remember { mutableStateOf<PermissionRequest?>(null) }
    var clipboardHost by remember { mutableStateOf("") }

    // 底部菜单状态
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    val sslState by remember(currentUrl, isLoading) {
        derivedStateOf {
            if (isLoading) SslState.Loading
            else if (currentUrl.startsWith("https://")) SslState.Valid else SslState.Invalid
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val insets = WindowInsets.systemBars.asPaddingValues()

    Column(
        Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(top = insets.calculateTopPadding())
    ) {
        // ========== 搜索栏（左侧刷新按钮，右侧SSL图标和搜索按钮） ==========
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .height(40.dp)
                .background(
                    color = colorScheme.surface,
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    width = 1.dp,
                    color = colorScheme.outline,
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧刷新按钮
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "刷新",
                    tint = colorScheme.primary,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable {
                            webView?.reload()
                            Toast.makeText(activity, "已刷新", Toast.LENGTH_SHORT).show()
                        }
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 中间搜索文本
                BasicTextField(
                    value = if (searchText.isEmpty()) webTitle else searchText,
                    onValueChange = { searchText = it },
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        color = colorScheme.onSurface
                    ),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField -> innerTextField() }
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 右侧SSL图标
                when (sslState) {
                    SslState.Loading -> Icon(
                        imageVector = Icons.Default.Help,
                        contentDescription = "SSL检查中",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                    SslState.Valid -> Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "HTTPS安全链接",
                        tint = Color.Green,
                        modifier = Modifier.size(18.dp)
                    )
                    SslState.Invalid -> Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "HTTP不安全链接",
                        tint = Color.Red,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 搜索/访问按钮
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索/访问",
                    tint = colorScheme.primary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable {
                            val input = searchText.trim()
                            if (input.isEmpty()) return@clickable
                            if (isUrl(input)) {
                                val target = if (input.startsWith("http")) input else "https://$input"
                                webView?.loadUrl(target)
                            } else {
                                val searchUrl = getSearchEngine(activity) + Uri.encode(input)
                                webView?.loadUrl(searchUrl)
                            }
                            searchText = ""
                        }
                )
            }
        }

        // 加载进度条
        if (isLoading) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(colorScheme.outlineVariant)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(loadProgress / 100f)
                        .height(2.dp)
                        .background(colorScheme.primary)
                )
            }
        }

        // WebView核心
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    webView = this
                    onWebViewCreated(this)

                    if (config.webSsl) {
                        activity.initSslSkip()
                    }

                    setInitialScale(100)
                    clearCache(true)
                    clearHistory()
                    clearFormData()

                    val webSettings = settings
                    webSettings.javaScriptEnabled = config.webJs
                    webSettings.domStorageEnabled = true
                    webSettings.databaseEnabled = true
                    webSettings.allowFileAccess = true
                    webSettings.allowContentAccess = true
                    webSettings.loadsImagesAutomatically = true
                    webSettings.defaultTextEncodingName = "UTF-8"

                    webSettings.useWideViewPort = true
                    webSettings.loadWithOverviewMode = true
                    webSettings.setSupportZoom(true)
                    webSettings.builtInZoomControls = false
                    webSettings.displayZoomControls = false
                    webSettings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL

                    webSettings.userAgentString = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

                    webSettings.javaScriptCanOpenWindowsAutomatically = true
                    webSettings.mediaPlaybackRequiresUserGesture = false

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        webSettings.mixedContentMode = if (config.webSsl) {
                            WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        } else {
                            WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        }
                    }

                    webSettings.cacheMode = WebSettings.LOAD_DEFAULT
                    webSettings.setGeolocationEnabled(true)
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)

                    setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                        if (!config.download) {
                            Toast.makeText(ctx, "下载功能已被禁用", Toast.LENGTH_SHORT).show()
                            return@setDownloadListener
                        }
                        onDownloadTrigger(url, userAgent, contentDisposition, mimeType, contentLength)
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loadProgress = newProgress
                            isLoading = newProgress < 100
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                        }
                        override fun onReceivedTitle(view: WebView?, title: String?) {
                            webTitle = title ?: "无标题"
                        }
                        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                            callback?.invoke(origin, true, false)
                        }
                        override fun onPermissionRequest(request: PermissionRequest?) {
                            request ?: return super.onPermissionRequest(request)
                            if (config.isPd && request.resources.contains(RESOURCE_CLIPBOARD_READ)) {
                                request.deny()
                                return
                            }
                            if (request.resources.contains(RESOURCE_CLIPBOARD_READ)) {
                                val host = request.origin.host ?: "unknown"
                                if (isClipboardBlocked(sharedPrefs, host)) {
                                    request.deny()
                                    return
                                }
                                pendingClipboardRequest = request
                                clipboardHost = host
                                showClipboardDialog = true
                                return
                            }
                            super.onPermissionRequest(request)
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            url ?: return false
                            if (url.startsWith("http") && !isNetworkAvailable(activity)) {
                                view?.loadUrl(ERROR_PAGE_URL)
                                return true
                            }
                            return handleUrlLoading(view, url, config, activity, sharedPrefs) { url, host ->
                                targetUrl = url
                                dialogHost = host
                                showDialog = true
                            }
                        }
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                            val url = request.url.toString()
                            if (url.startsWith("http") && !isNetworkAvailable(activity)) {
                                view?.loadUrl(ERROR_PAGE_URL)
                                return true
                            }
                            return handleUrlLoading(view, url, config, activity, sharedPrefs) { url, host ->
                                targetUrl = url
                                dialogHost = host
                                showDialog = true
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            val url = request?.url?.toString() ?: return
                            if (url.startsWith("http") && url != ERROR_PAGE_URL && !isNetworkAvailable(activity)) {
                                view?.stopLoading()
                                view?.loadUrl(ERROR_PAGE_URL)
                            }
                        }

                        @Suppress("DEPRECATION")
                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            super.onReceivedError(view, errorCode, description, failingUrl)
                            failingUrl ?: return
                            if (failingUrl.startsWith("http") && failingUrl != ERROR_PAGE_URL && !isNetworkAvailable(activity)) {
                                view?.stopLoading()
                                view?.loadUrl(ERROR_PAGE_URL)
                            }
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            url?.let { currentUrl = it }

                            view?.evaluateJavascript("""
                                (function() {
                                    var meta = document.createElement('meta');
                                    meta.name = 'viewport';
                                    meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';
                                    var head = document.getElementsByTagName('head')[0];
                                    if (head) {
                                        var oldMeta = document.querySelector('meta[name="viewport"]');
                                        if (oldMeta) head.removeChild(oldMeta);
                                        head.appendChild(meta);
                                    }
                                })();
                            """.trimIndent(), null)

                            if (config.isPd) {
                                view?.evaluateJavascript("""
                                    (function() {
                                        Object.defineProperty(window.navigator, 'clipboard', {
                                            value: undefined,
                                            writable: false,
                                            configurable: false,
                                            enumerable: false
                                        });
                                        document.addEventListener('copy', function(e) {
                                            e.preventDefault();
                                            e.stopImmediatePropagation();
                                            return false;
                                        }, true);
                                        document.addEventListener('cut', function(e) {
                                            e.preventDefault();
                                            e.stopImmediatePropagation();
                                            return false;
                                        }, true);
                                        document.addEventListener('paste', function(e) {
                                            e.preventDefault();
                                            e.stopImmediatePropagation();
                                            return false;
                                        }, true);
                                        document.addEventListener('DOMContentLoaded', function() {
                                            const allInputs = document.querySelectorAll('input, textarea');
                                            allInputs.forEach(el => {
                                                el.onpaste = () => false;
                                                el.addEventListener('paste', e => e.preventDefault(), true);
                                            });
                                            const observer = new MutationObserver((mutations) => {
                                                mutations.forEach(mutation => {
                                                    mutation.addedNodes.forEach(node => {
                                                        if (node.nodeType === 1) {
                                                            const inputs = node.querySelectorAll('input, textarea');
                                                            inputs.forEach(el => {
                                                                el.onpaste = () => false;
                                                                el.addEventListener('paste', e => e.preventDefault(), true);
                                                            });
                                                        }
                                                    });
                                                });
                                            });
                                            observer.observe(document.body, { childList: true, subtree: true });
                                        });
                                    })();
                                """.trimIndent(), null)
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            url?.let { currentUrl = it }
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true

                            if (url != null && url.startsWith("http") && url != ERROR_PAGE_URL) {
                                activity.recordVisit(webTitle, url)
                            }

                            view?.evaluateJavascript("""
                                (function() {
                                    document.body.style.width = '100%';
                                    document.body.style.minWidth = 'auto';
                                    document.documentElement.style.width = '100%';
                                    document.documentElement.style.overflowX = 'hidden';
                                })();
                            """.trimIndent(), null)
                        }
                    }

                    if (isNetworkAvailable(activity)) {
                        loadUrl(startUrl)
                    } else {
                        loadUrl(ERROR_PAGE_URL)
                    }
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth()
        )

        // ========== 底部导航栏（Dock）– 透明，移除了刷新按钮 ==========
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = insets.calculateBottomPadding()),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { webView?.goBack() }, enabled = canGoBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "后退",
                    tint = if (canGoBack) colorScheme.primary else colorScheme.outlineVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(onClick = { webView?.goForward() }, enabled = canGoForward) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "前进",
                    tint = if (canGoForward) colorScheme.primary else colorScheme.outlineVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(onClick = { activity.finish() }) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "主页",
                    tint = colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(onClick = { showBottomSheet = true }) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "菜单",
                    tint = colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 外部跳转确认对话框
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("跳转应用") },
                text = { Text("检测到外部链接 \"$dialogHost\"，是否跳转到其他应用？") },
                confirmButton = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            showDialog = false
                            try {
                                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
                            } catch (e: Exception) {}
                        }) { Text("是") }
                        TextButton(onClick = { showDialog = false }) { Text("否") }
                        TextButton(onClick = {
                            showDialog = false
                            saveNoPrompt(sharedPrefs, dialogHost)
                            try {
                                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
                            } catch (e: Exception) {}
                        }) { Text("不再提示") }
                    }
                }
            )
        }

        // 剪贴板权限对话框
        if (showClipboardDialog && pendingClipboardRequest != null) {
            AlertDialog(
                onDismissRequest = {
                    pendingClipboardRequest?.deny()
                    showClipboardDialog = false
                    pendingClipboardRequest = null
                },
                title = { Text("剪贴板权限请求") },
                text = { Text("网站 \"$clipboardHost\" 请求读取您的剪贴板内容，是否允许？") },
                confirmButton = {
                    TextButton(onClick = {
                        pendingClipboardRequest?.grant(pendingClipboardRequest?.resources)
                        showClipboardDialog = false
                        pendingClipboardRequest = null
                    }) { Text("允许") }
                },
                dismissButton = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            pendingClipboardRequest?.deny()
                            showClipboardDialog = false
                            pendingClipboardRequest = null
                        }) { Text("禁止") }
                        TextButton(onClick = {
                            saveClipboardBlocked(sharedPrefs, clipboardHost)
                            pendingClipboardRequest?.deny()
                            showClipboardDialog = false
                            pendingClipboardRequest = null
                        }) { Text("永久禁止") }
                    }
                }
            )
        }

        // ========== iOS风格底部菜单对话框 ==========
        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                containerColor = colorScheme.surface,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .padding(bottom = 16.dp)
                ) {
                    // 收藏当前页面
                    TextButton(
                        onClick = {
                            showBottomSheet = false
                            activity.addToFavorites(webTitle, currentUrl)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("收藏当前页面", fontSize = 18.sp)
                    }
                    // 收藏夹
                    TextButton(
                        onClick = {
                            showBottomSheet = false
                            val intent = Intent(activity, Class.forName("com.rootes.app.FormActivity"))
                            activity.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("收藏夹", fontSize = 18.sp)
                    }
                    // 历史
                    TextButton(
                        onClick = {
                            showBottomSheet = false
                            val intent = Intent(activity, Class.forName("com.rootes.app.TimeActivity"))
                            activity.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("历史", fontSize = 18.sp)
                    }
                    // 设置
                    TextButton(
                        onClick = {
                            showBottomSheet = false
                            val intent = Intent(activity, Class.forName("com.rootes.browser.SettingsActivity"))
                            activity.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("设置", fontSize = 18.sp)
                    }
                    // 取消
                    TextButton(
                        onClick = { showBottomSheet = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("取消", fontSize = 18.sp, color = Color.Red)
                    }
                }
            }
        }
    }
}
