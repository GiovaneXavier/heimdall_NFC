package br.com.corp.heimdall.domain.usecase

import br.com.corp.heimdall.core.crypto.HmacValidator
import br.com.corp.heimdall.core.util.TimeProvider
import br.com.corp.heimdall.domain.model.DenialReason
import br.com.corp.heimdall.domain.model.EmployeeInfo
import br.com.corp.heimdall.domain.model.Token
import br.com.corp.heimdall.domain.model.ValidationResult
import br.com.corp.heimdall.domain.repository.ValidationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ValidateNewTokenUseCaseTest {

    private val hmacValidator: HmacValidator       = mockk()
    private val timeProvider: TimeProvider         = mockk()
    private val repository: ValidationRepository   = mockk()

    private val configuredSystemId = "SRBR_EXIT"
    private val now                = 1_710_000_000L

    private lateinit var useCase: ValidateNewTokenUseCase

    private val validToken = Token.New(
        deviceId   = "DEVICE001",
        employeeId = "EMP001",
        systemId   = configuredSystemId,
        timestamp  = now,
        nonce      = "abc123",
        hmac       = "validHmac",
    )

    private val approvedEmployee = EmployeeInfo("EMP001", "Ana Lima", "https://example.com/photo.jpg")

    @Before
    fun setup() {
        useCase = ValidateNewTokenUseCase(hmacValidator, timeProvider, repository, configuredSystemId)
        every { timeProvider.nowSeconds() } returns now
    }

    // ── Etapa 1: HMAC ─────────────────────────────────────────────────────────

    @Test
    fun `invalid HMAC returns Denied INVALID_HMAC without calling repository`() = runTest {
        every { hmacValidator.verify(any(), any(), any()) } returns false

        val result = useCase(validToken)

        assertEquals(ValidationResult.Denied(DenialReason.INVALID_HMAC), result)
        coVerify(exactly = 0) { repository.validate(any()) }
    }

    // ── Etapa 2: Janela temporal ──────────────────────────────────────────────

    @Test
    fun `timestamp 31s in the past returns Denied EXPIRED`() = runTest {
        every { hmacValidator.verify(any(), any(), any()) } returns true
        val expiredToken = validToken.copy(timestamp = now - 31L)

        val result = useCase(expiredToken)

        assertEquals(ValidationResult.Denied(DenialReason.EXPIRED), result)
        coVerify(exactly = 0) { repository.validate(any()) }
    }

    @Test
    fun `timestamp 31s in the future returns Denied FUTURE_TOKEN`() = runTest {
        every { hmacValidator.verify(any(), any(), any()) } returns true
        val futureToken = validToken.copy(timestamp = now + 31L)

        val result = useCase(futureToken)

        assertEquals(ValidationResult.Denied(DenialReason.FUTURE_TOKEN), result)
        coVerify(exactly = 0) { repository.validate(any()) }
    }

    @Test
    fun `timestamp exactly at boundary (30s past) is accepted`() = runTest {
        every { hmacValidator.verify(any(), any(), any()) } returns true
        coEvery { repository.validate(any()) } returns ValidationResult.Approved(approvedEmployee)
        val boundaryToken = validToken.copy(timestamp = now - 30L)

        val result = useCase(boundaryToken)

        assertTrue(result is ValidationResult.Approved)
    }

    // ── Etapa 3: systemId ─────────────────────────────────────────────────────

    @Test
    fun `wrong systemId returns Denied WRONG_SYSTEM without calling repository`() = runTest {
        every { hmacValidator.verify(any(), any(), any()) } returns true
        val wrongSysToken = validToken.copy(systemId = "ANOTHER_SYSTEM")

        val result = useCase(wrongSysToken)

        assertEquals(ValidationResult.Denied(DenialReason.WRONG_SYSTEM), result)
        coVerify(exactly = 0) { repository.validate(any()) }
    }

    // ── Etapa 4: Backend ──────────────────────────────────────────────────────

    @Test
    fun `all local checks pass and repository returns Approved`() = runTest {
        every { hmacValidator.verify(any(), any(), any()) } returns true
        coEvery { repository.validate(any()) } returns ValidationResult.Approved(approvedEmployee)

        val result = useCase(validToken)

        assertEquals(ValidationResult.Approved(approvedEmployee), result)
        coVerify(exactly = 1) { repository.validate(validToken) }
    }

    @Test
    fun `all local checks pass and repository returns Denied NONCE_REPLAY`() = runTest {
        every { hmacValidator.verify(any(), any(), any()) } returns true
        coEvery { repository.validate(any()) } returns ValidationResult.Denied(DenialReason.NONCE_REPLAY)

        val result = useCase(validToken)

        assertEquals(ValidationResult.Denied(DenialReason.NONCE_REPLAY), result)
    }

    @Test
    fun `repository throws exception returns Denied SERVER_UNAVAILABLE`() = runTest {
        every { hmacValidator.verify(any(), any(), any()) } returns true
        coEvery { repository.validate(any()) } throws java.net.SocketTimeoutException("timeout")

        val result = useCase(validToken)

        assertEquals(ValidationResult.Denied(DenialReason.SERVER_UNAVAILABLE), result)
    }

    // ── Verificação de ordem de pipeline ──────────────────────────────────────

    @Test
    fun `HMAC checked before timestamp (HMAC fails, time not checked)`() = runTest {
        every { hmacValidator.verify(any(), any(), any()) } returns false
        // token com timestamp inválido, mas HMAC falha primeiro
        val expiredAndInvalidHmac = validToken.copy(timestamp = now - 31L)

        val result = useCase(expiredAndInvalidHmac)

        assertEquals(ValidationResult.Denied(DenialReason.INVALID_HMAC), result)
        verify(exactly = 1) { hmacValidator.verify(any(), any(), any()) }
        coVerify(exactly = 0) { repository.validate(any()) }
    }
}
