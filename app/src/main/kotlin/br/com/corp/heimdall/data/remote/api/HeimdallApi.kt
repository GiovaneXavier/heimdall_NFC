package br.com.corp.heimdall.data.remote.api

import br.com.corp.heimdall.data.remote.dto.ValidateRequestDto
import br.com.corp.heimdall.data.remote.dto.ValidateResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface HeimdallApi {

    /**
     * Valida um token Huginn contra o backend.
     *
     * POST /api/v1/validate
     *
     * O backend verifica: whitelist de dispositivos, validade do nonce (anti-replay),
     * existência do funcionário e HMAC. Retorna dados do funcionário se aprovado.
     */
    @POST("api/v1/validate")
    suspend fun validate(@Body request: ValidateRequestDto): Response<ValidateResponseDto>
}
