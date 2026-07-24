package com.example.dinoroar.data.sync

import android.util.Log
import com.example.dinoroar.data.local.PersonDao
import com.example.dinoroar.data.local.PersonEntity
import com.example.dinoroar.data.local.PersonCategoryEntity
import com.example.dinoroar.network.DinoApiService
import com.example.dinoroar.network.PersonSyncItem
import com.example.dinoroar.network.PersonSyncPayload
import com.example.dinoroar.network.PersonCategorySyncItem
import com.example.dinoroar.network.PersonCategorySyncPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

import com.example.dinoroar.data.local.SecurePrefs

@Singleton
class RelationshipSyncer @Inject constructor(
    private val personDao: PersonDao,
    private val apiService: DinoApiService,
    private val securePrefs: SecurePrefs
) {
    private val TAG = "RelationshipSyncer"

    suspend fun syncRelationship() = withContext(Dispatchers.IO) {
        val userId = securePrefs.currentUserId
        // ==================== 阶段〇：同步关系人分类库 ====================
        Log.i(TAG, "Syncing person categories for user $userId...")
        val localCategories = personDao.getAllCategoriesIncludingDeleted(userId)
        val toSyncCategories = localCategories.filter { !it.isDeleted }.map {
            PersonCategorySyncItem(
                uuid = it.uuid,
                name = it.name,
                sort_order = it.sortOrder,
                created_at = it.createdAt
            )
        }
        val deletedCategoryUuids = localCategories.filter { it.isDeleted }.map { it.uuid }
        val categoryPayload = PersonCategorySyncPayload(categories = toSyncCategories, deleted_uuids = deletedCategoryUuids)
        
        try {
            val activeServerCategories = apiService.syncCategories(categoryPayload)
            
            // 将云端最新活跃分类写入本地
            activeServerCategories.forEach { serverCat ->
                val categoryEntity = PersonCategoryEntity(
                    uuid = serverCat.uuid,
                    userId = userId,
                    name = serverCat.name,
                    sortOrder = serverCat.sort_order,
                    createdAt = serverCat.created_at ?: java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date()),
                    isDeleted = false
                )
                personDao.insertOrUpdateCategory(categoryEntity)
            }
            
            // 逻辑同步：如果本地处于活跃状态但不在云端活跃分类里，在本地将其置为已逻辑删除
            val serverCategoryUuids = activeServerCategories.map { it.uuid }.toSet()
            val currentLocalCats = personDao.getAllCategories(userId)
            currentLocalCats.forEach { localCat ->
                if (!localCat.isDeleted && !serverCategoryUuids.contains(localCat.uuid)) {
                    personDao.insertOrUpdateCategory(localCat.copy(isDeleted = true, userId = userId))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Category synchronization failed: ${e.message}", e)
            throw e
        }

        // ==================== 阶段一：同步关系人物库 ====================
        Log.i(TAG, "Syncing person database for user $userId...")
        val unsyncedPersons = personDao.getUnsyncedPersons(userId)
        val toSyncPersons = unsyncedPersons.filter { !it.isDeleted }.map {
            PersonSyncItem(
                uuid = it.uuid,
                name = it.name,
                abbreviation = it.abbreviation,
                relationship = it.relationship,
                category_uuid = it.categoryUuid,
                sort_order = it.sortOrder,
                color_tag = it.colorTag,
                is_temporary = it.isTemporary,
                created_at = it.createdAt
            )
        }
        val deletedPersonUuids = unsyncedPersons.filter { it.isDeleted }.map { it.uuid }
        val personPayload = PersonSyncPayload(persons = toSyncPersons, deleted_uuids = deletedPersonUuids)
        
        try {
            val activeServerPersons = apiService.syncPersons(personPayload)
            
            // 标记本地人物已同步
            unsyncedPersons.forEach {
                personDao.markSynced(it.uuid)
            }
            
            // 将服务器下发的人物更新/插入本地
            activeServerPersons.forEach { serverPerson ->
                val personEntity = PersonEntity(
                    uuid = serverPerson.uuid,
                    userId = userId,
                    name = serverPerson.name,
                    abbreviation = serverPerson.abbreviation,
                    relationship = serverPerson.relationship,
                    categoryUuid = serverPerson.category_uuid,
                    sortOrder = serverPerson.sort_order,
                    colorTag = serverPerson.color_tag,
                    isTemporary = serverPerson.is_temporary,
                    createdAt = serverPerson.created_at,
                    isDeleted = false,
                    isSynced = true
                )
                personDao.insertOrUpdate(personEntity)
            }

            // 逻辑同步：若本地是活跃状态且已同步，但不在云端活跃人物列表中，我们将其设为已删除
            val serverPersonUuids = activeServerPersons.map { it.uuid }.toSet()
            val allLocalPersons = personDao.getAllActivePersons(userId)
            allLocalPersons.forEach { localPerson ->
                if (localPerson.isSynced && !serverPersonUuids.contains(localPerson.uuid)) {
                    personDao.insertOrUpdate(localPerson.copy(isDeleted = true, isSynced = true))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Person synchronization failed: ${e.message}", e)
            throw e
        }
    }
}
