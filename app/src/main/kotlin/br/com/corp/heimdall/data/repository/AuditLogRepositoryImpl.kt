package br.com.corp.heimdall.data.repository

import br.com.corp.heimdall.data.local.db.AccessLogDao
import br.com.corp.heimdall.data.local.db.AccessLogEntry
import br.com.corp.heimdall.domain.repository.AuditLogRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditLogRepositoryImpl @Inject constructor(
    private val dao: AccessLogDao,
) : AuditLogRepository {

    override suspend fun log(entry: AccessLogEntry) = dao.insert(entry)

    override fun observeRecent(limit: Int): Flow<List<AccessLogEntry>> =
        dao.observeRecent(limit)

    override suspend fun getEntriesSince(sinceMs: Long): List<AccessLogEntry> =
        dao.getEntriesSince(sinceMs)

    override suspend fun purgeOlderThan(beforeMs: Long) = dao.deleteOlderThan(beforeMs)
}
