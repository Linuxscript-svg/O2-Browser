package com.rootes.browser2

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 硬件加速，避免渲染异常
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        setContent {
            WebViewAppContent()
        }
    }
}

@Composable
fun WebViewAppContent() {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    // 核心状态：仅核心状态触发重组，避免循环
    var isMainLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var mainSiteUrl by remember { mutableStateOf<String?>(null) }
    var localHtmlContent by remember { mutableStateOf<String?>(null) }
    var localBaseUrl by remember { mutableStateOf<String?>(null) }

    // 修复1：WebView实例全局唯一，永远不会因为重组重复创建
    val webView = remember {
        WebView(context).apply {
            // WebView基础配置，仅创建时执行一次
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                // 本地HTML跨域权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    allowUniversalAccessFromFileURLs = true
                    allowFileAccessFromFileURLs = true
                }
                // 混合内容加载支持
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                loadsImagesAutomatically = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = false
                displayZoomControls = false
                // 标准User-Agent，避免被网站拦截
                userAgentString = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${Build.VERSION.RELEASE} Mobile Safari/537.36"
            }
            // 开启调试，开发时可打开
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                WebView.setWebContentsDebuggingEnabled(true)
            }
        }
    }

    // 修复2：WebViewClient用remember包裹，仅创建一次，不会重复设置
    val webViewClient = remember {
        object : WebViewClient() {
            // 仅主页面开始加载时修改状态，子资源不触发
            override fun onPageStarted(
                view: WebView?,
                url: String?,
                favicon: android.graphics.Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)
                if (view?.url == url) {
                    isMainLoading = true
                    errorMessage = null
                }
            }

            // 仅主页面加载完成时修改状态
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (view?.url == url) {
                    isMainLoading = false
                }
            }

            // 仅主页面加载失败才显示错误
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    isMainLoading = false
                    errorMessage = "页面加载失败：${error?.description}\n网址：${request.url}"
                }
            }

            // 核心跳转逻辑
            override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean {
                // 设置页跳转
                if (url.startsWith("settings://start")) {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                    return true
                }

                // 非http/https链接，留在当前页
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    return false
                }

                // 同域名站内跳转，留在当前页
                val mainHost = runCatching { Uri.parse(mainSiteUrl).host?.removePrefix("www.") }.getOrNull()
                val targetHost = runCatching { Uri.parse(url).host?.removePrefix("www.") }.getOrNull()
                if (mainHost != null && targetHost != null && mainHost == targetHost) {
                    return false
                }

                // 不同域名外链，跳正式浏览器
                return try {
                    val intent = Intent(
                        context,
                        Class.forName("com.rootes.browser.unit.WebActivity")
                    )
                    intent.putExtra("url", url)
                    context.startActivity(intent)
                    
                    true
                } catch (e: ClassNotFoundException) {
                    false
                }
            }
        }
    }

    // 初始化时给WebView设置Client，仅执行一次
    LaunchedEffect(Unit) {
        webView.webViewClient = webViewClient
    }

    // 核心初始化逻辑，仅执行一次
    LaunchedEffect(Unit) {
        // 重置所有状态
        isMainLoading = true
        errorMessage = null
        mainSiteUrl = null
        localHtmlContent = null
        localBaseUrl = null
        webView.stopLoading()
        webView.clearHistory()

        // 第一步：优先读取web_online.txt
        val onlineConfigFile = File(context.filesDir, "web_online.txt")
        if (onlineConfigFile.exists()) {
            try {
                val targetUrl = onlineConfigFile.readText().trim()
                if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
                    mainSiteUrl = targetUrl
                    isMainLoading = false
                    return@LaunchedEffect
                }
            } catch (e: IOException) {
                // 读取失败，回退本地HTML
            }
        }

        // 第二步：加载本地HTML
        val localHtmlFile = File(context.filesDir, "home/index.html")
        if (!localHtmlFile.exists()) {
            try {
                context.assets.open("home/index.html").use { inputStream ->
                    localHtmlFile.parentFile?.mkdirs()
                    FileOutputStream(localHtmlFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: IOException) {
                errorMessage = "本地主页初始化失败：${e.message}"
                isMainLoading = false
                return@LaunchedEffect
            }
        }

        // 读取本地HTML内容
        try {
            localHtmlContent = localHtmlFile.readText()
            localBaseUrl = "file://${localHtmlFile.parentFile?.absolutePath}/"
        } catch (e: IOException) {
            errorMessage = "本地主页读取失败：${e.message}"
        }

        isMainLoading = false
    }

    // 在线网址加载，仅mainSiteUrl变化时执行一次
    LaunchedEffect(mainSiteUrl) {
        mainSiteUrl?.let { url ->
            webView.stopLoading()
            webView.loadUrl(url)
        }
    }

    // 本地HTML加载，仅内容变化时执行一次
    LaunchedEffect(localHtmlContent, localBaseUrl) {
        val content = localHtmlContent
        val base = localBaseUrl
        if (content != null && base != null) {
            webView.stopLoading()
            webView.loadDataWithBaseURL(
                base,
                content,
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    // 页面UI
    Box(modifier = Modifier.fillMaxSize()) {
        // WebView固定在底层，只创建一次
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )

        // 加载中遮罩
        if (isMainLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        // 错误提示
        if (errorMessage != null && !isMainLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = errorMessage!!,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
    }
}
