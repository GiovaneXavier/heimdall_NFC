package br.com.corp.heimdall.domain.model

/**
 * Representa um token lido pelo Heimdall, discriminado pelo formato.
 *
 * - [New]: token Huginn (formato `deviceId|employeeId|systemId|timestamp|nonce.hmac`)
 * - [Legacy]: token legado corporativo (`CORP.XXXX` ou `PART.XXXX`)
 * - [Invalid]: string que não corresponde a nenhum formato conhecido
 */
sealed class Token {

    /**
     * Token emitido pelo app Huginn.
     *
     * @property deviceId   Identificador do dispositivo emissor.
     * @property employeeId Matrícula do funcionário.
     * @property systemId   Identificador do sistema de controle de acesso.
     * @property timestamp  Momento de emissão em segundos Unix.
     * @property nonce      Valor aleatório único para proteção contra replay.
     * @property hmac       Assinatura HMAC-SHA256 em Base64url sem padding.
     */
    data class New(
        val deviceId: String,
        val employeeId: String,
        val systemId: String,
        val timestamp: Long,
        val nonce: String,
        val hmac: String,
    ) : Token()

    /**
     * Token legado no formato `CORP.XXXX` ou `PART.XXXX`.
     *
     * @property prefix Prefixo do tipo (`CORP` ou `PART`).
     * @property code   Código numérico do funcionário.
     */
    data class Legacy(
        val prefix: String,
        val code: String,
    ) : Token()

    /** Token com formato não reconhecido ou estruturalmente inválido. */
    data object Invalid : Token()
}
