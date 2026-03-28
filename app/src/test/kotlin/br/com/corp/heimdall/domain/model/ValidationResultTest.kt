package br.com.corp.heimdall.domain.model

import org.junit.Assert.*
import org.junit.Test

class ValidationResultTest {

    @Test
    fun `Approved holds EmployeeInfo correctly`() {
        val employee = EmployeeInfo(id = "EMP001", name = "Ana Lima", photoUrl = "https://example.com/photo.jpg")
        val result = ValidationResult.Approved(employee)
        assertEquals(employee, result.employee)
    }

    @Test
    fun `Denied holds denial reason correctly`() {
        val result = ValidationResult.Denied(DenialReason.INVALID_HMAC)
        assertEquals(DenialReason.INVALID_HMAC, result.reason)
    }

    @Test
    fun `all DenialReason values are distinct`() {
        val reasons = DenialReason.entries
        assertEquals(reasons.size, reasons.toSet().size)
    }

    @Test
    fun `Approved equality holds`() {
        val emp = EmployeeInfo("E1", "Bob", "")
        assertEquals(ValidationResult.Approved(emp), ValidationResult.Approved(emp))
    }

    @Test
    fun `Denied equality holds for same reason`() {
        assertEquals(
            ValidationResult.Denied(DenialReason.NONCE_REPLAY),
            ValidationResult.Denied(DenialReason.NONCE_REPLAY)
        )
    }

    @Test
    fun `Denied inequality for different reasons`() {
        assertNotEquals(
            ValidationResult.Denied(DenialReason.EXPIRED),
            ValidationResult.Denied(DenialReason.FUTURE_TOKEN)
        )
    }
}
