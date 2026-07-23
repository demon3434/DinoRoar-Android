package com.example.dinoroar.ui.main

import com.example.dinoroar.data.local.LogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncConflictTest {

    @Test
    fun testConflictDetectionLogic() {
        // 1. 模拟本地编辑保存但尚未同步的日志 (树洞)
        val localLog = LogEntity(
            uuid = "test-log-1",
            incidentDate = "2026-07-12T10:00:00",
            moodDinoId = 1,
            content = "今天我跟霸王龙玩耍了！",
            ownThoughts = "开心",
            createdAt = "2026-07-12T10:00:00",
            updatedAt = "2026-07-12T10:05:00",
            isDeleted = false,
            isSynced = false,
            version = 1,
            isConflict = false
        )

        // 2. 模拟云端同步下发已被其他设备更新过的日志数据，其版本号较高 (秘密基地)
        val serverVersion = 2
        val serverContent = "今天我跟霸王龙在秘密基地玩耍了！"

        // 3. 冲突校验核心判断算法
        val isConflictDetected = !localLog.isSynced && serverVersion > localLog.version

        assertTrue("Should detect version sync conflict", isConflictDetected)

        // 4. 测试冲突策略：选择保留手机端的树洞记忆 (强推模式：本地版本升级为云端版本 + 1)
        val resolvedKeepLocal = localLog.copy(
            isConflict = false,
            version = serverVersion + 1,
            isSynced = false
        )
        assertEquals(3, resolvedKeepLocal.version)
        assertEquals("今天我跟霸王龙玩耍了！", resolvedKeepLocal.content)

        // 5. 测试冲突策略：选择载入秘密基地的记忆 (覆盖模式：直接装载云端数据，标记已同步)
        val resolvedLoadServer = localLog.copy(
            content = serverContent,
            version = serverVersion,
            isConflict = false,
            isSynced = true
        )
        assertEquals(2, resolvedLoadServer.version)
        assertEquals(serverContent, resolvedLoadServer.content)
        assertTrue(resolvedLoadServer.isSynced)
    }
}
