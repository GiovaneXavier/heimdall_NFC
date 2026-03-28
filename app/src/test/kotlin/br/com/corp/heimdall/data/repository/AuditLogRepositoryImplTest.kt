package br.com.corp.heimdall.data.repository

import br.com.corp.heimdall.data.local.db.AccessLogDao
import br.com.corp.heimdall.data.local.db.AccessLogEntry
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AuditLogRepositoryImplTest {

    private val dao: AccessLogDao = mockk(relaxed = true)
    private lateinit var repository: AuditLogRepositoryImpl

    private val sampleEntry = AccessLogEntry(
        timestampMs = 1711500000_000L,
        employeeId = "SRBR-001",
        employeeName = "Ana Lima",
        deviceId = "dev-1",
        channel = "NFC",
        result = "APPROVED",
        denialReason = "",
    )

    @Before
    fun setup() {
        repository = AuditLogRepositoryImpl(dao)
    }

    @Test
    fun `log delega para dao insert`() = runTest {
        repository.log(sampleEntry)
        coVerify(exactly = 1) { dao.insert(sampleEntry) }
    }

    @Test
    fun `observeRecent retorna flow do dao`() = runTest {
        val entries = listOf(sampleEntry)
        every { dao.observeRecent(100) } returns flowOf(entries)

        val result = repository.observeRecent(100).first()

        assertEquals(entries, result)
    }

    @Test
    fun `purgeOlderThan delega para dao deleteOlderThan`() = runTest {
        val threshold = 1711500000_000L
        repository.purgeOlderThan(threshold)
        coVerify(exactly = 1) { dao.deleteOlderThan(threshold) }
    }
}
