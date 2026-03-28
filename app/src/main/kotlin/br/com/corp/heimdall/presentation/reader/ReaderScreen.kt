package br.com.corp.heimdall.presentation.reader

import android.view.WindowManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.corp.heimdall.domain.model.DenialReason
import br.com.corp.heimdall.domain.model.ValidationResult
import br.com.corp.heimdall.presentation.maintenance.MaintenanceDialog
import kotlinx.coroutines.delay

/**
 * Tela principal de leitura no modo NFC.
 *
 * Exibe ícone NFC animado em estado [ReaderUiState.Idle] e progress indicator durante
 * [ReaderUiState.Processing]. Ao receber [ReaderUiState.NavigateToResult], dispara
 * [onNavigateToResult] e consome o estado.
 *
 * O logo recebe 5 toques rápidos (< 2s) para abrir o [MaintenanceDialog].
 */
@Composable
fun ReaderScreen(
    onNavigateToResult: (Boolean, String, String, String) -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Mantém a tela sempre ligada
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Navega ao resultado quando disponível
    LaunchedEffect(uiState) {
        val state = uiState
        if (state is ReaderUiState.NavigateToResult) {
            val (isApproved, name, photo, denial) = extractResultArgs(state)
            onNavigateToResult(isApproved, name, photo, denial)
            viewModel.onResultConsumed()
        }
    }

    var showMaintenance by remember { mutableStateOf(false) }
    var logoTapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }

    // Detector de 5 toques rápidos no logo
    val deviceId = "N/A" // será substituído por DeviceIdProvider no T-21
    val appVersion = "1.0.0"

    if (showMaintenance) {
        MaintenanceDialog(
            deviceId = deviceId,
            appVersion = appVersion,
            onDismiss = { showMaintenance = false },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        when (uiState) {
            is ReaderUiState.Processing -> {
                CircularProgressIndicator(modifier = Modifier.size(72.dp))
            }

            else -> {
                NfcIdleContent(
                    onLogoTap = {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime > 2000) logoTapCount = 0
                        lastTapTime = now
                        logoTapCount++
                        if (logoTapCount >= 5) {
                            logoTapCount = 0
                            showMaintenance = true
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun NfcIdleContent(onLogoTap: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "nfc_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Nfc,
            contentDescription = "NFC",
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer { this.alpha = alpha }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onLogoTap() })
                },
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Aproxime o celular",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

private data class ResultArgs(
    val isApproved: Boolean,
    val name: String,
    val photo: String,
    val denial: String,
)

private fun extractResultArgs(state: ReaderUiState.NavigateToResult): ResultArgs {
    val isApproved = state.result is ValidationResult.Approved
    val name = state.employee?.name ?: ""
    val photo = state.employee?.photoUrl ?: ""
    val denial = if (state.result is ValidationResult.Denied) {
        state.result.reason.toDisplayMessage()
    } else ""
    return ResultArgs(isApproved, name, photo, denial)
}

private fun DenialReason.toDisplayMessage(): String = when (this) {
    DenialReason.INVALID_HMAC -> "Assinatura inválida"
    DenialReason.EXPIRED -> "Token expirado"
    DenialReason.FUTURE_TOKEN -> "Token do futuro"
    DenialReason.WRONG_SYSTEM -> "Sistema incorreto"
    DenialReason.DEVICE_NOT_WHITELISTED -> "Dispositivo não autorizado"
    DenialReason.NONCE_REPLAY -> "Token já utilizado"
    DenialReason.EMPLOYEE_NOT_FOUND -> "Funcionário não encontrado"
    DenialReason.SERVER_UNAVAILABLE -> "Servidor indisponível"
    DenialReason.INVALID_FORMAT -> "Formato inválido"
}
