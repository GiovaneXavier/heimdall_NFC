package br.com.corp.heimdall.core.util

/**
 * Abstração sobre o relógio do sistema.
 *
 * Permite substituição por um fake nos testes unitários, eliminando dependência
 * de [System.currentTimeMillis] nas camadas de domínio.
 */
interface TimeProvider {
    /** Retorna o instante atual em segundos Unix. */
    fun nowSeconds(): Long
}

/** Implementação de produção que delega ao relógio do sistema. */
class SystemTimeProvider : TimeProvider {
    override fun nowSeconds(): Long = System.currentTimeMillis() / 1000L
}
