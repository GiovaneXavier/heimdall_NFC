package br.com.corp.heimdall.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuditSyncRequestDto(
    @SerialName("device_id") val deviceId: String,
    @SerialName("entries")   val entries: List<AuditEntryDto>,
)

@Serializable
data class AuditEntryDto(
    @SerialName("id")            val id: Long,
    @SerialName("timestamp_ms")  val timestampMs: Long,
    @SerialName("employee_id")   val employeeId: String,
    @SerialName("employee_name") val employeeName: String,
    @SerialName("channel")       val channel: String,
    @SerialName("result")        val result: String,
    @SerialName("denial_reason") val denialReason: String,
)

@Serializable
data class AuditSyncResponseDto(
    @SerialName("received") val received: Int,
)
