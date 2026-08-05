package com.rootes.browser2.unit

import android.content.Context
import android.webkit.WebView
import com.google.gson.Gson

object TampermonkeyEngine {
    private val gson = Gson()
    private var cachedScripts: List<UserScript> = emptyList()
    private var lastLoadTime: Long = 0
    private const val CACHE_TIMEOUT = 5000L // 5秒缓存，避免频繁读取文件

    // 刷新脚本缓存
    fun refreshScripts(context: Context) {
        cachedScripts = UserScriptParser.loadAllScripts(context)
        lastLoadTime = System.currentTimeMillis()
    }

   /** 只加载启用的脚本，注入到页面
fun getAllScripts(context: Context): List<UserScript> {
    if (System.currentTimeMillis() - lastLoadTime > CACHE_TIMEOUT) {
        cachedScripts = UserScriptParser.loadAllScripts(context).filter { it.isEnabled } // 新增过滤：只保留启用的脚本
        lastLoadTime = System.currentTimeMillis()
    }
    return cachedScripts
}**/


// 获取所有脚本
    fun getAllScripts(context: Context): List<UserScript> {
        if (System.currentTimeMillis() - lastLoadTime > CACHE_TIMEOUT) {
            refreshScripts(context)
        }
        return cachedScripts
    }
   


    // 注入GM API 桥接代码到WebView
    fun injectBridge(webView: WebView, script: UserScript): GMBridge {
        val bridge = GMBridge(webView.context, webView, script)
        webView.addJavascriptInterface(bridge, "GMAndroidBridge_${script.fileName.hashCode()}")
        return bridge
    }

    // 生成GM API JS封装代码
    private fun generateGMJsCode(bridgeName: String, grantApis: List<String>): String {
        val baseJs = """
            // GM API 封装层
            const GM_info = JSON.parse(window['$bridgeName'].getGMInfo());
            
            function GM_setValue(key, value) {
                window['$bridgeName'].GM_setValue(key, value);
            }
            function GM_getValue(key, defaultValue) {
                return JSON.parse(window['$bridgeName'].GM_getValue(key, defaultValue));
            }
            function GM_deleteValue(key) {
                window['$bridgeName'].GM_deleteValue(key);
            }
            function GM_listValues() {
                return JSON.parse(window['$bridgeName'].GM_listValues());
            }
            function GM_addStyle(css) {
                window['$bridgeName'].GM_addStyle(css);
            }
            function GM_xmlhttpRequest(details) {
                window['$bridgeName'].GM_xmlhttpRequest(JSON.stringify(details));
            }
            function GM_openInTab(url, options) {
                window['$bridgeName'].GM_openInTab(url, JSON.stringify(options));
            }
            function GM_notification(details, ondone) {
                window['$bridgeName'].GM_notification(JSON.stringify(details), ondone);
            }
            function GM_setClipboard(text, type) {
                window['$bridgeName'].GM_setClipboard(text, type);
            }
            function GM_getResourceText(name) {
                return window['$bridgeName'].GM_getResourceText(name);
            }
            function GM_getResourceURL(name) {
                return window['$bridgeName'].GM_getResourceURL(name);
            }
            function GM_registerMenuCommand(caption, callback, accessKey) {
                window['$bridgeName'].GM_registerMenuCommand(caption, callback.toString(), accessKey);
            }
            function GM_unregisterMenuCommand(caption) {
                window['$bridgeName'].GM_unregisterMenuCommand(caption);
            }
            function GM_download(details) {
                window['$bridgeName'].GM_download(JSON.stringify(details));
            }
            function GM_log(message) {
                window['$bridgeName'].GM_log(message);
            }
            
            // 兼容GM.* 写法
            const GM = {
                info: GM_info,
                setValue: GM_setValue,
                getValue: GM_getValue,
                deleteValue: GM_deleteValue,
                listValues: GM_listValues,
                addStyle: GM_addStyle,
                xmlHttpRequest: GM_xmlhttpRequest,
                openInTab: GM_openInTab,
                notification: GM_notification,
                setClipboard: GM_setClipboard,
                getResourceText: GM_getResourceText,
                getResourceURL: GM_getResourceURL,
                registerMenuCommand: GM_registerMenuCommand,
                unregisterMenuCommand: GM_unregisterMenuCommand,
                download: GM_download,
                log: GM_log
            };
        """.trimIndent()

        // 只保留脚本@grant声明的API，避免污染全局
        return if (grantApis.contains("none") || grantApis.isEmpty()) {
            ""
        } else {
            baseJs
        }
    }

    // 按注入时机注入脚本
    fun injectScriptsByTiming(
        webView: WebView,
        context: Context,
        url: String,
        isMainFrame: Boolean,
        runAt: String
    ): List<GMBridge> {
        val scripts = getAllScripts(context)
        val injectedBridges = mutableListOf<GMBridge>()

        scripts.forEach { script ->
            // 过滤匹配当前页面、符合注入时机的脚本
            if (script.runAt != runAt) return@forEach
            if (!UserScriptParser.isUrlMatch(script, url, isMainFrame)) return@forEach

            // 注入桥接与脚本
            val bridge = injectBridge(webView, script)
            val bridgeName = "GMAndroidBridge_${script.fileName.hashCode()}"
            val gmJsCode = generateGMJsCode(bridgeName, script.grantApis)

            // 完整注入代码
            val fullInjectJs = """
                (function() {
                    'use strict';
                    $gmJsCode
                    ${script.scriptContent}
                })();
            """.trimIndent()

            webView.evaluateJavascript(fullInjectJs, null)
            injectedBridges.add(bridge)
        }

        return injectedBridges
    }
}
