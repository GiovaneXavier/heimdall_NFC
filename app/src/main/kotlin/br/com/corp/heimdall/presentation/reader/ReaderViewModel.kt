package br.com.corp.heimdall.presentation.reader

import android.nfc.tech.IsoDep
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.corp.heimdall.data.local.db.AccessLogEntry
import br.com.corp.heimdall.data.local.preferences.ConfigPreferences
import br.com.corp.heimdall.data.local.preferences.ConfigPreferences.Channel
import br.com.corp.heimdall.domain.model.DenialReason
import br.com.corp.heimdall.domain.model.EmployeeInfo
import br.com.corp.heimdall.domain.model.Token
import br.com.corp.heimdall.domain.model.ValidationResult
import br.com.corp.heimdall.domain.repository.AuditLogRepository
import br.com.corp.heimdall.domain.usecase.ParseTokenUseCase
import br.com.corp.heimdall.domain.usecase.ValidateLegacyTokenUseCase
import br.com.corp.heimdall.domain.usecase.ValidateNewTokenUseCase
import br.com.corp.heimdall.presentation.reader.nfc.NfcReaderHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel que orquestra o fluxo de leitura NFC e QR.
 *
 * Responsável por:
 * - Chamar [NfcReaderHelper] para extrair o token da tag NFC
 * - Chamar [ParseTokenUseCase] para distinguir o formato
 * - Despachar para [ValidateNewTokenUseCase] ou [ValidateLegacyTokenUseCase]
 * - Gravar o resultado no audit log via [AuditLogRepository]
 * - Emitir [ReaderUiState] para a UI
 *
 * Novas leituras são ignoradas enquanto o estado for [ReaderUiState.Processing].
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val nfcHelper: NfcReaderHelper,
    private val parseToken: ParseTokenUseCase,
    private val validateNew: ValidateNewTokenUseCase,
    private val validateLegacy: ValidateLegacyTokenUseCase,
    private val auditLog: AuditLogRepository,
    config: ConfigPreferences,
) : ViewModel() {

    /** Canal de leitura configurado neste ponto de acesso. */
    val channel: Channel = config.channel ?: Channel.NFC

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Idle)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    /** Debug info da última leitura NFC (hex, SW bytes, payload). Null até a primeira leitura. */
    private val _nfcDebugInfo = MutableStateFlow<String?>(null)
    val nfcDebugInfo: StateFlow<String?> = _nfcDebugInfo.asStateFlow()

    /**
     * Chamado pela [br.com.corp.heimdall.MainActivity] quando uma tag NFC é detectada.
     * Extrai o token via APDU e inicia o pipeline de validação.
     */
    fun onNfcTagDetected(isoDep: IsoDep) {
        if (_uiState.value is ReaderUiState.Processing) return

        viewModelScope.launch {
            _uiState.update { ReaderUiState.Processing }
            val raw = nfcHelper.sendSelectApdu(isoDep)
            _nfcDebugInfo.update { nfcHelper.lastDebugInfo }
            if (raw == null) {
                val result = ValidationResult.Denied(DenialReason.INVALID_FORMAT)
                writeAuditLog(result, Channel.NFC, deviceId = "", employeeId = "")
                emitResult(result, employee = null)
                return@launch
            }
            processRaw(raw, Channel.NFC)
        }
    }

    /**
     * Chamado quando uma tag NFC é detectada mas não suporta IsoDep.
     * Atualiza o debug sem iniciar validação.
     */
    fun onNfcTagWithoutIsoDep(techList: Array<String>) {
        _nfcDebugInfo.update {
            buildString {
                appendLine("Tag NFC detectada — SEM IsoDep!")
                appendLine("Tecnologias encontradas:")
                techList.forEach { appendLine("  • $it") }
                appendLine()
                appendLine("O Huginn precisa estar em foreground")
                appendLine("e com HCE ativo para expor IsoDep.")
            }.trimEnd()
        }
    }

    /**
     * Chamado pelo [br.com.corp.heimdall.presentation.reader.qr.QrAnalyzer]
     * quando um QR Code é decodificado.
     */
    fun onQrDetected(raw: String) {
        if (_uiState.value is ReaderUiState.Processing) return

        viewModelScope.launch {
            _uiState.update { ReaderUiState.Processing }
            processRaw(raw, Channel.QR)
        }
    }

    /** Notifica a UI que o estado NavigateToResult foi consumido; retorna para Idle. */
    fun onResultConsumed() {
        _uiState.update { ReaderUiState.Idle }
    }

    /** Cancela processamento em andamento (ex: app vai para background). */
    fun onAppBackground() {
        if (_uiState.value is ReaderUiState.Processing) {
            _uiState.update { ReaderUiState.Idle }
        }
    }

    private suspend fun processRaw(raw: String, channel: Channel) {
        when (val token = parseToken(raw)) {
            is Token.New -> {
                val result = validateNew(token)
                val employee = if (result is ValidationResult.Approved) result.employee else null
                writeAuditLog(result, channel, token.deviceId, token.employeeId)
                emitResult(result, employee)
            }
            is Token.Legacy -> {
                val result = validateLegacy(token)
                val employee = if (result is ValidationResult.Approved) result.employee else null
                writeAuditLog(result, channel, deviceId = "", employeeId = token.code)
                emitResult(result, employee)
            }
            is Token.Invalid -> {
                val result = ValidationResult.Denied(DenialReason.INVALID_FORMAT)
                writeAuditLog(result, channel, deviceId = "", employeeId = "")
                emitResult(result, employee = null)
            }
        }
    }

    private suspend fun writeAuditLog(
        result: ValidationResult,
        channel: Channel,
        deviceId: String,
        employeeId: String,
    ) {
        val approved = result is ValidationResult.Approved
        val employee = if (result is ValidationResult.Approved) result.employee else null
        auditLog.log(
            AccessLogEntry(
                timestampMs = System.currentTimeMillis(),
                employeeId = employee?.id ?: employeeId,
                employeeName = employee?.name ?: "",
                deviceId = deviceId,
                channel = channel.name,
                result = if (approved) "APPROVED" else "DENIED",
                denialReason = if (result is ValidationResult.Denied) result.reason.name else "",
            )
        )
    }

    private fun emitResult(result: ValidationResult, employee: EmployeeInfo?) {
        _uiState.update { ReaderUiState.NavigateToResult(result, employee) }
    }
}

/** Estados possíveis da tela de leitura. */
sealed class ReaderUiState {
    /** Aguardando leitura — exibe ícone NFC animado ou câmera ativa. */
    data object Idle : ReaderUiState()

    /** Processando token lido — exibe progress indicator. */
    data object Processing : ReaderUiState()

    /**
     * Validação concluída — UI deve navegar para a tela de resultado.
     *
     * @property result   Resultado da validação.
     * @property employee Dados do funcionário (apenas em caso de aprovação).
     */
    data class NavigateToResult(
        val result: ValidationResult,
        val employee: EmployeeInfo?,
    ) : ReaderUiState()
}
