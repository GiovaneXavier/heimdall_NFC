package br.com.corp.heimdall.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ValidateResponseDto(
    @SerialName("approved")      val approved: Boolean,
    @SerialName("employee")      val employee: EmployeeDto? = null,
    @SerialName("denial_reason") val denialReason: String? = null,
)

@Serializable
data class EmployeeDto(
    @SerialName("id")        val id: String,
    @SerialName("name")      val name: String,
    @SerialName("photo_url") val photoUrl: String = "",
)
