package br.com.corp.heimdall.domain.model

/**
 * Resultado do pipeline de validação de um token.
 *
 * - [Approved]: acesso concedido, com dados do funcionário para exibição.
 * - [Denied]: acesso negado, com motivo específico para mensagem de erro.
 */
sealed class ValidationResult {

    /** Acesso concedido. Contém as informações do funcionário para a tela de resultado. */
    data class Approved(val employee: EmployeeInfo) : ValidationResult()

    /** Acesso negado com o motivo detalhado. */
    data class Denied(val reason: DenialReason) : ValidationResult()
}

/**
 * Motivos possíveis de negação, mapeados a mensagens de erro na UI.
 *
 * Mapeamento UI:
 * - [INVALID_HMAC]           → "Assinatura inválida"
 * - [EXPIRED]                → "Token expirado"
 * - [FUTURE_TOKEN]           → "Token do futuro"
 * - [WRONG_SYSTEM]           → "Sistema incorreto"
 * - [DEVICE_NOT_WHITELISTED] → "Dispositivo não autorizado"
 * - [NONCE_REPLAY]           → "Token já utilizado"
 * - [EMPLOYEE_NOT_FOUND]     → "Funcionário não encontrado"
 * - [SERVER_UNAVAILABLE]     → "Servidor indisponível"
 * - [INVALID_FORMAT]         → "Formato inválido"
 * - [RATE_LIMITED]           → "Muitas tentativas. Aguarde."
 */
enum class DenialReason {
    INVALID_HMAC,
    EXPIRED,
    FUTURE_TOKEN,
    WRONG_SYSTEM,
    DEVICE_NOT_WHITELISTED,
    NONCE_REPLAY,
    EMPLOYEE_NOT_FOUND,
    SERVER_UNAVAILABLE,
    INVALID_FORMAT,
    RATE_LIMITED,
}
