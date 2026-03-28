package br.com.corp.heimdall.presentation.reader.qr

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Analisador de frames da câmera que extrai QR Codes via ML Kit.
 *
 * Implementa [ImageAnalysis.Analyzer] para uso com CameraX.
 * Após cada leitura bem-sucedida, aplica debounce de [DEBOUNCE_MS] para evitar
 * leituras duplicadas do mesmo código durante a validação.
 *
 * @param onQrDetected Callback chamado com o valor bruto do QR Code detectado.
 * @param scanner      Instância de [BarcodeScanner] — injetável para testes.
 */
class QrAnalyzer(
    private val onQrDetected: (String) -> Unit,
    private val scanner: com.google.mlkit.vision.barcode.BarcodeScanner =
        BarcodeScanning.getClient(),
) : ImageAnalysis.Analyzer {
    private var lastDetectedMs = 0L

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastDetectedMs < DEBOUNCE_MS) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val qr = barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                val raw = qr?.rawValue
                if (!raw.isNullOrBlank()) {
                    lastDetectedMs = System.currentTimeMillis()
                    onQrDetected(raw)
                }
            }
            .addOnCompleteListener {
                // Sempre fechar o proxy — em todos os caminhos (sucesso, erro, cancelamento)
                imageProxy.close()
            }
    }

    companion object {
        /** Janela de debounce em milissegundos após cada leitura. */
        const val DEBOUNCE_MS = 3_000L
    }
}
