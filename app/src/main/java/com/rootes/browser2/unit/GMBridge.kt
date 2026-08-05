package com.rootes.browser2.unit

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

// GM API 桥接实现
class GMBridge(
    private val context: Context,
    private val webView: WebView,
     val currentScript: UserScript
) {
    private val gson = Gson()
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // 脚本独立存储隔离
    private val scriptPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("gm_values_${currentScript.namespace}_${currentScript.name}", Context.MODE_PRIVATE)
    }

    // 脚本注册的菜单命令
    val registeredMenuCommands = mutableMapOf<String, () -> Unit>()

    // ==================== 核心存储API ====================
    @JavascriptInterface
    fun GM_setValue(key: String, value: Any) {
        val editor = scriptPrefs.edit()
        when (value) {
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Boolean -> editor.putBoolean(key, value)
            else -> editor.putString(key, gson.toJson(value))
        }
        editor.apply()
    }

    @JavascriptInterface
    fun GM_getValue(key: String, defaultValue: Any? = null): String? {
        if (!scriptPrefs.contains(key)) return gson.toJson(defaultValue)
        val all = scriptPrefs.all
        return gson.toJson(all[key])
    }

    @JavascriptInterface
    fun GM_deleteValue(key: String) {
        scriptPrefs.edit().remove(key).apply()
    }

    @JavascriptInterface
    fun GM_listValues(): String {
        return gson.toJson(scriptPrefs.all.keys)
    }

    // ==================== 样式API ====================
    @JavascriptInterface
    fun GM_addStyle(css: String) {
        webView.post {
            val js = """
                (function() {
                    const style = document.createElement('style');
                    style.textContent = ${gson.toJson(css)};
                    document.head.appendChild(style);
                })();
            """.trimIndent()
            webView.evaluateJavascript(js, null)
        }
    }

    // ==================== 跨域请求API ====================
    @JavascriptInterface
    fun GM_xmlhttpRequest(detailsJson: String) {
        try {
            val details = gson.fromJson(detailsJson, GMRequestDetails::class.java)
            val requestBuilder = Request.Builder().url(details.url)

            // 处理请求方法
            val method = details.method?.uppercase() ?: "GET"
            // 处理请求头
            details.headers?.forEach { (key, value) ->
                requestBuilder.addHeader(key, value.toString())
            }
            // 处理Cookie
            details.cookie?.let {
                requestBuilder.addHeader("Cookie", it)
            }
            // 处理请求体
            val requestBody = if (details.data != null) {
                val contentType = details.headers?.get("Content-Type")?.toString() ?: "text/plain; charset=utf-8"
                details.data.toString().toRequestBody(contentType.toMediaType())
            } else null

            requestBuilder.method(method, requestBody)
            // 超时设置
            val client = if (details.timeout != null) {
                okHttpClient.newBuilder()
                    .readTimeout(details.timeout.toLong(), TimeUnit.MILLISECONDS)
                    .connectTimeout(details.timeout.toLong(), TimeUnit.MILLISECONDS)
                    .build()
            } else okHttpClient

            // 异步请求
            client.newCall(requestBuilder.build()).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    webView.post {
                        val errorJs = details.onerror?.let {
                            """$it({ message: ${gson.toJson(e.message)} })"""
                        } ?: ""
                        webView.evaluateJavascript(errorJs, null)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string() ?: ""
                    val responseHeaders = response.headers.toMultimap().mapValues { it.value.joinToString("; ") }
                    webView.post {
                        val responseJson = gson.toJson(mapOf(
                            "responseText" to responseBody,
                            "status" to response.code,
                            "statusText" to response.message,
                            "readyState" to 4,
                            "responseHeaders" to responseHeaders,
                            "finalUrl" to response.request.url.toString()
                        ))
                        val successJs = details.onload?.let {
                            """$it($responseJson)"""
                        } ?: ""
                        webView.evaluateJavascript(successJs, null)
                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==================== 标签页API ====================
    @JavascriptInterface
    fun GM_openInTab(url: String, optionsJson: String? = null) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "打开链接失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== 通知API ====================
    @JavascriptInterface
    fun GM_notification(detailsJson: String, ondone: String? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(context, "请先开启通知权限", Toast.LENGTH_SHORT).show()
                return
            }
        }

        try {
            val details = gson.fromJson(detailsJson, GMNotificationDetails::class.java)
            val channelId = "gm_notification_channel"
            val notificationManager = NotificationManagerCompat.from(context)

            // 创建通知渠道
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "油猴脚本通知",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(details.title ?: currentScript.name)
                .setContentText(details.text ?: "")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(currentScript.name.hashCode(), notification)
            ondone?.let {
                webView.post { webView.evaluateJavascript(it, null) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==================== 剪贴板API ====================
    @JavascriptInterface
    fun GM_setClipboard(text: String, type: String = "text") {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("GM_clipboard", text)
        clipboardManager.setPrimaryClip(clip)
    }

    // ==================== 资源API ====================
    @JavascriptInterface
    fun GM_getResourceText(name: String): String {
        val resourceUrl = currentScript.resources[name] ?: return ""
        return try {
            val request = Request.Builder().url(resourceUrl).build()
            val response = okHttpClient.newCall(request).execute()
            response.body?.string() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    @JavascriptInterface
    fun GM_getResourceURL(name: String): String {
        val resourceUrl = currentScript.resources[name] ?: return ""
        return try {
            val request = Request.Builder().url(resourceUrl).build()
            val response = okHttpClient.newCall(request).execute()
            val bytes = response.body?.bytes() ?: return ""
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val mimeType = response.header("Content-Type") ?: "application/octet-stream"
            "data:$mimeType;base64,$base64"
        } catch (e: Exception) {
            ""
        }
    }

    // ==================== 菜单API ====================
    @JavascriptInterface
    fun GM_registerMenuCommand(caption: String, callback: String, accessKey: String? = null) {
        registeredMenuCommands[caption] = {
            webView.post { webView.evaluateJavascript(callback, null) }
        }
    }

    @JavascriptInterface
    fun GM_unregisterMenuCommand(caption: String) {
        registeredMenuCommands.remove(caption)
    }

    // ==================== 下载API ====================
    @JavascriptInterface
    fun GM_download(detailsJson: String) {
        try {
            val details = gson.fromJson(detailsJson, GMDownloadDetails::class.java)
            val url = details.url ?: return
            val fileName = details.name ?: url.substringAfterLast("/", "gm_download_${System.currentTimeMillis()}")

            val downloadDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            }
            val targetFile = File(downloadDir, fileName)

            // 异步下载
            Thread {
                try {
                    val request = Request.Builder().url(url).build()
                    val response = okHttpClient.newCall(request).execute()
                    val inputStream = response.body?.byteStream() ?: return@Thread
                    val outputStream = FileOutputStream(targetFile)

                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()

                    webView.post {
                        details.ontimeout?.let { webView.evaluateJavascript(it, null) }
                        Toast.makeText(context, "下载完成: $fileName", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    webView.post {
                        details.onerror?.let { webView.evaluateJavascript(it, null) }
                        Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==================== 日志API ====================
    @JavascriptInterface
    fun GM_log(message: String) {
        android.util.Log.d("GM_${currentScript.name}", message)
    }

    // ==================== 信息对象 ====================
    @JavascriptInterface
    fun getGMInfo(): String {
        return gson.toJson(mapOf(
            "script" to mapOf(
                "name" to currentScript.name,
                "version" to currentScript.version,
                "description" to currentScript.description,
                "author" to currentScript.author,
                "matches" to currentScript.matchRules,
                "grant" to currentScript.grantApis,
                "resources" to currentScript.resources
            ),
            "scriptMetaStr" to currentScript.scriptContent.substringBefore("// ==/UserScript=="),
            "version" to "1.0.0",
            "platform" to "Android WebView",
            "browserName" to "RootesBrowser",
            "browserVersion" to "1.0.2"
        ))
    }

    // 数据模型类
    private data class GMRequestDetails(
        val method: String? = null,
        val url: String = "",
        val headers: Map<String, Any>? = null,
        val data: Any? = null,
        val cookie: String? = null,
        val timeout: Int? = null,
        val onload: String? = null,
        val onerror: String? = null,
        val onprogress: String? = null
    )

    private data class GMNotificationDetails(
        val title: String? = null,
        val text: String? = null,
        val image: String? = null,
        val timeout: Int? = null
    )

    private data class GMDownloadDetails(
        val url: String? = null,
        val name: String? = null,
        val onerror: String? = null,
        val onload: String? = null,
        val onprogress: String? = null,
        val ontimeout: String? = null
    )
}
