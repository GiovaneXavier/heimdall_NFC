package br.com.corp.heimdall.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LegacyValidateRequestDto(
    @SerialName("prefix") val prefix: String,
    @SerialName("code")   val code: String,
)
