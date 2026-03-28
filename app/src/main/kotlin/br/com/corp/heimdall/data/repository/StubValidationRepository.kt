package br.com.corp.heimdall.data.repository

import br.com.corp.heimdall.domain.model.DenialReason
import br.com.corp.heimdall.domain.model.Token
import br.com.corp.heimdall.domain.model.ValidationResult
import br.com.corp.heimdall.domain.repository.ValidationRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementação stub do [ValidationRepository].
 *
 * Retorna [DenialReason.SERVER_UNAVAILABLE] para qualquer token até que a
 * implementação real com Retrofit seja entregue no Sprint 4 (T-18).
 */
@Singleton
class StubValidationRepository @Inject constructor() : ValidationRepository {

    override suspend fun validate(token: Token.New): ValidationResult =
        ValidationResult.Denied(DenialReason.SERVER_UNAVAILABLE)

    override suspend fun validateLegacy(token: Token.Legacy): ValidationResult =
        ValidationResult.Denied(DenialReason.SERVER_UNAVAILABLE)
}
