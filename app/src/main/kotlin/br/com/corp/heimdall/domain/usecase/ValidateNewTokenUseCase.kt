package br.com.corp.heimdall.domain.usecase

import br.com.corp.heimdall.BuildConfig
import br.com.corp.heimdall.core.crypto.HmacValidator
import br.com.corp.heimdall.core.util.TimeProvider
import br.com.corp.heimdall.domain.model.DenialReason
import br.com.corp.heimdall.domain.model.Token
import br.com.corp.heimdall.domain.model.ValidationResult
import br.com.corp.heimdall.di.ConfiguredSystemId
import br.com.corp.heimdall.domain.repository.ValidationRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executa o pipeline completo de validação de um [Token.New] em 4 etapas ordenadas.
 *
 * **Etapas locais (sem rede):**
 * 1. HMAC-SHA256 — verifica autenticidade da assinatura
 * 2. Janela temporal — rejeita tokens com `|timestamp - agora| > 30s`
 * 3. systemId — rejeita tokens de outro sistema de controle de acesso
 *
 * **Etapa remota:**
 * 4. Backend — whitelist de dispositivos, anti-replay de nonces, existência do funcionário
 *
 * Qualquer etapa que falhe interrompe o pipeline imediatamente.
 *
 * @property hmacValidator   Validador de assinatura HMAC.
 * @property timeProvider    Fonte do instante atual (mockável em testes).
 * @property repository      Gateway para validação remota.
 * @property configuredSystemId systemId configurado neste ponto de acesso.
 */
@Singleton
class ValidateNewTokenUseCase @Inject constructor(
    private val hmacValidator: HmacValidator,
    private val timeProvider: TimeProvider,
    private val repository: ValidationRepository,
    @ConfiguredSystemId private val configuredSystemId: String,
) {

    companion object {
        /** Janela temporal máxima em segundos (±30 s). */
        const val TIMESTAMP_WINDOW_SECONDS = 30L
    }

    /**
     * Valida [token] através do pipeline de 4 etapas.
     *
     * @return [ValidationResult.Approved] ou [ValidationResult.Denied] com o motivo.
     */
    suspend operator fun invoke(token: Token.New): ValidationResult {
        // Etapa 1 — HMAC
        val payload = "${token.deviceId}|${token.employeeId}|${token.systemId}|${token.timestamp}|${token.nonce}"
        if (!hmacValidator.verify(BuildConfig.TOKEN_HMAC_KEY, payload, token.hmac)) {
            return ValidationResult.Denied(DenialReason.INVALID_HMAC)
        }

        // Etapa 2 — Janela temporal
        val delta = token.timestamp - timeProvider.nowSeconds()
        when {
            delta < -TIMESTAMP_WINDOW_SECONDS -> return ValidationResult.Denied(DenialReason.EXPIRED)
            delta > TIMESTAMP_WINDOW_SECONDS  -> return ValidationResult.Denied(DenialReason.FUTURE_TOKEN)
        }

        // Etapa 3 — systemId
        if (token.systemId != configuredSystemId) {
            return ValidationResult.Denied(DenialReason.WRONG_SYSTEM)
        }

        // Etapa 4 — Backend
        return try {
            repository.validate(token)
        } catch (_: Exception) {
            ValidationResult.Denied(DenialReason.SERVER_UNAVAILABLE)
        }
    }
}
