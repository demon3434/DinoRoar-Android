package com.example.dinoroar.data

import com.example.dinoroar.data.local.AttachmentDao
import com.example.dinoroar.data.local.AttachmentEntity
import com.example.dinoroar.data.local.LogDao
import com.example.dinoroar.data.local.LogEntity
import com.example.dinoroar.data.local.PersonDao
import com.example.dinoroar.data.local.LogPersonDao
import com.example.dinoroar.data.local.PersonEntity
import com.example.dinoroar.data.local.LogPersonCrossRef
import com.example.dinoroar.data.local.DinoDatabase
import com.example.dinoroar.data.local.PersonCategoryEntity
import com.example.dinoroar.data.local.DinoConfigEntity
import com.example.dinoroar.data.local.DinoConfigDao
import com.example.dinoroar.data.local.SecurePrefs
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

interface DataRepository {
    val allLogs: Flow<List<LogEntity>>
    val allLogsWithConfig: Flow<List<com.example.dinoroar.data.local.LogWithConfig>>
    val allAttachmentsFlow: Flow<List<AttachmentEntity>>
    val allCrossRefsFlow: Flow<List<LogPersonCrossRef>>
    fun searchLogs(query: String): Flow<List<LogEntity>>
    suspend fun getLogByUuid(uuid: String): LogEntity?
    suspend fun getLogWithConfigByUuid(uuid: String): com.example.dinoroar.data.local.LogWithConfig?
    suspend fun insertLog(log: LogEntity, personUuids: List<String> = emptyList())
    suspend fun softDeleteLog(uuid: String)
    
    fun getAttachmentsForLogFlow(logUuid: String): Flow<List<AttachmentEntity>>
    suspend fun getAttachmentsForLog(logUuid: String): List<AttachmentEntity>
    suspend fun insertAttachment(attachment: AttachmentEntity)
    suspend fun deleteAttachment(uuid: String)
    suspend fun softDeleteAttachment(uuid: String)
    suspend fun getAllSyncedAttachments(): List<AttachmentEntity>

    val allPersons: Flow<List<PersonEntity>>
    suspend fun getAllActivePersons(): List<PersonEntity>
    suspend fun getPersonsForLog(logUuid: String): List<PersonEntity>
    fun getPersonsForLogFlow(logUuid: String): Flow<List<PersonEntity>>
    suspend fun insertPerson(person: PersonEntity)
    suspend fun softDeletePerson(uuid: String)
    suspend fun getPersonByUuid(uuid: String): PersonEntity?
    suspend fun getAllPersonsWithTemporary(): List<PersonEntity>
    val deletedPersons: Flow<List<PersonEntity>>
    val deletedCategories: Flow<List<PersonCategoryEntity>>

    // === Category & Ordering ===
    val allCategories: Flow<List<PersonCategoryEntity>>
    suspend fun getAllCategories(): List<PersonCategoryEntity>
    suspend fun getAllCategoriesIncludingDeleted(): List<PersonCategoryEntity>
    suspend fun insertCategory(category: PersonCategoryEntity)
    suspend fun deleteCategory(categoryUuid: String)
    suspend fun updateCategoriesOrder(categories: List<PersonCategoryEntity>)
    suspend fun updatePersonsOrder(persons: List<PersonEntity>)
    suspend fun getRecentPersons(): List<PersonEntity>
    fun getAllActiveDinoConfigsFlow(): Flow<List<DinoConfigEntity>>
    suspend fun getAllActiveDinoConfigs(): List<DinoConfigEntity>
    suspend fun syncDinoConfig(dinoConfigs: List<DinoConfigEntity>)
    suspend fun clearAllData()
    suspend fun getLogCanvasByUuid(logUuid: String): com.example.dinoroar.data.local.LogCanvasEntity?
    fun getLogCanvasByUuidFlow(logUuid: String): Flow<com.example.dinoroar.data.local.LogCanvasEntity?>
    fun getCanvasImageUrlFlow(logUuid: String): Flow<String?>
    suspend fun getCanvasInstanceById(instanceId: Int): com.example.dinoroar.data.local.CanvasInstanceEntity?
    suspend fun getLastUsedLogCanvas(): com.example.dinoroar.data.local.LogCanvasEntity?
    suspend fun insertLogCanvas(logCanvas: com.example.dinoroar.data.local.LogCanvasEntity)
}

@Singleton
class DefaultDataRepository @Inject constructor(
    private val database: DinoDatabase,
    private val logDao: LogDao,
    private val attachmentDao: AttachmentDao,
    private val personDao: PersonDao,
    private val logPersonDao: LogPersonDao,
    private val dinoConfigDao: DinoConfigDao,
    private val canvasInstanceDao: com.example.dinoroar.data.local.CanvasInstanceDao,
    private val logCanvasDao: com.example.dinoroar.data.local.LogCanvasDao,
    private val securePrefs: SecurePrefs
) : DataRepository {

    private val currentUserId: String
        get() = securePrefs.currentUserId

    @OptIn(ExperimentalCoroutinesApi::class)
    override val allLogs: Flow<List<LogEntity>> = securePrefs.currentUserIdFlow.flatMapLatest { userId ->
        logDao.getAllActiveLogsFlow(userId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val allLogsWithConfig: Flow<List<com.example.dinoroar.data.local.LogWithConfig>> = securePrefs.currentUserIdFlow.flatMapLatest { userId ->
        logDao.getAllActiveLogsWithConfigFlow(userId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val allAttachmentsFlow: Flow<List<AttachmentEntity>> = securePrefs.currentUserIdFlow.flatMapLatest { userId ->
        attachmentDao.getAllActiveAttachmentsFlow(userId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val allCrossRefsFlow: Flow<List<LogPersonCrossRef>> = securePrefs.currentUserIdFlow.flatMapLatest { userId ->
        logPersonDao.getAllCrossRefsFlow(userId)
    }

    override fun searchLogs(query: String): Flow<List<LogEntity>> {
        return logDao.searchActiveLogsFlow(currentUserId, query)
    }

    override suspend fun getLogByUuid(uuid: String): LogEntity? {
        return logDao.getLogByUuid(uuid)
    }

    override suspend fun getLogWithConfigByUuid(uuid: String): com.example.dinoroar.data.local.LogWithConfig? {
        return logDao.getLogWithConfigByUuid(uuid)
    }

    override suspend fun insertLog(log: LogEntity, personUuids: List<String>) {
        val targetLog = if (log.userId.isBlank()) log.copy(userId = currentUserId) else log
        database.withTransaction {
            logDao.insertOrUpdate(targetLog)
            logPersonDao.deleteCrossRefsForLog(targetLog.uuid)
            val refs = personUuids.map { personUuid ->
                LogPersonCrossRef(logUuid = targetLog.uuid, personUuid = personUuid, userId = targetLog.userId)
            }
            logPersonDao.insertCrossRefs(refs)
        }
    }

    override suspend fun softDeleteLog(uuid: String) {
        logDao.softDeleteLog(uuid)
    }

    override fun getAttachmentsForLogFlow(logUuid: String): Flow<List<AttachmentEntity>> {
        return attachmentDao.getAttachmentsForLogFlow(logUuid)
    }

    override suspend fun getAttachmentsForLog(logUuid: String): List<AttachmentEntity> {
        return attachmentDao.getAttachmentsForLog(logUuid)
    }

    override suspend fun insertAttachment(attachment: AttachmentEntity) {
        val targetAttachment = if (attachment.userId.isBlank()) attachment.copy(userId = currentUserId) else attachment
        attachmentDao.insertOrUpdate(targetAttachment)
    }

    override suspend fun deleteAttachment(uuid: String) {
        attachmentDao.softDeleteAttachment(uuid)
    }

    override suspend fun softDeleteAttachment(uuid: String) {
        attachmentDao.softDeleteAttachment(uuid)
    }

    override suspend fun getAllSyncedAttachments(): List<AttachmentEntity> {
        return attachmentDao.getAllSyncedAttachments(currentUserId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val allPersons: Flow<List<PersonEntity>> = securePrefs.currentUserIdFlow.flatMapLatest { userId ->
        personDao.getAllActivePersonsFlow(userId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val deletedPersons: Flow<List<PersonEntity>> = securePrefs.currentUserIdFlow.flatMapLatest { userId ->
        personDao.getDeletedPersonsFlow(userId)
    }

    override suspend fun getAllActivePersons(): List<PersonEntity> {
        return personDao.getAllActivePersons(currentUserId)
    }

    override suspend fun getPersonsForLog(logUuid: String): List<PersonEntity> {
        return logPersonDao.getPersonsForLog(logUuid)
    }

    override fun getPersonsForLogFlow(logUuid: String): Flow<List<PersonEntity>> {
        return logPersonDao.getPersonsForLogFlow(logUuid)
    }

    override suspend fun insertPerson(person: PersonEntity) {
        val targetPerson = if (person.userId.isBlank()) person.copy(userId = currentUserId) else person
        personDao.insertOrUpdate(targetPerson)
    }

    override suspend fun softDeletePerson(uuid: String) {
        personDao.softDeletePerson(uuid)
    }

    override suspend fun getPersonByUuid(uuid: String): PersonEntity? {
        return personDao.getPersonByUuid(uuid)
    }

    override suspend fun getAllPersonsWithTemporary(): List<PersonEntity> {
        return personDao.getAllPersonsWithTemporary(currentUserId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val allCategories: Flow<List<PersonCategoryEntity>> = securePrefs.currentUserIdFlow.flatMapLatest { userId ->
        personDao.getAllCategoriesFlow(userId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val deletedCategories: Flow<List<PersonCategoryEntity>> = securePrefs.currentUserIdFlow.flatMapLatest { userId ->
        personDao.getDeletedCategoriesFlow(userId)
    }

    override suspend fun getAllCategories(): List<PersonCategoryEntity> {
        return personDao.getAllCategories(currentUserId)
    }

    override suspend fun getAllCategoriesIncludingDeleted(): List<PersonCategoryEntity> {
        return personDao.getAllCategoriesIncludingDeleted(currentUserId)
    }

    override suspend fun insertCategory(category: PersonCategoryEntity) {
        val targetCategory = if (category.userId.isBlank()) category.copy(userId = currentUserId) else category
        personDao.insertOrUpdateCategory(targetCategory)
    }

    override suspend fun deleteCategory(categoryUuid: String) {
        personDao.deleteCategoryAndUnbindPersons(categoryUuid)
    }

    override suspend fun updateCategoriesOrder(categories: List<PersonCategoryEntity>) {
        database.withTransaction {
            val updated = categories.mapIndexed { index, cat ->
                val targetCat = if (cat.userId.isBlank()) cat.copy(userId = currentUserId, sortOrder = index) else cat.copy(sortOrder = index)
                targetCat
            }
            personDao.insertOrUpdateCategories(updated)
        }
    }

    override suspend fun updatePersonsOrder(persons: List<PersonEntity>) {
        database.withTransaction {
            val updated = persons.mapIndexed { index, person ->
                val targetPerson = if (person.userId.isBlank()) person.copy(userId = currentUserId, sortOrder = index, isSynced = false) else person.copy(sortOrder = index, isSynced = false)
                targetPerson
            }
            personDao.insertOrUpdateAll(updated)
        }
    }

    override suspend fun getRecentPersons(): List<PersonEntity> {
        return personDao.getRecentPersons(currentUserId)
    }

    override fun getAllActiveDinoConfigsFlow(): Flow<List<DinoConfigEntity>> = dinoConfigDao.getAllActiveDinoConfigsFlow()

    override suspend fun getAllActiveDinoConfigs(): List<DinoConfigEntity> {
        return dinoConfigDao.getAllActiveDinoConfigs()
    }

    override suspend fun syncDinoConfig(dinoConfigs: List<DinoConfigEntity>) {
        database.withTransaction {
            dinoConfigDao.insertOrUpdateAll(dinoConfigs)
        }
    }

    override suspend fun clearAllData() {
        database.withTransaction {
            database.clearAllTables()
        }
    }

    override suspend fun getLogCanvasByUuid(logUuid: String): com.example.dinoroar.data.local.LogCanvasEntity? {
        return logCanvasDao.getLogCanvasByUuid(logUuid)
    }

    override fun getLogCanvasByUuidFlow(logUuid: String): Flow<com.example.dinoroar.data.local.LogCanvasEntity?> {
        return logCanvasDao.getLogCanvasByUuidFlow(logUuid)
    }

    override fun getCanvasImageUrlFlow(logUuid: String): Flow<String?> {
        return logCanvasDao.getCanvasImageUrlFlow(logUuid)
    }

    override suspend fun getCanvasInstanceById(instanceId: Int): com.example.dinoroar.data.local.CanvasInstanceEntity? {
        return canvasInstanceDao.getInstanceById(instanceId)
    }

    override suspend fun getLastUsedLogCanvas(): com.example.dinoroar.data.local.LogCanvasEntity? {
        return logCanvasDao.getLastUsedLogCanvas()
    }

    override suspend fun insertLogCanvas(logCanvas: com.example.dinoroar.data.local.LogCanvasEntity) {
        logCanvasDao.insertOrUpdate(logCanvas)
    }
}
