package br.com.corp.heimdall.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import br.com.corp.heimdall.data.local.preferences.ConfigPreferences
import br.com.corp.heimdall.data.remote.api.AuditSyncApi
import br.com.corp.heimdall.data.remote.dto.AuditEntryDto
import br.com.corp.heimdall.data.remote.dto.AuditSyncRequestDto
import br.com.corp.heimdall.domain.repository.AuditLogRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Worker que sincroniza os registros de auditoria pendentes com o backend.
 *
 * Executa periodicamente via WorkManager (a cada 15 minutos).
 * Usa a última data de sync (armazenada em [ConfigPreferences]) para enviar
 * somente registros novos. Em caso de falha de rede ou HTTP, retorna [Result.retry()].
 */
@HiltWorker
class AuditSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val auditLogRepository: AuditLogRepository,
    private val auditSyncApi: AuditSyncApi,
    private val config: ConfigPreferences,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val sinceMs = config.lastAuditSyncMs
            val entries = auditLogRepository.getEntriesSince(sinceMs)
            if (entries.isEmpty()) return Result.success()

            val request = AuditSyncRequestDto(
                deviceId = config.systemId,
                entries = entries.map {
                    AuditEntryDto(
                        id = it.id,
                        timestampMs = it.timestampMs,
                        employeeId = it.employeeId,
                        employeeName = it.employeeName,
                        channel = it.channel,
                        result = it.result,
                        denialReason = it.denialReason,
                    )
                },
            )

            val response = auditSyncApi.sync(request)
            if (response.isSuccessful) {
                config.lastAuditSyncMs = System.currentTimeMillis()
                Result.success()
            } else {
                Result.retry()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "heimdall_audit_sync"
    }
}
