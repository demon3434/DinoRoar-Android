package com.example.dinoroar.media

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticLogger {
    private const val MAX_LOGS = 1000
    private const val ENABLE_DEBUG_LOGS = false // 正式环境已关闭测试日志输出
    private val logs = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun log(tag: String, level: String, message: String, throwable: Throwable? = null) {
        // 调试阶段日志在生产环境自动丢弃，仅在出现严重 Error/Exception 时予以记录
        if (!ENABLE_DEBUG_LOGS && level != "E") {
            return
        }

        val timestamp = dateFormat.format(Date())
        val exceptionStr = throwable?.let {
            "\n" + android.util.Log.getStackTraceString(it)
        } ?: ""
        val formattedMsg = "[$timestamp] [$level/$tag] $message$exceptionStr"
        
        when (level) {
            "D" -> android.util.Log.d(tag, message, throwable)
            "I" -> android.util.Log.i(tag, message, throwable)
            "W" -> android.util.Log.w(tag, message, throwable)
            "E" -> android.util.Log.e(tag, message, throwable)
            else -> android.util.Log.i(tag, message, throwable)
        }

        synchronized(logs) {
            if (logs.size >= MAX_LOGS) {
                logs.removeAt(0)
            }
            logs.add(formattedMsg)
        }
    }

    fun getLogs(): String {
        return synchronized(logs) {
            logs.joinToString("\n")
        }
    }

    fun clear() {
        logs.clear()
    }
}
