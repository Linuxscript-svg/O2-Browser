package com.rootes.browser2.unit

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

// 油猴脚本元数据模型
data class UserScript(
    val fileName: String,
    val name: String,
    val namespace: String,
    val version: String,
    val description: String,
    val author: String,
    val matchRules: List<String>,
    val excludeRules: List<String>,
    val grantApis: List<String>,
    val runAt: String,
    val noFrames: Boolean,
    val resources: Map<String, String>,
    val requires: List<String>,
    val scriptContent: String,
    val fileLastModified: Long,
    val isEnabled: Boolean // 新增：启用状态
)

// 油猴脚本元数据解析器
object UserScriptParser {
    // 元数据块正则
    private val metaBlockPattern = Pattern.compile("// ==UserScript==([\\s\\S]*?)// ==/UserScript==")
    // 单行元数据正则
    private val metaLinePattern = Pattern.compile("//\\s*@(\\w+)\\s+(.*)")
    // OkHttp客户端（修复：正确构建客户端）
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // 通配符转正则
    private fun wildcardToRegex(wildcard: String): Regex {
        val regex = wildcard
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
            .replace("://", "://.*")
        return Regex("^$regex$", RegexOption.IGNORE_CASE)
    }

    // 匹配URL是否符合规则
    fun isUrlMatch(script: UserScript, url: String, isMainFrame: Boolean): Boolean {
        // 禁用的脚本直接不匹配
        if (!script.isEnabled) return false
        // 禁止iframe注入
        if (script.noFrames && !isMainFrame) return false
        // 排除规则优先
        script.excludeRules.forEach { rule ->
            if (wildcardToRegex(rule).matches(url)) return false
        }
        // 匹配规则
        script.matchRules.forEach { rule ->
            if (wildcardToRegex(rule).matches(url)) return true
        }
        // 无匹配规则则不注入
        return script.matchRules.isEmpty()
    }

    // 解析单个脚本文件
    fun parseScriptFile(file: File): UserScript? {
        if (!file.exists()) return null
        // 识别启用状态：.user.js=启用 .user.jsoff=禁用
        val isEnabled = file.name.endsWith(".user.js", ignoreCase = true)
        val isScriptFile = file.name.endsWith(".user.js", ignoreCase = true) || file.name.endsWith(".user.jsoff", ignoreCase = true)
        if (!isScriptFile) return null

        return try {
            val content = file.readText()
            val metaMatcher = metaBlockPattern.matcher(content)
            if (!metaMatcher.find()) return null

            val metaBlock = metaMatcher.group(1) ?: ""
            val metaMap = mutableMapOf<String, MutableList<String>>()

            // 解析所有元数据行
            metaBlock.split("\n").forEach { line ->
                val lineMatcher = metaLinePattern.matcher(line.trim())
                if (lineMatcher.find()) {
                    val key = lineMatcher.group(1)?.lowercase() ?: return@forEach
                    val value = lineMatcher.group(2)?.trim() ?: return@forEach
                    metaMap.getOrPut(key) { mutableListOf() }.add(value)
                }
            }

            UserScript(
                fileName = file.name,
                name = metaMap["name"]?.firstOrNull() ?: file.name,
                namespace = metaMap["namespace"]?.firstOrNull() ?: "com.rootes.browser",
                version = metaMap["version"]?.firstOrNull() ?: "1.0.0",
                description = metaMap["description"]?.firstOrNull() ?: "无描述",
                author = metaMap["author"]?.firstOrNull() ?: "未知作者",
                matchRules = metaMap["match"] ?: emptyList(),
                excludeRules = metaMap["exclude"] ?: emptyList(),
                grantApis = metaMap["grant"] ?: emptyList(),
                runAt = metaMap["run-at"]?.firstOrNull() ?: "document-idle",
                noFrames = metaMap.containsKey("noframes"),
                resources = metaMap["resource"]?.associate {
                    val parts = it.split(" ", limit = 2)
                    parts[0] to parts.getOrElse(1) { "" }
                } ?: emptyMap(),
                requires = metaMap["require"] ?: emptyList(),
                scriptContent = content,
                fileLastModified = file.lastModified(),
                isEnabled = isEnabled
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 批量加载js目录下所有脚本（含禁用状态）
    fun loadAllScripts(context: Context): List<UserScript> {
        val jsDir = getScriptDir(context)
        if (!jsDir.exists()) jsDir.mkdirs()
        val scripts = mutableListOf<UserScript>()
        jsDir.listFiles()?.forEach { file ->
            parseScriptFile(file)?.let { scripts.add(it) }
        }
        // 启用的在前，禁用的在后，按修改时间倒序
        return scripts.sortedWith(compareByDescending<UserScript> { it.isEnabled }.thenByDescending { it.fileLastModified })
    }

    // 获取脚本存放目录
    fun getScriptDir(context: Context): File {
        return File(context.filesDir, "js").apply { if (!exists()) mkdirs() }
    }

    // ========== 管理功能：启用/禁用脚本 ==========
    fun toggleScriptEnable(context: Context, script: UserScript): Boolean {
        val jsDir = getScriptDir(context)
        val oldFile = File(jsDir, script.fileName)
        if (!oldFile.exists()) return false

        val newFileName = if (script.isEnabled) {
            // 启用→禁用：改后缀为.jsoff
            script.fileName.replace(".user.js", ".user.jsoff", ignoreCase = true)
        } else {
            // 禁用→启用：改后缀为.user.js
            script.fileName.replace(".user.jsoff", ".user.js", ignoreCase = true)
        }
        val newFile = File(jsDir, newFileName)

        return try {
            oldFile.renameTo(newFile)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ========== 管理功能：删除脚本 ==========
    fun deleteScript(context: Context, script: UserScript): Boolean {
        val file = File(getScriptDir(context), script.fileName)
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    // ========== 管理功能：保存脚本到本地 ==========
    fun saveScript(context: Context, scriptContent: String, fileName: String): UserScript? {
        val jsDir = getScriptDir(context)
        // 确保文件名正确
        val finalFileName = if (fileName.endsWith(".user.js", ignoreCase = true)) {
            fileName
        } else {
            "$fileName.user.js"
        }
        val targetFile = File(jsDir, finalFileName)

        return try {
            targetFile.writeText(scriptContent)
            // 解析验证脚本
            parseScriptFile(targetFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ========== 管理功能：OkHttp下载在线脚本 ==========
    suspend fun downloadScriptFromUrl(context: Context, url: String): UserScript? {
        return try {
            // OkHttp发起请求
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val scriptContent = response.body?.string() ?: return null
            // 验证是否是油猴脚本
            if (!metaBlockPattern.matcher(scriptContent).find()) return null

            // 从url提取文件名
            val fileName = url.substringAfterLast("/", "script_${System.currentTimeMillis()}")
            // 保存并解析脚本
            saveScript(context, scriptContent, fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}