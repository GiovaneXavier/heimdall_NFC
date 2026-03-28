package br.com.corp.heimdall.data.repository

import br.com.corp.heimdall.data.remote.api.HeimdallApi
import br.com.corp.heimdall.data.remote.api.LegacyApi
import br.com.corp.heimdall.data.remote.dto.LegacyValidateRequestDto
import br.com.corp.heimdall.data.remote.dto.ValidateRequestDto
import br.com.corp.heimdall.data.remote.dto.ValidateResponseDto
import br.com.corp.heimdall.domain.model.DenialReason
import br.com.corp.heimdall.domain.model.EmployeeInfo
import br.com.corp.heimdall.domain.model.Token
import br.com.corp.heimdall.domain.model.ValidationResult
import br.com.corp.heimdall.domain.repository.ValidationRepository
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ValidationRepositoryImpl @Inject constructor(
    private val heimdallApi: HeimdallApi,
    private val legacyApi: LegacyApi,
) : ValidationRepository {

    override suspend fun validate(token: Token.New): ValidationResult {
        val response = heimdallApi.validate(
            ValidateRequestDto(
                deviceId = token.deviceId,
                employeeId = token.employeeId,
                systemId = token.systemId,
                timestamp = token.timestamp,
                nonce = token.nonce,
                hmac = token.hmac,
            )
        )
        return response.toValidationResult()
    }

    override suspend fun validateLegacy(token: Token.Legacy): ValidationResult {
        val response = legacyApi.validateLegacy(
            LegacyValidateRequestDto(prefix = token.prefix, code = token.code)
        )
        return response.toValidationResult()
    }

    // ── Mapeamento HTTP → domínio ─────────────────────────────────────────────

    private fun Response<ValidateResponseDto>.toValidationResult(): ValidationResult {
        if (!isSuccessful) return ValidationResult.Denied(httpCodeToDenialReason(code()))

        val body = body() ?: return ValidationResult.Denied(DenialReason.SERVER_UNAVAILABLE)

        return if (body.approved) {
            val emp = body.employee
                ?: return ValidationResult.Denied(DenialReason.SERVER_UNAVAILABLE)
            ValidationResult.Approved(
                EmployeeInfo(id = emp.id, name = emp.name, photoUrl = emp.photoUrl)
            )
        } else {
            val reason = body.denialReason
                ?.let { runCatching { DenialReason.valueOf(it) }.getOrNull() }
                ?: DenialReason.SERVER_UNAVAILABLE
            ValidationResult.Denied(reason)
        }
    }

    private fun httpCodeToDenialReason(code: Int): DenialReason = when (code) {
        401, 403 -> DenialReason.DEVICE_NOT_WHITELISTED
        404      -> DenialReason.EMPLOYEE_NOT_FOUND
        409      -> DenialReason.NONCE_REPLAY
        else     -> DenialReason.SERVER_UNAVAILABLE
    }
}
