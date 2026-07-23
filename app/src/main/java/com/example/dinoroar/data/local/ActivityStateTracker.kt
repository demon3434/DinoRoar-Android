package com.example.dinoroar.data.local

/**
 * 运行时跳出与前后台防护标志管理器 (内存级隔离，严禁磁盘存盘常驻)
 * 遵循《DinoRoar 开发者与 AI 协同约定》中【运行时状态内存隔离约定】。
 * 应用强杀或冷启动时自动复位为 false，彻底斩断因锁屏误判定引起的退栈/覆盖问题。
 */
object ActivityStateTracker {
    @Volatile
    var isCameraActive: Boolean = false

    @Volatile
    var isExternalActivityActive: Boolean = false

    fun reset() {
        isCameraActive = false
        isExternalActivityActive = false
    }
}
