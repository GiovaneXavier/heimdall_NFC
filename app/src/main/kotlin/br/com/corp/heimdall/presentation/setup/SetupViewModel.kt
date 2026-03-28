package br.com.corp.heimdall.presentation.setup

import androidx.lifecycle.ViewModel
import br.com.corp.heimdall.data.local.preferences.ConfigPreferences
import br.com.corp.heimdall.data.local.preferences.ConfigPreferences.Channel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * ViewModel para a tela de configuração inicial do ponto de acesso.
 *
 * Valida os campos e persiste a configuração via [ConfigPreferences].
 * Emite [SetupUiState.NavigateToReader] quando o setup é concluído com sucesso.
 */
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val config: ConfigPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SetupUiState>(SetupUiState.Idle)
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    fun saveConfig(channel: Channel, systemId: String, pin: String, pinConfirm: String) {
        if (systemId.isBlank()) {
            _uiState.update { SetupUiState.Error("O ID do sistema não pode estar vazio.") }
            return
        }
        if (pin.length != 6 || pin.any { !it.isDigit() }) {
            _uiState.update { SetupUiState.Error("O PIN deve ter exatamente 6 dígitos numéricos.") }
            return
        }
        if (pin != pinConfirm) {
            _uiState.update { SetupUiState.Error("Os PINs não coincidem.") }
            return
        }

        config.channel = channel
        config.systemId = systemId.trim()
        config.savePin(pin)

        _uiState.update { SetupUiState.NavigateToReader }
    }

    fun clearError() {
        _uiState.update { SetupUiState.Idle }
    }
}

/** Estados possíveis da tela de setup. */
sealed class SetupUiState {
    data object Idle : SetupUiState()
    data class Error(val message: String) : SetupUiState()
    data object NavigateToReader : SetupUiState()
}
