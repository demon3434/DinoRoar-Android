package com.example.dinoroar.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT * FROM logs WHERE (userId = :userId OR userId = '') AND isDeleted = 0 ORDER BY incidentDate DESC")
    fun getAllActiveLogsFlow(userId: String): Flow<List<LogEntity>>

    @androidx.room.Transaction
    @Query("SELECT * FROM logs WHERE (userId = :userId OR userId = '') AND isDeleted = 0 ORDER BY incidentDate DESC")
    fun getAllActiveLogsWithConfigFlow(userId: String): Flow<List<LogWithConfig>>

    @androidx.room.Transaction
    @Query("SELECT * FROM logs WHERE uuid = :uuid LIMIT 1")
    suspend fun getLogWithConfigByUuid(uuid: String): LogWithConfig?

    @Query("SELECT * FROM logs WHERE (userId = :userId OR userId = '') AND isDeleted = 0 AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') ORDER BY incidentDate DESC")
    fun searchActiveLogsFlow(userId: String, query: String): Flow<List<LogEntity>>

    @Query("SELECT * FROM logs WHERE (userId = :userId OR userId = '') AND isDeleted = 0 ORDER BY incidentDate DESC")
    suspend fun getAllActiveLogs(userId: String): List<LogEntity>

    @Query("SELECT * FROM logs WHERE uuid = :uuid LIMIT 1")
    suspend fun getLogByUuid(uuid: String): LogEntity?

    @Query("SELECT * FROM logs WHERE (userId = :userId OR userId = '') AND isSynced = 0")
    suspend fun getUnsyncedLogs(userId: String): List<LogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(log: LogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(logs: List<LogEntity>)

    @Query("UPDATE logs SET isDeleted = 1, isSynced = 0 WHERE uuid = :uuid")
    suspend fun softDeleteLog(uuid: String)

    @Query("DELETE FROM logs WHERE uuid = :uuid")
    suspend fun hardDeleteLog(uuid: String)

    @Query("DELETE FROM logs WHERE uuid IN (:uuids)")
    suspend fun hardDeleteLogs(uuids: List<String>)

    @Query("UPDATE logs SET isSynced = 1, isLocalOnly = 0 WHERE uuid = :uuid")
    suspend fun markSynced(uuid: String)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE (userId = :userId OR userId = '') AND isDeleted = 0")
    fun getAllActiveAttachmentsFlow(userId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE logUuid = :logUuid AND isDeleted = 0")
    fun getAttachmentsForLogFlow(logUuid: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE logUuid = :logUuid AND isDeleted = 0")
    suspend fun getAttachmentsForLog(logUuid: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE uuid = :uuid LIMIT 1")
    suspend fun getAttachmentByUuid(uuid: String): AttachmentEntity?

    @Query("SELECT * FROM attachments WHERE (userId = :userId OR userId = '') AND isSynced = 0 AND isDeleted = 0")
    suspend fun getUnsyncedAttachments(userId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE (userId = :userId OR userId = '') AND isSynced = 1 AND isDeleted = 0")
    suspend fun getAllSyncedAttachments(userId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE (userId = :userId OR userId = '') AND isDeleted = 1 AND isSynced = 0")
    suspend fun getPendingDeleteAttachments(userId: String): List<AttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(attachment: AttachmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(attachments: List<AttachmentEntity>)

    @Query("UPDATE attachments SET isDeleted = 1, isSynced = 0 WHERE uuid = :uuid")
    suspend fun softDeleteAttachment(uuid: String)

    @Query("UPDATE attachments SET isDeleted = 1, isSynced = 0 WHERE logUuid = :logUuid")
    suspend fun softDeleteAttachmentsForLog(logUuid: String)

    @Query("DELETE FROM attachments WHERE uuid = :uuid")
    suspend fun hardDeleteAttachment(uuid: String)

    @Query("DELETE FROM attachments WHERE uuid = :uuid")
    suspend fun deleteAttachment(uuid: String)

    @Query("DELETE FROM attachments WHERE logUuid = :logUuid")
    suspend fun deleteAttachmentsForLog(logUuid: String)

    @Query("UPDATE attachments SET isSynced = 1 WHERE uuid = :uuid")
    suspend fun markSynced(uuid: String)
}

@Dao
interface PersonDao {
    @Query("SELECT * FROM persons WHERE (userId = :userId OR userId = '') AND isDeleted = 0 AND isTemporary = 0 ORDER BY sortOrder ASC, name ASC")
    fun getAllActivePersonsFlow(userId: String): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons WHERE (userId = :userId OR userId = '') AND isDeleted = 0 AND isTemporary = 0 ORDER BY sortOrder ASC, name ASC")
    suspend fun getAllActivePersons(userId: String): List<PersonEntity>

    @Query("SELECT * FROM persons WHERE (userId = :userId OR userId = '') AND isDeleted = 0 ORDER BY sortOrder ASC, name ASC")
    suspend fun getAllPersonsWithTemporary(userId: String): List<PersonEntity>

    @Query("SELECT * FROM persons WHERE uuid = :uuid LIMIT 1")
    suspend fun getPersonByUuid(uuid: String): PersonEntity?

    @Query("SELECT * FROM persons WHERE (userId = :userId OR userId = '') AND isSynced = 0")
    suspend fun getUnsyncedPersons(userId: String): List<PersonEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(person: PersonEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(persons: List<PersonEntity>)

    @Query("UPDATE persons SET isDeleted = 1, isSynced = 0 WHERE uuid = :uuid")
    suspend fun softDeletePerson(uuid: String)

    @Query("DELETE FROM persons WHERE uuid = :uuid")
    suspend fun hardDeletePerson(uuid: String)

    @Query("DELETE FROM persons WHERE uuid IN (:uuids)")
    suspend fun hardDeletePersons(uuids: List<String>)

    @Query("UPDATE persons SET isSynced = 1 WHERE uuid = :uuid")
    suspend fun markSynced(uuid: String)

    @Query("SELECT * FROM persons WHERE (userId = :userId OR userId = '') AND isDeleted = 1 AND isTemporary = 0 ORDER BY name ASC")
    fun getDeletedPersonsFlow(userId: String): Flow<List<PersonEntity>>

    @Query("SELECT * FROM person_categories WHERE (userId = :userId OR userId = '') AND isDeleted = 1 ORDER BY sortOrder ASC")
    fun getDeletedCategoriesFlow(userId: String): Flow<List<PersonCategoryEntity>>

    // === Category Operations ===
    @Query("SELECT * FROM person_categories WHERE (userId = :userId OR userId = '') AND isDeleted = 0 ORDER BY sortOrder ASC")
    fun getAllCategoriesFlow(userId: String): Flow<List<PersonCategoryEntity>>

    @Query("SELECT * FROM person_categories WHERE (userId = :userId OR userId = '') AND isDeleted = 0 ORDER BY sortOrder ASC")
    suspend fun getAllCategories(userId: String): List<PersonCategoryEntity>

    @Query("SELECT * FROM person_categories WHERE (userId = :userId OR userId = '')")
    suspend fun getAllCategoriesIncludingDeleted(userId: String): List<PersonCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCategory(category: PersonCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCategories(categories: List<PersonCategoryEntity>)

    @Query("UPDATE person_categories SET isDeleted = 1 WHERE uuid = :uuid")
    suspend fun softDeleteCategory(uuid: String)

    @Query("UPDATE persons SET categoryUuid = NULL, isSynced = 0 WHERE categoryUuid = :categoryUuid")
    suspend fun removeCategoryFromPersons(categoryUuid: String)

    @Transaction
    suspend fun deleteCategoryAndUnbindPersons(categoryUuid: String) {
        softDeleteCategory(categoryUuid)
        removeCategoryFromPersons(categoryUuid)
    }

    // === Recent Persons (5) Query ===
    @Query("""
        SELECT p.* FROM persons p
        INNER JOIN log_person_cross_ref ref ON p.uuid = ref.personUuid
        INNER JOIN logs l ON ref.logUuid = l.uuid
        WHERE (p.userId = :userId OR p.userId = '') AND p.isDeleted = 0 AND p.isTemporary = 0 AND l.isDeleted = 0
        GROUP BY p.uuid
        ORDER BY MAX(l.createdAt) DESC
        LIMIT 5
    """)
    suspend fun getRecentPersons(userId: String): List<PersonEntity>
}

@Dao
interface LogPersonDao {
    @Query("SELECT * FROM log_person_cross_ref WHERE userId = :userId OR userId = ''")
    fun getAllCrossRefsFlow(userId: String): Flow<List<LogPersonCrossRef>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRef(crossRef: LogPersonCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(crossRefs: List<LogPersonCrossRef>)

    @Query("DELETE FROM log_person_cross_ref WHERE logUuid = :logUuid")
    suspend fun deleteCrossRefsForLog(logUuid: String)

    @Query("DELETE FROM log_person_cross_ref WHERE personUuid = :personUuid")
    suspend fun deleteCrossRefsForPerson(personUuid: String)

    @Query("""
        SELECT p.* FROM persons p
        INNER JOIN log_person_cross_ref ref ON p.uuid = ref.personUuid
        WHERE ref.logUuid = :logUuid
    """)
    suspend fun getPersonsForLog(logUuid: String): List<PersonEntity>

    @Query("""
        SELECT p.* FROM persons p
        INNER JOIN log_person_cross_ref ref ON p.uuid = ref.personUuid
        WHERE ref.logUuid = :logUuid
    """)
    fun getPersonsForLogFlow(logUuid: String): Flow<List<PersonEntity>>
}

@Dao
interface DinoConfigDao {
    @Query("SELECT * FROM dino_config WHERE isActive = 1 ORDER BY sortOrder ASC")
    fun getAllActiveDinoConfigsFlow(): Flow<List<DinoConfigEntity>>

    @Query("SELECT * FROM dino_config WHERE isActive = 1 ORDER BY sortOrder ASC")
    suspend fun getAllActiveDinoConfigs(): List<DinoConfigEntity>

    @Query("SELECT * FROM dino_config")
    suspend fun getAllDinoConfigs(): List<DinoConfigEntity>

    @Query("SELECT * FROM dino_config WHERE id = :id LIMIT 1")
    suspend fun getDinoConfigById(id: Int): DinoConfigEntity?

    @Query("SELECT * FROM dino_config WHERE legacyKey = :legacyKey LIMIT 1")
    suspend fun getDinoConfigByLegacyKey(legacyKey: String): DinoConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(configs: List<DinoConfigEntity>)

    @Query("DELETE FROM dino_config")
    suspend fun deleteAll()
}

@Dao
interface StickerDao {
    @Query("SELECT * FROM stickers WHERE isDeleted = 0 AND isActive = 1 ORDER BY sortOrder ASC")
    fun getAllActiveStickersFlow(): Flow<List<StickerEntity>>

    @Query("SELECT * FROM stickers WHERE isDeleted = 0 AND isActive = 1 ORDER BY sortOrder ASC")
    suspend fun getAllActiveStickers(): List<StickerEntity>

    @Query("SELECT * FROM stickers WHERE id = :id LIMIT 1")
    suspend fun getStickerById(id: Int): StickerEntity?

    @Query("SELECT * FROM stickers WHERE id IN (:ids)")
    suspend fun getStickersByIds(ids: List<Int>): List<StickerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(stickers: List<StickerEntity>)

    @Query("DELETE FROM stickers")
    suspend fun deleteAll()
}

@Dao
interface StickerSeriesDao {
    @Query("SELECT * FROM sticker_series WHERE isDeleted = 0 AND isActive = 1 ORDER BY sortOrder ASC")
    fun getAllActiveSeriesFlow(): Flow<List<StickerSeriesEntity>>

    @Query("SELECT * FROM sticker_series WHERE isDeleted = 0 AND isActive = 1 ORDER BY sortOrder ASC")
    suspend fun getAllActiveSeries(): List<StickerSeriesEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(seriesList: List<StickerSeriesEntity>)

    @Query("DELETE FROM sticker_series")
    suspend fun deleteAll()
}
