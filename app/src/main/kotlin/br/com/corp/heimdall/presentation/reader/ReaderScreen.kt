package br.com.corp.heimdall.presentation.reader

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.corp.heimdall.R
import br.com.corp.heimdall.data.local.preferences.ConfigPreferences.Channel
import br.com.corp.heimdall.domain.model.DenialReason
import br.com.corp.heimdall.domain.model.ValidationResult
import br.com.corp.heimdall.presentation.maintenance.MaintenanceDialog
import br.com.corp.heimdall.presentation.reader.qr.QrReaderComposable
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

/**
 * Tela principal de leitura — adapta UI ao canal configurado (NFC ou QR).
 *
 * **Modo NFC:** ícone pulsante + texto "Aproxime o celular".
 * **Modo QR:** preview full-screen da câmera + overlay com mira + tratamento de permissão.
 *
 * Em ambos os modos: 5 toques rápidos no logo abre [MaintenanceDialog].
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ReaderScreen(
    onNavigateToResult: (Boolean, String, String, String) -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(LocalContext.current as ComponentActivity),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

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

    val onLogoTap: () -> Unit = {
        val now = System.currentTimeMillis()
        if (now - lastTapTime > 2000) logoTapCount = 0
        lastTapTime = now
        logoTapCount++
        if (logoTapCount >= 5) { logoTapCount = 0; showMaintenance = true }
    }

    if (showMaintenance) {
        MaintenanceDialog(
            deviceId = "N/A",
            appVersion = "1.0.0",
            onDismiss = { showMaintenance = false },
        )
    }

    val nfcDebugInfo by viewModel.nfcDebugInfo.collectAsStateWithLifecycle()

    when (viewModel.channel) {
        Channel.NFC -> NfcReaderContent(uiState = uiState, debugInfo = nfcDebugInfo, onLogoTap = onLogoTap)
        Channel.QR  -> QrReaderContent(
            uiState = uiState,
            onQrDetected = viewModel::onQrDetected,
            onLogoTap = onLogoTap,
        )
    }
}

// ── Modo NFC ──────────────────────────────────────────────────────────────────

@Composable
private fun NfcReaderContent(uiState: ReaderUiState, debugInfo: String?, onLogoTap: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        when (uiState) {
            is ReaderUiState.Processing -> CircularProgressIndicator(Modifier.size(72.dp))
            else -> NfcIdleContent(onLogoTap)
        }

        NfcDebugPanel(
            debugInfo = debugInfo ?: "Aguardando leitura NFC...",
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun NfcDebugPanel(debugInfo: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "DEBUG NFC",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF00FF88),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = debugInfo,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
    }
}

@Composable
private fun NfcIdleContent(onLogoTap: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "nfc_pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "alpha",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_nfc),
            contentDescription = "NFC",
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer { this.alpha = alpha }
                .pointerInput(Unit) { detectTapGestures(onTap = { onLogoTap() }) },
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Aproxime o celular",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

// ── Modo QR ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun QrReaderContent(
    uiState: ReaderUiState,
    onQrDetected: (String) -> Unit,
    onLogoTap: () -> Unit,
) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val context = LocalContext.current

    // Solicita permissão na primeira composição
    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    if (!cameraPermission.status.isGranted) {
        CameraPermissionDenied(
            showRationale = cameraPermission.status.shouldShowRationale,
            onRequestPermission = { cameraPermission.launchPermissionRequest() },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    },
                )
            },
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Preview da câmera full-screen
        QrReaderComposable(
            onQrDetected = onQrDetected,
            modifier = Modifier.fillMaxSize(),
        )

        // Overlay de texto no topo
        Text(
            text = "Aponte o QR para a câmera",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // Mira central
        QrViewFinder(
            modifier = Modifier.align(Alignment.Center),
            onLogoTap = onLogoTap,
        )

        // Overlay de processing sobre a câmera
        if (uiState is ReaderUiState.Processing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(72.dp))
            }
        }
    }
}

@Composable
private fun QrViewFinder(modifier: Modifier = Modifier, onLogoTap: () -> Unit) {
    // Mira simples: canto superior-esquerdo desenhado via Canvas ou ícone placeholder
    Box(
        modifier = modifier
            .size(220.dp)
            .pointerInput(Unit) { detectTapGestures(onTap = { onLogoTap() }) },
        contentAlignment = Alignment.Center,
    ) {
        // Os 4 cantos da mira são desenhados via bordas de Boxes sobrepostas
        val cornerSize = 32.dp
        val cornerThickness = 4.dp
        val cornerColor = Color.White

        // Canto superior-esquerdo
        Box(Modifier.align(Alignment.TopStart)) {
            Box(Modifier.size(cornerSize, cornerThickness).background(cornerColor))
            Box(Modifier.size(cornerThickness, cornerSize).background(cornerColor))
        }
        // Canto superior-direito
        Box(Modifier.align(Alignment.TopEnd)) {
            Box(Modifier.size(cornerSize, cornerThickness).background(cornerColor).align(Alignment.TopEnd))
            Box(Modifier.size(cornerThickness, cornerSize).background(cornerColor).align(Alignment.TopEnd))
        }
        // Canto inferior-esquerdo
        Box(Modifier.align(Alignment.BottomStart)) {
            Box(Modifier.size(cornerSize, cornerThickness).background(cornerColor).align(Alignment.BottomStart))
            Box(Modifier.size(cornerThickness, cornerSize).background(cornerColor).align(Alignment.BottomStart))
        }
        // Canto inferior-direito
        Box(Modifier.align(Alignment.BottomEnd)) {
            Box(Modifier.size(cornerSize, cornerThickness).background(cornerColor).align(Alignment.BottomEnd))
            Box(Modifier.size(cornerThickness, cornerSize).background(cornerColor).align(Alignment.BottomEnd))
        }
    }
}

@Composable
private fun CameraPermissionDenied(
    showRationale: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = if (showRationale)
                    "A câmera é necessária para ler QR Codes.\nConceda a permissão para continuar."
                else
                    "Permissão de câmera negada permanentemente.\nAcesse as configurações para habilitá-la.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            if (showRationale) {
                Button(onClick = onRequestPermission) { Text("Conceder permissão") }
            } else {
                Button(onClick = onOpenSettings) { Text("Abrir configurações") }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

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
    val denial = if (state.result is ValidationResult.Denied) state.result.reason.toDisplayMessage() else ""
    return ResultArgs(isApproved, name, photo, denial)
}

private fun DenialReason.toDisplayMessage(): String = when (this) {
    DenialReason.INVALID_HMAC           -> "Assinatura inválida"
    DenialReason.EXPIRED                -> "Token expirado"
    DenialReason.FUTURE_TOKEN           -> "Token do futuro"
    DenialReason.WRONG_SYSTEM           -> "Sistema incorreto"
    DenialReason.DEVICE_NOT_WHITELISTED -> "Dispositivo não autorizado"
    DenialReason.NONCE_REPLAY           -> "Token já utilizado"
    DenialReason.EMPLOYEE_NOT_FOUND     -> "Funcionário não encontrado"
    DenialReason.SERVER_UNAVAILABLE     -> "Servidor indisponível"
    DenialReason.INVALID_FORMAT         -> "Formato inválido"
    DenialReason.RATE_LIMITED           -> "Muitas tentativas. Aguarde."
}
