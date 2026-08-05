package com.rootes.browser2.unit
data class SettingsData(
    val webJs: Boolean = true,        // true=不加载JS 默认true
    val webSsl: Boolean = false,      // true=跳过SSL验证 默认false
    val download: Boolean = true,     // true=允许下载 默认true
    val downloadFiles: String = "/sdcard/Download", // 默认下载目录
    val apps: Boolean = true,
    val isPd: Boolean = false // 新增：全局剪贴板拦截开关，默认关闭
    
 
)
