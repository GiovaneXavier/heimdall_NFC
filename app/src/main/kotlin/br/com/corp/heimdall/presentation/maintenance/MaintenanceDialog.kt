package br.com.corp.heimdall.presentation.maintenance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.corp.heimdall.data.local.preferences.ConfigPreferences.Channel
import kotlinx.coroutines.delay

private enum class MaintenanceStep { PIN, OPTIONS, CHANGE_CHANNEL, CHANGE_PIN, DEVICE_INFO }

/**
 * Diálogo de manutenção protegido por PIN.
 *
 * Acessível via 5 toques rápidos no logo da tela de leitura.
 * Máximo de 5 tentativas erradas — bloqueia por 60s após esgotar.
 *
 * @param deviceId  UUID do dispositivo Heimdall (exibido na aba de info).
 * @param appVersion Versão do app (exibida na aba de info).
 * @param onDismiss Callback chamado ao fechar o diálogo.
 */
@Composable
fun MaintenanceDialog(
    deviceId: String,
    appVersion: String,
    onDismiss: () -> Unit,
    viewModel: MaintenanceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var step by remember { mutableStateOf(MaintenanceStep.PIN) }
    var pin by remember { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(0) }

    // Countdown de lockout
    LaunchedEffect(uiState) {
        if (uiState is MaintenanceUiState.LockedUntil) {
            val until = (uiState as MaintenanceUiState.LockedUntil).untilMs
            while (System.currentTimeMillis() < until) {
                countdown = ((until - System.currentTimeMillis()) / 1000).toInt() + 1
                delay(500)
            }
            countdown = 0
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is MaintenanceUiState.Unlocked && step == MaintenanceStep.PIN) {
            step = MaintenanceStep.OPTIONS
        }
    }

    AlertDialog(
        onDismissRequest = {
            viewModel.dismiss()
            onDismiss()
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = {
                viewModel.dismiss()
                onDismiss()
            }) { Text("Fechar") }
        },
        title = { Text("Manutenção") },
        text = {
            when (step) {
                MaintenanceStep.PIN -> PinStep(
                    pin = pin,
                    onPinChange = { pin = it },
                    uiState = uiState,
                    countdown = countdown,
                    onVerify = { viewModel.verifyPin(pin) },
                )

                MaintenanceStep.OPTIONS -> OptionsStep(
                    onChangeChannel = { step = MaintenanceStep.CHANGE_CHANNEL },
                    onChangePin = { step = MaintenanceStep.CHANGE_PIN },
                    onDeviceInfo = { step = MaintenanceStep.DEVICE_INFO },
                )

                MaintenanceStep.CHANGE_CHANNEL -> ChangeChannelStep(
                    currentChannel = viewModel.currentChannel ?: Channel.NFC,
                    currentSystemId = viewModel.currentSystemId,
                    onSave = { ch, sysId ->
                        viewModel.saveConfig(ch, sysId)
                        step = MaintenanceStep.OPTIONS
                    },
                    onBack = { step = MaintenanceStep.OPTIONS },
                )

                MaintenanceStep.CHANGE_PIN -> ChangePinStep(
                    onSave = { newPin, confirm ->
                        if (viewModel.saveNewPin(newPin, confirm)) step = MaintenanceStep.OPTIONS
                    },
                    onBack = { step = MaintenanceStep.OPTIONS },
                )

                MaintenanceStep.DEVICE_INFO -> DeviceInfoStep(
                    deviceId = deviceId,
                    appVersion = appVersion,
                    onBack = { step = MaintenanceStep.OPTIONS },
                )
            }
        },
    )
}

@Composable
private fun PinStep(
    pin: String,
    onPinChange: (String) -> Unit,
    uiState: MaintenanceUiState,
    countdown: Int,
    onVerify: () -> Unit,
) {
    Column {
        val isLocked = uiState is MaintenanceUiState.LockedUntil

        if (isLocked) {
            Text(
                "Bloqueado. Aguarde ${countdown}s.",
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            if (uiState is MaintenanceUiState.WrongPin) {
                Text(
                    "PIN incorreto. ${uiState.attemptsRemaining} tentativa(s) restante(s).",
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 6) onPinChange(it) },
                label = { Text("PIN de manutenção") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onVerify,
                modifier = Modifier.fillMaxWidth(),
                enabled = pin.length == 6,
            ) {
                Text("Verificar")
            }
        }
    }
}

@Composable
private fun OptionsStep(
    onChangeChannel: () -> Unit,
    onChangePin: () -> Unit,
    onDeviceInfo: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onChangeChannel, modifier = Modifier.fillMaxWidth()) {
            Text("Alterar canal / System ID")
        }
        Button(onClick = onChangePin, modifier = Modifier.fillMaxWidth()) {
            Text("Alterar PIN")
        }
        TextButton(onClick = onDeviceInfo, modifier = Modifier.fillMaxWidth()) {
            Text("Informações do dispositivo")
        }
    }
}

@Composable
private fun ChangeChannelStep(
    currentChannel: Channel,
    currentSystemId: String,
    onSave: (Channel, String) -> Unit,
    onBack: () -> Unit,
) {
    var selected by remember { mutableStateOf(currentChannel) }
    var sysId by remember { mutableStateOf(currentSystemId) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected == Channel.NFC, onClick = { selected = Channel.NFC })
            Text("NFC", modifier = Modifier.padding(end = 16.dp))
            RadioButton(selected = selected == Channel.QR, onClick = { selected = Channel.QR })
            Text("QR Code")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = sysId,
            onValueChange = { sysId = it },
            label = { Text("System ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text("Voltar") }
            Button(onClick = { onSave(selected, sysId) }, enabled = sysId.isNotBlank()) {
                Text("Salvar")
            }
        }
    }
}

@Composable
private fun ChangePinStep(onSave: (String, String) -> Unit, onBack: () -> Unit) {
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = newPin.length == 6 && confirm.length == 6 && newPin != confirm

    Column {
        OutlinedTextField(
            value = newPin,
            onValueChange = { if (it.length <= 6) newPin = it },
            label = { Text("Novo PIN (6 dígitos)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { if (it.length <= 6) confirm = it },
            label = { Text("Confirmar PIN") },
            isError = mismatch,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        if (mismatch) {
            Text("PINs não coincidem.", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text("Voltar") }
            Button(
                onClick = { onSave(newPin, confirm) },
                enabled = newPin.length == 6 && confirm.length == 6 && !mismatch,
            ) {
                Text("Salvar")
            }
        }
    }
}

@Composable
private fun DeviceInfoStep(deviceId: String, appVersion: String, onBack: () -> Unit) {
    Column {
        Text("Device ID:", style = MaterialTheme.typography.labelMedium)
        Text(deviceId, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Text("Versão:", style = MaterialTheme.typography.labelMedium)
        Text(appVersion)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) { Text("Voltar") }
    }
}
