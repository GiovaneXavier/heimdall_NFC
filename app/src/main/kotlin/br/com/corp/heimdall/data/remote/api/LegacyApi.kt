package br.com.corp.heimdall.data.remote.api

import br.com.corp.heimdall.data.remote.dto.LegacyValidateRequestDto
import br.com.corp.heimdall.data.remote.dto.ValidateResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface LegacyApi {

    /**
     * Valida um token legado (CORP.XXXX / PART.XXXX) via API legada.
     *
     * POST /api/v1/legacy-validate
     */
    @POST("api/v1/legacy-validate")
    suspend fun validateLegacy(@Body request: LegacyValidateRequestDto): Response<ValidateResponseDto>
}
