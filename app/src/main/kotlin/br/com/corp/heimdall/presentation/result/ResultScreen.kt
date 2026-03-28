package br.com.corp.heimdall.presentation.result

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

private val ColorApproved = Color(0xFF4CAF50)
private val ColorDenied = Color(0xFFF44336)

/**
 * Tela de resultado da validação do token.
 *
 * Exibe fundo verde/vermelho, foto e nome (se aprovado) ou mensagem de erro (se negado).
 * Auto-dismiss após 3 segundos via [onDismiss].
 *
 * Nota: implementação completa de som e animações será adicionada no Sprint 4 (T-19).
 *
 * @param isApproved    `true` = aprovado (fundo verde), `false` = negado (fundo vermelho).
 * @param employeeName  Nome do funcionário (apenas se aprovado).
 * @param photoUrl      URL da foto (apenas se aprovado).
 * @param denialReason  Mensagem de motivo de negação (apenas se negado).
 * @param onDismiss     Callback para retornar à tela de leitura.
 */
@Composable
fun ResultScreen(
    isApproved: Boolean,
    employeeName: String,
    photoUrl: String,
    denialReason: String,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(Unit) {
        delay(3000)
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
                    modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.height(16.dp))

                if (employeeName.isNotBlank() && employeeName != "-") {
                    Text(
                        text = employeeName,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Negado",
                    tint = Color.White,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = denialReason.ifBlank { "Acesso negado" },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                )
            }
        }
    }
}
