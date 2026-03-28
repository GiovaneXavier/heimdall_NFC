package br.com.corp.heimdall.presentation.reader.qr

import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QrAnalyzerTest {

    private val detectedValues = mutableListOf<String>()
    private val scanner: BarcodeScanner = mockk(relaxed = true)
    private lateinit var analyzer: QrAnalyzer

    private val imageProxy: ImageProxy = mockk(relaxed = true)
    private val inputImage: InputImage = mockk(relaxed = true)
    private val mockTask: Task<List<Barcode>> = mockk(relaxed = true)

    @Before
    fun setup() {
        detectedValues.clear()
        analyzer = QrAnalyzer(onQrDetected = { detectedValues.add(it) }, scanner = scanner)

        mockkStatic(InputImage::class)
        every { InputImage.fromMediaImage(any(), any()) } returns inputImage

        val mediaImage = mockk<android.media.Image>(relaxed = true)
        every { imageProxy.image } returns mediaImage
        every { imageProxy.imageInfo } returns mockk<ImageInfo>(relaxed = true) {
            every { rotationDegrees } returns 0
        }
        every { scanner.process(any<InputImage>()) } returns mockTask
    }

    @After
    fun teardown() {
        unmockkStatic(InputImage::class)
    }

    // ── proxy.close() sempre chamado ─────────────────────────────────────────

    @Test
    fun `proxy fechado quando mediaImage e null`() {
        every { imageProxy.image } returns null

        analyzer.analyze(imageProxy)

        verify(exactly = 1) { imageProxy.close() }
    }

    @Test
    fun `proxy fechado apos sucesso com barcode detectado`() {
        val barcode = mockk<Barcode> {
            every { format } returns Barcode.FORMAT_QR_CODE
            every { rawValue } returns "token_value"
        }
        val successSlot = slot<OnSuccessListener<List<Barcode>>>()
        val completeSlot = slot<OnCompleteListener<List<Barcode>>>()
        every { mockTask.addOnSuccessListener(capture(successSlot)) } returns mockTask
        every { mockTask.addOnCompleteListener(capture(completeSlot)) } returns mockTask

        analyzer.analyze(imageProxy)
        successSlot.captured.onSuccess(listOf(barcode))
        completeSlot.captured.onComplete(mockTask)

        verify(exactly = 1) { imageProxy.close() }
        assertEquals(listOf("token_value"), detectedValues)
    }

    @Test
    fun `proxy fechado mesmo quando nenhum barcode detectado`() {
        val completeSlot = slot<OnCompleteListener<List<Barcode>>>()
        every { mockTask.addOnSuccessListener(any()) } returns mockTask
        every { mockTask.addOnCompleteListener(capture(completeSlot)) } returns mockTask

        analyzer.analyze(imageProxy)
        completeSlot.captured.onComplete(mockTask)

        verify(exactly = 1) { imageProxy.close() }
        assertTrue(detectedValues.isEmpty())
    }

    // ── Debounce ──────────────────────────────────────────────────────────────

    @Test
    fun `segundo barcode dentro de DEBOUNCE_MS nao chama callback`() {
        val barcode = mockk<Barcode> {
            every { format } returns Barcode.FORMAT_QR_CODE
            every { rawValue } returns "valor"
        }
        val successSlot = slot<OnSuccessListener<List<Barcode>>>()
        val completeSlot = slot<OnCompleteListener<List<Barcode>>>()
        every { mockTask.addOnSuccessListener(capture(successSlot)) } returns mockTask
        every { mockTask.addOnCompleteListener(capture(completeSlot)) } returns mockTask

        // Primeira leitura
        analyzer.analyze(imageProxy)
        successSlot.captured.onSuccess(listOf(barcode))
        completeSlot.captured.onComplete(mockTask)
        assertEquals(1, detectedValues.size)

        // Segunda leitura imediata — dentro do debounce → proxy fechado, callback NÃO chamado
        analyzer.analyze(imageProxy)
        verify(exactly = 2) { imageProxy.close() }
        assertEquals(1, detectedValues.size)
    }

    @Test
    fun `leitura apos debounce expirado chama callback novamente`() {
        // Força debounce expirado
        val field = QrAnalyzer::class.java.getDeclaredField("lastDetectedMs")
        field.isAccessible = true
        field.setLong(analyzer, System.currentTimeMillis() - QrAnalyzer.DEBOUNCE_MS - 500)

        val barcode = mockk<Barcode> {
            every { format } returns Barcode.FORMAT_QR_CODE
            every { rawValue } returns "novo_valor"
        }
        val successSlot = slot<OnSuccessListener<List<Barcode>>>()
        val completeSlot = slot<OnCompleteListener<List<Barcode>>>()
        every { mockTask.addOnSuccessListener(capture(successSlot)) } returns mockTask
        every { mockTask.addOnCompleteListener(capture(completeSlot)) } returns mockTask

        analyzer.analyze(imageProxy)
        successSlot.captured.onSuccess(listOf(barcode))
        completeSlot.captured.onComplete(mockTask)

        assertEquals(listOf("novo_valor"), detectedValues)
    }
}
