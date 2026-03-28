package br.com.corp.heimdall.domain.model

/**
 * Informações do funcionário retornadas após validação bem-sucedida.
 *
 * @property id       Matrícula do funcionário.
 * @property name     Nome completo para exibição na tela de resultado.
 * @property photoUrl URL da foto de perfil (pode ser vazio se não cadastrada).
 */
data class EmployeeInfo(
    val id: String,
    val name: String,
    val photoUrl: String,
)
