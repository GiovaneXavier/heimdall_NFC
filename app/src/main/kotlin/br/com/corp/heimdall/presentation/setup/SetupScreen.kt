package br.com.corp.heimdall.presentation.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.corp.heimdall.data.local.preferences.ConfigPreferences.Channel

/**
 * Tela de configuração inicial do ponto de acesso.
 *
 * Exibida apenas na primeira execução ou quando o admin acessa via PIN de manutenção.
 * Após salvar com sucesso, dispara [onNavigateToReader].
 */
@Composable
fun SetupScreen(
    onNavigateToReader: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is SetupUiState.NavigateToReader -> onNavigateToReader()
            is SetupUiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearError()
            }
            else -> Unit
        }
    }

    var selectedChannel by remember { mutableStateOf(Channel.NFC) }
    var systemId by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Configuração do Ponto de Acesso", style = MaterialTheme.typography.headlineSmall)

            Spacer(Modifier.height(32.dp))

            Text("Canal de leitura", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedChannel == Channel.NFC,
                    onClick = { selectedChannel = Channel.NFC },
                )
                Text("NFC", modifier = Modifier.padding(end = 16.dp))
                RadioButton(
                    selected = selectedChannel == Channel.QR,
                    onClick = { selectedChannel = Channel.QR },
                )
                Text("QR Code")
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = systemId,
                onValueChange = { systemId = it },
                label = { Text("ID do Sistema (ex: ACCESS_HQ)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 6) pin = it },
                label = { Text("PIN de manutenção (6 dígitos)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = pinConfirm,
                onValueChange = { if (it.length <= 6) pinConfirm = it },
                label = { Text("Confirmar PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = { viewModel.saveConfig(selectedChannel, systemId, pin, pinConfirm) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Salvar e Iniciar")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SetupScreenPreview() {
    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                "Setup Screen Preview",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}
