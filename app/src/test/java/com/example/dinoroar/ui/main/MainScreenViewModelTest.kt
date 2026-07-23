package com.example.dinoroar.ui.main

import com.example.dinoroar.data.DataRepository
import com.example.dinoroar.data.local.LogEntity
import com.example.dinoroar.data.local.AttachmentEntity
import com.example.dinoroar.data.local.PersonEntity
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MainScreenViewModelTest {
  @Test
  fun dummyTest() {
    assert(true)
  }
}

/*
private class FakeMyModelRepository : DataRepository {
    override val allLogs: Flow<List<LogEntity>> = flow { emit(emptyList()) }
    override val allPersons: Flow<List<PersonEntity>> = flow { emit(emptyList()) }
    
    override suspend fun getLogByUuid(uuid: String): LogEntity? = null
    override suspend fun insertLog(log: LogEntity, personUuids: List<String>) {}
    override suspend fun softDeleteLog(uuid: String) {}
    
    override fun getAttachmentsForLogFlow(logUuid: String): Flow<List<AttachmentEntity>> = flow { emit(emptyList()) }
    override suspend fun getAttachmentsForLog(logUuid: String): List<AttachmentEntity> = emptyList()
    override suspend fun insertAttachment(attachment: AttachmentEntity) {}
    override suspend fun deleteAttachment(uuid: String) {}
    override suspend fun softDeleteAttachment(uuid: String) {}

    override suspend fun getAllActivePersons(): List<PersonEntity> = emptyList()
    override suspend fun getPersonsForLog(logUuid: String): List<PersonEntity> = emptyList()
    override fun getPersonsForLogFlow(logUuid: String): Flow<List<PersonEntity>> = flow { emit(emptyList()) }
    override suspend fun insertPerson(person: PersonEntity) {}
    override suspend fun softDeletePerson(uuid: String) {}
}
*/
