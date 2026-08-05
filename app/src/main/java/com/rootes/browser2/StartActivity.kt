package com.rootes.browser2

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.view.Gravity
import java.io.File

class StartActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 启动文字（保留你原来的界面不变）
        val tv = TextView(this)
        tv.text = "启动中..."
        tv.textSize = 20f
        tv.gravity = Gravity.CENTER
        setContentView(tv)

        // ========== 修复1：先定义所有文件对象，先定义再使用，修复未定义引用报错 ==========
        // 自定义链接主页配置文件（优先级最高）
        val customLinkFile = File(filesDir, "web_online.txt")
        // 自定义HTML主页文件（优先级次之）
        val customHtmlFile = File(filesDir, "home/index.html")

        // ========== 修复2：只声明一次targetIntent，修复重复声明变量报错 ==========
        // 优先级：自定义链接主页 > 自定义HTML主页 > 默认主页
        val targetIntent = when {
            // 有自定义链接配置，跳HomeActivity
            customLinkFile.exists() -> Intent(this, HomeActivity::class.java)
            // 没有链接配置，但有自定义HTML，也跳HomeActivity
            customHtmlFile.exists() -> Intent(this, HomeActivity::class.java)
            // 都没有，跳默认主页
            else -> Intent(this, MainActivity::class.java)
        }

        // 执行跳转
        startActivity(targetIntent)
        finish()
    }
}
