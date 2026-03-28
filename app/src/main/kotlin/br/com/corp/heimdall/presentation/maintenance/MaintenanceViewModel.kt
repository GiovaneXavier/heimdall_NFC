package br.com.corp.heimdall.presentation.maintenance

import androidx.lifecycle.ViewModel
import br.com.corp.heimdall.data.local.preferences.ConfigPreferences
import br.com.corp.heimdall.data.local.preferences.ConfigPreferences.Channel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

private const val MAX_ATTEMPTS = 5
private const val LOCKOUT_DURATION_MS = 60_000L

/**
 * ViewModel para o diálogo de manutenção protegido por PIN.
 *
 * Gerencia tentativas de PIN com bloqueio temporário após [MAX_ATTEMPTS] falhas.
 * Após desbloqueio, permite alterar canal, systemId e o próprio PIN.
 */
@HiltViewModel
class MaintenanceViewModel @Inject constructor(
    private val config: ConfigPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MaintenanceUiState>(MaintenanceUiState.Locked)
    val uiState: StateFlow<MaintenanceUiState> = _uiState.asStateFlow()

    private var failedAttempts = 0
    private var lockedUntilMs = 0L

    /** Retorna o systemId atual para pré-preencher o campo de edição. */
    val currentSystemId: String get() = config.systemId

    /** Retorna o canal atual. */
    val currentChannel: Channel? get() = config.channel

    /**
     * Verifica o PIN digitado pelo administrador.
     * Em caso de erro excessivo, bloqueia por [LOCKOUT_DURATION_MS].
     */
    fun verifyPin(pin: String) {
        val now = System.currentTimeMillis()
        if (now < lockedUntilMs) {
            val remaining = (lockedUntilMs - now) / 1000
            _uiState.update { MaintenanceUiState.LockedUntil(lockedUntilMs, remaining) }
            return
        }

        if (config.verifyPin(pin)) {
            failedAttempts = 0
            _uiState.update { MaintenanceUiState.Unlocked }
        } else {
            failedAttempts++
            if (failedAttempts >= MAX_ATTEMPTS) {
                lockedUntilMs = System.currentTimeMillis() + LOCKOUT_DURATION_MS
                failedAttempts = 0
                val remaining = LOCKOUT_DURATION_MS / 1000
                _uiState.update { MaintenanceUiState.LockedUntil(lockedUntilMs, remaining) }
            } else {
                val remaining = MAX_ATTEMPTS - failedAttempts
                _uiState.update { MaintenanceUiState.WrongPin(attemptsRemaining = remaining) }
            }
        }
    }

    /** Salva novo canal e/ou systemId. Exige estar desbloqueado. */
    fun saveConfig(channel: Channel, systemId: String) {
        if (_uiState.value !is MaintenanceUiState.Unlocked) return
        config.channel = channel
        config.systemId = systemId.trim()
    }

    /** Salva novo PIN. Exige estar desbloqueado. */
    fun saveNewPin(pin: String, pinConfirm: String): Boolean {
        if (_uiState.value !is MaintenanceUiState.Unlocked) return false
        if (pin.length != 6 || pin != pinConfirm) return false
        config.savePin(pin)
        return true
    }

    fun dismiss() {
        _uiState.update { MaintenanceUiState.Locked }
        failedAttempts = 0
    }
}

/** Estados do diálogo de manutenção. */
sealed class MaintenanceUiState {
    data object Locked : MaintenanceUiState()
    data class WrongPin(val attemptsRemaining: Int) : MaintenanceUiState()
    data class LockedUntil(val untilMs: Long, val remainingSeconds: Long) : MaintenanceUiState()
    data object Unlocked : MaintenanceUiState()
}
