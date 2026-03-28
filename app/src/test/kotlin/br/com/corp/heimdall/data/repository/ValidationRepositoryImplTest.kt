package br.com.corp.heimdall.data.repository

import br.com.corp.heimdall.data.remote.api.HeimdallApi
import br.com.corp.heimdall.data.remote.api.LegacyApi
import br.com.corp.heimdall.data.remote.dto.EmployeeDto
import br.com.corp.heimdall.data.remote.dto.ValidateResponseDto
import br.com.corp.heimdall.domain.model.DenialReason
import br.com.corp.heimdall.domain.model.Token
import br.com.corp.heimdall.domain.model.ValidationResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class ValidationRepositoryImplTest {

    private val heimdallApi: HeimdallApi = mockk()
    private val legacyApi: LegacyApi = mockk()
    private lateinit var repository: ValidationRepositoryImpl

    private val newToken = Token.New(
        deviceId = "dev-1",
        employeeId = "SRBR-001",
        systemId = "ACCESS_HQ",
        timestamp = 1711500000L,
        nonce = "abc",
        hmac = "sig",
    )
    private val legacyToken = Token.Legacy(prefix = "CORP", code = "1234")

    private val employeeDto = EmployeeDto(
        id = "SRBR-001",
        name = "Ana Lima",
        photoUrl = "https://cdn.corp.br/photos/001.jpg",
    )

    @Before
    fun setup() {
        repository = ValidationRepositoryImpl(heimdallApi, legacyApi)
    }

    // ── Token Novo ────────────────────────────────────────────────────────────

    @Test
    fun `validate retorna Approved quando backend retorna approved true`() = runTest {
        coEvery { heimdallApi.validate(any()) } returns Response.success(
            ValidateResponseDto(approved = true, employee = employeeDto)
        )

        val result = repository.validate(newToken)

        assertTrue(result is ValidationResult.Approved)
        val approved = result as ValidationResult.Approved
        assertEquals("SRBR-001", approved.employee.id)
        assertEquals("Ana Lima", approved.employee.name)
    }

    @Test
    fun `validate retorna Denied com reason do servidor quando approved false`() = runTest {
        coEvery { heimdallApi.validate(any()) } returns Response.success(
            ValidateResponseDto(approved = false, denialReason = "NONCE_REPLAY")
        )

        val result = repository.validate(newToken)

        assertTrue(result is ValidationResult.Denied)
        assertEquals(DenialReason.NONCE_REPLAY, (result as ValidationResult.Denied).reason)
    }

    @Test
    fun `validate retorna SERVER_UNAVAILABLE para reason desconhecida`() = runTest {
        coEvery { heimdallApi.validate(any()) } returns Response.success(
            ValidateResponseDto(approved = false, denialReason = "UNKNOWN_REASON_XYZ")
        )

        val result = repository.validate(newToken)

        assertEquals(DenialReason.SERVER_UNAVAILABLE, (result as ValidationResult.Denied).reason)
    }

    @Test
    fun `validate retorna DEVICE_NOT_WHITELISTED para HTTP 403`() = runTest {
        coEvery { heimdallApi.validate(any()) } returns Response.error(
            403, "Forbidden".toResponseBody()
        )

        val result = repository.validate(newToken)

        assertEquals(DenialReason.DEVICE_NOT_WHITELISTED, (result as ValidationResult.Denied).reason)
    }

    @Test
    fun `validate retorna EMPLOYEE_NOT_FOUND para HTTP 404`() = runTest {
        coEvery { heimdallApi.validate(any()) } returns Response.error(
            404, "Not Found".toResponseBody()
        )

        val result = repository.validate(newToken)

        assertEquals(DenialReason.EMPLOYEE_NOT_FOUND, (result as ValidationResult.Denied).reason)
    }

    @Test
    fun `validate retorna NONCE_REPLAY para HTTP 409`() = runTest {
        coEvery { heimdallApi.validate(any()) } returns Response.error(
            409, "Conflict".toResponseBody()
        )

        val result = repository.validate(newToken)

        assertEquals(DenialReason.NONCE_REPLAY, (result as ValidationResult.Denied).reason)
    }

    @Test
    fun `validate retorna SERVER_UNAVAILABLE para HTTP 500`() = runTest {
        coEvery { heimdallApi.validate(any()) } returns Response.error(
            500, "Internal Server Error".toResponseBody()
        )

        val result = repository.validate(newToken)

        assertEquals(DenialReason.SERVER_UNAVAILABLE, (result as ValidationResult.Denied).reason)
    }

    @Test
    fun `validate retorna SERVER_UNAVAILABLE quando body e null`() = runTest {
        coEvery { heimdallApi.validate(any()) } returns Response.success(null)

        val result = repository.validate(newToken)

        assertEquals(DenialReason.SERVER_UNAVAILABLE, (result as ValidationResult.Denied).reason)
    }

    // ── Token Legado ──────────────────────────────────────────────────────────

    @Test
    fun `validateLegacy retorna Approved quando backend aprova`() = runTest {
        coEvery { legacyApi.validateLegacy(any()) } returns Response.success(
            ValidateResponseDto(approved = true, employee = employeeDto)
        )

        val result = repository.validateLegacy(legacyToken)

        assertTrue(result is ValidationResult.Approved)
    }

    @Test
    fun `validateLegacy retorna Denied para HTTP 401`() = runTest {
        coEvery { legacyApi.validateLegacy(any()) } returns Response.error(
            401, "Unauthorized".toResponseBody()
        )

        val result = repository.validateLegacy(legacyToken)

        assertEquals(DenialReason.DEVICE_NOT_WHITELISTED, (result as ValidationResult.Denied).reason)
    }
}
