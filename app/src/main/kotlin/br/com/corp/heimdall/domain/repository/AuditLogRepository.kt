package br.com.corp.heimdall.domain.repository

import br.com.corp.heimdall.data.local.db.AccessLogEntry
import kotlinx.coroutines.flow.Flow

interface AuditLogRepository {

    /** Grava um registro de acesso. Não-suspending na interface — impl usa suspend. */
    suspend fun log(entry: AccessLogEntry)

    /** Observa os últimos [limit] registros em tempo real. */
    fun observeRecent(limit: Int = 100): Flow<List<AccessLogEntry>>

    /** Retorna registros a partir de [sinceMs] (para sincronização com backend). */
    suspend fun getEntriesSince(sinceMs: Long): List<br.com.corp.heimdall.data.local.db.AccessLogEntry>

    /** Remove registros anteriores a [beforeMs] (limpeza periódica). */
    suspend fun purgeOlderThan(beforeMs: Long)
}
