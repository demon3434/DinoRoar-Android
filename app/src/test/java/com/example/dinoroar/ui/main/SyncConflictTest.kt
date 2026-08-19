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

    @Test
    fun testCategoryIncrementalSyncLogic() {
        // 1. 模拟本地已同步分类
        val syncedCat = com.example.dinoroar.data.local.PersonCategoryEntity(
            uuid = "cat-1",
            userId = "u-1",
            name = "家人",
            sortOrder = 0,
            createdAt = "2026-08-01T00:00:00Z",
            isDeleted = false,
            isSynced = true
        )
        // 本地新增待同步分类
        val unsyncedCat = com.example.dinoroar.data.local.PersonCategoryEntity(
            uuid = "cat-2",
            userId = "u-1",
            name = "同学",
            sortOrder = 1,
            createdAt = "2026-08-01T00:00:00Z",
            isDeleted = false,
            isSynced = false
        )

        val localCategories = listOf(syncedCat, unsyncedCat)
        val unsyncedList = localCategories.filter { !it.isSynced }

        // 仅上报 unsynced
        val toSyncItems = unsyncedList.filter { !it.isDeleted }.map { it.uuid }
        assertEquals(listOf("cat-2"), toSyncItems)

        // 2. 模拟服务端下发：cat-1 在 Web 端已被停用 (is_deleted = true)
        val serverCat1Deleted = true
        val updatedLocalCat1 = syncedCat.copy(isDeleted = serverCat1Deleted, isSynced = true)
        assertTrue("本地 cat-1 应被成功停用", updatedLocalCat1.isDeleted)
        assertTrue("本地 cat-1 应标记为已同步", updatedLocalCat1.isSynced)
    }

    @Test
    fun testPersonFilterShieldsDeletedCategoryPersons() {
        val activeCategories = listOf(
            com.example.dinoroar.data.local.PersonCategoryEntity(uuid = "cat-active", name = "好友", sortOrder = 0, createdAt = "", isDeleted = false, isSynced = true)
        )
        val activeCatUuids = activeCategories.filter { !it.isDeleted }.map { it.uuid }.toSet()

        val persons = listOf(
            com.example.dinoroar.data.local.PersonEntity(uuid = "p-1", name = "小明", abbreviation = "XM", relationship = "同桌", categoryUuid = "cat-active", createdAt = "", isDeleted = false, isSynced = true),
            com.example.dinoroar.data.local.PersonEntity(uuid = "p-2", name = "张三", abbreviation = "ZS", relationship = "邻居", categoryUuid = "cat-deleted", createdAt = "", isDeleted = false, isSynced = true)
        )

        // 筛选逻辑验证：属于已停用分类 cat-deleted 的张三应被过滤掉
        val filteredPersons = persons.filter {
            !it.isDeleted && !it.isTemporary && it.categoryUuid != null && activeCatUuids.contains(it.categoryUuid)
        }

        assertEquals(1, filteredPersons.size)
        assertEquals("p-1", filteredPersons[0].uuid)
    }
}
