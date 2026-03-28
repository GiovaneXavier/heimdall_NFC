package br.com.corp.heimdall.domain.repository

import br.com.corp.heimdall.domain.model.Token
import br.com.corp.heimdall.domain.model.ValidationResult

/**
 * Contrato para validação remota de tokens.
 *
 * A implementação real ([ValidationRepositoryImpl]) faz chamada ao backend REST.
 * Nos testes, é substituída por um mock.
 */
interface ValidationRepository {

    /**
     * Valida o token novo contra o backend (whitelist de dispositivos, anti-replay de nonces,
     * existência do funcionário).
     *
     * @param token Token já parseado e com etapas locais aprovadas.
     * @return [ValidationResult.Approved] ou [ValidationResult.Denied].
     * @throws Exception Em caso de timeout ou erro de rede (o use case mapeia para
     *                   [br.com.corp.heimdall.domain.model.DenialReason.SERVER_UNAVAILABLE]).
     */
    suspend fun validate(token: Token.New): ValidationResult

    /**
     * Valida token legado via API legada.
     *
     * @param token Token legado a validar.
     * @return [ValidationResult.Approved] ou [ValidationResult.Denied].
     */
    suspend fun validateLegacy(token: Token.Legacy): ValidationResult
}
