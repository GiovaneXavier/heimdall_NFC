package br.com.corp.heimdall.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ValidateRequestDto(
    @SerialName("device_id")   val deviceId: String,
    @SerialName("employee_id") val employeeId: String,
    @SerialName("system_id")   val systemId: String,
    @SerialName("timestamp")   val timestamp: Long,
    @SerialName("nonce")       val nonce: String,
    @SerialName("hmac")        val hmac: String,
)
