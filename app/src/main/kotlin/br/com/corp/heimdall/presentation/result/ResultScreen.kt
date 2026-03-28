package br.com.corp.heimdall.presentation.result

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ColorApproved = Color(0xFF4CAF50)
private val ColorDenied   = Color(0xFFF44336)

/**
 * Tela de resultado da validação do token.
 *
 * Exibe fundo verde/vermelho com animação de escala no ícone central e
 * emite um sinal sonoro via [ToneGenerator] (aprovado: tom agudo curto;
 * negado: dois bipes graves).
 *
 * Auto-dismiss após 3 segundos via [onDismiss].
 *
 * @param isApproved   `true` = aprovado (verde), `false` = negado (vermelho).
 * @param employeeName Nome do funcionário (apenas se aprovado).
 * @param photoUrl     URL da foto (apenas se aprovado).
 * @param denialReason Motivo de negação (apenas se negado).
 * @param onDismiss    Callback para retornar à tela de leitura.
 */
@Composable
fun ResultScreen(
    isApproved: Boolean,
    employeeName: String,
    photoUrl: String,
    denialReason: String,
    onDismiss: () -> Unit,
) {
    val iconScale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Animação de entrada (scale 0 → 1.15 → 1.0)
        launch {
            iconScale.animateTo(
                targetValue = 1.15f,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
            )
            iconScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 120),
            )
        }

        // Som de feedback
        launch { playFeedbackTone(isApproved) }

        // Auto-dismiss
        delay(3_000)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isApproved) ColorApproved else ColorDenied),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            if (isApproved) {
                if (photoUrl.isNotBlank() && photoUrl != "-") {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "Foto do funcionário",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape),
                    )
                    Spacer(Modifier.height(16.dp))
                }

                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Aprovado",
                    tint = Color.White,
                    modifier = Modifier
                        .size(80.dp)
                        .scale(iconScale.value),
                )
                Spacer(Modifier.height(16.dp))

                if (employeeName.isNotBlank() && employeeName != "-") {
                    Text(
                        text = employeeName,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "ACESSO AUTORIZADO",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.85f),
                    letterSpacing = 2.sp,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Negado",
                    tint = Color.White,
                    modifier = Modifier
                        .size(80.dp)
                        .scale(iconScale.value),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "ACESSO NEGADO",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = denialReason.ifBlank { "Acesso negado" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

/**
 * Emite tom de feedback usando [ToneGenerator].
 *
 * Aprovado: um bip agudo de 200 ms (TONE_PROP_BEEP).
 * Negado:   dois bips graves com intervalo (TONE_PROP_NACK).
 */
private fun playFeedbackTone(approved: Boolean) {
    val toneGen = try {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, ToneGenerator.MAX_VOLUME)
    } catch (_: RuntimeException) {
        return // hardware de áudio indisponível
    }

    try {
        if (approved) {
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            Thread.sleep(250)
        } else {
            toneGen.startTone(ToneGenerator.TONE_PROP_NACK, 150)
            Thread.sleep(200)
            toneGen.startTone(ToneGenerator.TONE_PROP_NACK, 150)
            Thread.sleep(200)
        }
    } finally {
        toneGen.release()
    }
}
