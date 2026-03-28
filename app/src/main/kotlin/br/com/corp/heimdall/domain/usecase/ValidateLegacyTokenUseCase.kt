package br.com.corp.heimdall.domain.usecase

import br.com.corp.heimdall.domain.model.DenialReason
import br.com.corp.heimdall.domain.model.Token
import br.com.corp.heimdall.domain.model.ValidationResult
import br.com.corp.heimdall.domain.repository.ValidationRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Valida um [Token.Legacy] contra a API legada corporativa.
 *
 * Delega inteiramente ao [ValidationRepository.validateLegacy]. Qualquer exceção
 * de rede é capturada e mapeada para [DenialReason.SERVER_UNAVAILABLE].
 *
 * @property repository Gateway para a API legada.
 */
@Singleton
class ValidateLegacyTokenUseCase @Inject constructor(
    private val repository: ValidationRepository,
) {

    /**
     * Valida [token] via API legada.
     *
     * @return [ValidationResult.Approved] ou [ValidationResult.Denied].
     */
    suspend operator fun invoke(token: Token.Legacy): ValidationResult {
        return try {
            repository.validateLegacy(token)
        } catch (_: Exception) {
            ValidationResult.Denied(DenialReason.SERVER_UNAVAILABLE)
        }
    }
}
