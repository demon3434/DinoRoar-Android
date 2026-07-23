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
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
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
}

@Singleton
class DefaultDataRepository @Inject constructor(
    private val database: DinoDatabase,
    private val logDao: LogDao,
    private val attachmentDao: AttachmentDao,
    private val personDao: PersonDao,
    private val logPersonDao: LogPersonDao,
    private val dinoConfigDao: DinoConfigDao
) : DataRepository {

    override val allLogs: Flow<List<LogEntity>> = logDao.getAllActiveLogsFlow()
    override val allLogsWithConfig: Flow<List<com.example.dinoroar.data.local.LogWithConfig>> = logDao.getAllActiveLogsWithConfigFlow()
    override val allAttachmentsFlow: Flow<List<AttachmentEntity>> = attachmentDao.getAllActiveAttachmentsFlow()
    override val allCrossRefsFlow: Flow<List<LogPersonCrossRef>> = logPersonDao.getAllCrossRefsFlow()

    override fun searchLogs(query: String): Flow<List<LogEntity>> {
        return logDao.searchActiveLogsFlow(query)
    }

    override suspend fun getLogByUuid(uuid: String): LogEntity? {
        return logDao.getLogByUuid(uuid)
    }

    override suspend fun getLogWithConfigByUuid(uuid: String): com.example.dinoroar.data.local.LogWithConfig? {
        return logDao.getLogWithConfigByUuid(uuid)
    }

    override suspend fun insertLog(log: LogEntity, personUuids: List<String>) {
        database.withTransaction {
            logDao.insertOrUpdate(log)
            logPersonDao.deleteCrossRefsForLog(log.uuid)
            val refs = personUuids.map { personUuid ->
                LogPersonCrossRef(logUuid = log.uuid, personUuid = personUuid)
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
        attachmentDao.insertOrUpdate(attachment)
    }

    override suspend fun deleteAttachment(uuid: String) {
        attachmentDao.softDeleteAttachment(uuid)
    }

    override suspend fun softDeleteAttachment(uuid: String) {
        attachmentDao.softDeleteAttachment(uuid)
    }

    override suspend fun getAllSyncedAttachments(): List<AttachmentEntity> {
        return attachmentDao.getAllSyncedAttachments()
    }

    override val allPersons: Flow<List<PersonEntity>> = personDao.getAllActivePersonsFlow()
    override val deletedPersons: Flow<List<PersonEntity>> = personDao.getDeletedPersonsFlow()

    override suspend fun getAllActivePersons(): List<PersonEntity> {
        return personDao.getAllActivePersons()
    }

    override suspend fun getPersonsForLog(logUuid: String): List<PersonEntity> {
        return logPersonDao.getPersonsForLog(logUuid)
    }

    override fun getPersonsForLogFlow(logUuid: String): Flow<List<PersonEntity>> {
        return logPersonDao.getPersonsForLogFlow(logUuid)
    }

    override suspend fun insertPerson(person: PersonEntity) {
        personDao.insertOrUpdate(person)
    }

    override suspend fun softDeletePerson(uuid: String) {
        personDao.softDeletePerson(uuid)
    }

    override suspend fun getPersonByUuid(uuid: String): PersonEntity? {
        return personDao.getPersonByUuid(uuid)
    }

    override suspend fun getAllPersonsWithTemporary(): List<PersonEntity> {
        return personDao.getAllPersonsWithTemporary()
    }

    override val allCategories: Flow<List<PersonCategoryEntity>> = personDao.getAllCategoriesFlow()
    override val deletedCategories: Flow<List<PersonCategoryEntity>> = personDao.getDeletedCategoriesFlow()

    override suspend fun getAllCategories(): List<PersonCategoryEntity> {
        return personDao.getAllCategories()
    }

    override suspend fun getAllCategoriesIncludingDeleted(): List<PersonCategoryEntity> {
        return personDao.getAllCategoriesIncludingDeleted()
    }

    override suspend fun insertCategory(category: PersonCategoryEntity) {
        personDao.insertOrUpdateCategory(category)
    }

    override suspend fun deleteCategory(categoryUuid: String) {
        personDao.deleteCategoryAndUnbindPersons(categoryUuid)
    }

    override suspend fun updateCategoriesOrder(categories: List<PersonCategoryEntity>) {
        database.withTransaction {
            val updated = categories.mapIndexed { index, cat ->
                cat.copy(sortOrder = index)
            }
            personDao.insertOrUpdateCategories(updated)
        }
    }

    override suspend fun updatePersonsOrder(persons: List<PersonEntity>) {
        database.withTransaction {
            val updated = persons.mapIndexed { index, person ->
                person.copy(sortOrder = index, isSynced = false)
            }
            personDao.insertOrUpdateAll(updated)
        }
    }

    override suspend fun getRecentPersons(): List<PersonEntity> {
        return personDao.getRecentPersons()
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
}
