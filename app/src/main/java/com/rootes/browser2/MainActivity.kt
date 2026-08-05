package com.rootes.browser2

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.rootes.browser2.ui.theme.MyComposeApplicationTheme
import com.rootes.browser2.unit.WebActivity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)

        window.apply {
            statusBarColor = android.graphics.Color.TRANSPARENT
            navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isNavigationBarContrastEnforced = false
                isStatusBarContrastEnforced = false
            }
            WindowCompat.setDecorFitsSystemWindows(this, false)
        }

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            MyComposeApplicationTheme {
                Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
                    BrowserHomeScreen(
                        onSearch = { input ->
                            val searchUrl = getSearchUrlFromConfig(input)
                            startWeb(searchUrl)
                        }
                    )
                }
            }
        }
    }

    private fun getSearchUrlFromConfig(input: String): String {
        val trimInput = input.trim()
        when {
            trimInput.startsWith("http://") || trimInput.startsWith("https://") -> return trimInput
            trimInput.matches(Regex("^www\\..+\\..+$")) -> return "https://$trimInput"
            trimInput.matches(Regex("^[a-zA-Z0-9-]+\\.[a-zA-Z]{2,}(/.*)?$")) -> return "https://$trimInput"
        }

        val configFile = File(filesDir, "launch_web.txt")
        if (!configFile.exists()) {
            val defaultContent = "launch_web=\"baidu\""
            FileOutputStream(configFile).use { it.write(defaultContent.toByteArray()) }
        }

        val engine = try {
            val content = FileInputStream(configFile).bufferedReader().use { it.readText() }
            val regex = Regex("launch_web=\"([^\"]+)\"")
            regex.find(content)?.groupValues?.get(1)?.trim()?.lowercase() ?: "baidu"
        } catch (e: Exception) {
            "baidu"
        }

        val encodedInput = java.net.URLEncoder.encode(trimInput, "UTF-8")
        return when (engine) {
            "bing" -> "https://m.bing.com/search?q=$encodedInput"
            "google" -> "https://google.com/search?q=$encodedInput"
            else -> "https://www.baidu.com/s?wd=$encodedInput"
        }
    }

    private fun startWeb(url: String) {
        try {
            val intent = Intent(this, WebActivity::class.java)
            intent.putExtra("url", url)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "页面打开失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun BrowserHomeScreen(
    onSearch: (String) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchText by remember { mutableStateOf("") }

    fun handleSearch(query: String) {
        val trimQuery = query.trim()
        if (trimQuery.isNotEmpty()) {
            onSearch(trimQuery)
            searchText = ""
            keyboardController?.hide()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景图片
        AsyncImage(
            model = "file:///android_asset/NoWIFI.png",
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // 半透明遮罩
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.2f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 问候文字：美好的一天
                Text(
                    text = "美好的一天",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // 搜索框
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    placeholder = {
                        Text("搜索或输入网址", color = Color.White.copy(alpha = 0.7f))
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索",
                            modifier = Modifier.clickable {
                                if (searchText.isNotBlank()) {
                                    handleSearch(searchText)
                                }
                            },
                            tint = Color.White
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(32.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchText.isNotBlank()) {
                                handleSearch(searchText)
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.2f),
                        focusedBorderColor = Color.White.copy(alpha = 0.8f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        }
    }
}