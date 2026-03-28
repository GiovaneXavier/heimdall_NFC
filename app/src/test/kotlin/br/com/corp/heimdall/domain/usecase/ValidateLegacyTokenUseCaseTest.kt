package br.com.corp.heimdall.domain.usecase

import br.com.corp.heimdall.domain.model.DenialReason
import br.com.corp.heimdall.domain.model.EmployeeInfo
import br.com.corp.heimdall.domain.model.Token
import br.com.corp.heimdall.domain.model.ValidationResult
import br.com.corp.heimdall.domain.repository.ValidationRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ValidateLegacyTokenUseCaseTest {

    private val repository: ValidationRepository = mockk()
    private lateinit var useCase: ValidateLegacyTokenUseCase

    private val legacyToken = Token.Legacy("CORP", "1234")
    private val employee = EmployeeInfo("CORP-1234", "Carlos Souza", "https://legacy.corp.br/fotos/1234.jpg")

    @Before
    fun setup() {
        useCase = ValidateLegacyTokenUseCase(repository)
    }

    @Test
    fun `repositorio retorna Approved propaga Approved`() = runTest {
        coEvery { repository.validateLegacy(legacyToken) } returns ValidationResult.Approved(employee)

        val result = useCase(legacyToken)

        assertEquals(ValidationResult.Approved(employee), result)
    }

    @Test
    fun `repositorio retorna Denied propaga Denied`() = runTest {
        coEvery { repository.validateLegacy(legacyToken) } returns
            ValidationResult.Denied(DenialReason.EMPLOYEE_NOT_FOUND)

        val result = useCase(legacyToken)

        assertEquals(ValidationResult.Denied(DenialReason.EMPLOYEE_NOT_FOUND), result)
    }

    @Test
    fun `repositorio lanca excecao retorna Denied SERVER_UNAVAILABLE`() = runTest {
        coEvery { repository.validateLegacy(legacyToken) } throws
            java.net.SocketTimeoutException("timeout")

        val result = useCase(legacyToken)

        assertEquals(ValidationResult.Denied(DenialReason.SERVER_UNAVAILABLE), result)
    }
}
