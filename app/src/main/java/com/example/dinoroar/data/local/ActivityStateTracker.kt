package com.example.dinoroar.data.local

import java.util.concurrent.atomic.AtomicInteger

/**
 * 运行时跳出与前后台防护标志管理器 (内存级隔离，严禁磁盘存盘常驻)
 * 遵循《DinoRoar 开发者与 AI 协同约定》中【运行时状态内存隔离约定】。
 * 采用原子计数器跟踪子 Activity 完整生命周期（onCreate~onDestroy），
 * 彻底杜绝因前后台生命周期异步时序（MainActivity.onStop 在子Activity.onCreate之后触发）引发的返回误锁屏问题。
 */
object ActivityStateTracker {
    @Volatile
    var isCameraActive: Boolean = false

    private val externalActivityCounter = AtomicInteger(0)

    var isExternalActivityActive: Boolean
        get() = externalActivityCounter.get() > 0
        set(value) {
            if (value) {
                externalActivityCounter.incrementAndGet()
            } else {
                if (externalActivityCounter.get() > 0) {
                    externalActivityCounter.decrementAndGet()
                }
            }
        }

    fun onExternalActivityStarted() {
        externalActivityCounter.incrementAndGet()
    }

    fun onExternalActivityDestroyed() {
        if (externalActivityCounter.get() > 0) {
            externalActivityCounter.decrementAndGet()
        }
    }

    fun reset() {
        isCameraActive = false
        externalActivityCounter.set(0)
    }
}

