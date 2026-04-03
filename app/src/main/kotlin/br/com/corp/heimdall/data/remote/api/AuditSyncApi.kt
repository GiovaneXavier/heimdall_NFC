package br.com.corp.heimdall.data.remote.api

import br.com.corp.heimdall.data.remote.dto.AuditSyncRequestDto
import br.com.corp.heimdall.data.remote.dto.AuditSyncResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuditSyncApi {

    /**
     * Envia registros de auditoria pendentes para o backend.
     *
     * POST /api/v1/audit-sync
     *
     * O backend confirma o recebimento via [AuditSyncResponseDto.received].
     * Em caso de falha (4xx/5xx), o worker tenta novamente no próximo ciclo.
     */
    @POST("api/v1/audit-sync")
    suspend fun sync(@Body request: AuditSyncRequestDto): Response<AuditSyncResponseDto>
}
