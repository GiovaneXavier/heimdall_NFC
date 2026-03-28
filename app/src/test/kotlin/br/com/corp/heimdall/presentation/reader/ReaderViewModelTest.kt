package br.com.corp.heimdall.presentation.reader

import android.nfc.tech.IsoDep
import app.cash.turbine.test
import br.com.corp.heimdall.data.local.preferences.ConfigPreferences
import br.com.corp.heimdall.data.local.preferences.ConfigPreferences.Channel
import br.com.corp.heimdall.domain.model.DenialReason
import br.com.corp.heimdall.domain.model.EmployeeInfo
import br.com.corp.heimdall.domain.model.Token
import br.com.corp.heimdall.domain.model.ValidationResult
import br.com.corp.heimdall.domain.usecase.ParseTokenUseCase
import br.com.corp.heimdall.domain.usecase.ValidateLegacyTokenUseCase
import br.com.corp.heimdall.domain.usecase.ValidateNewTokenUseCase
import br.com.corp.heimdall.presentation.reader.nfc.NfcReaderHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private val nfcHelper: NfcReaderHelper = mockk()
    private val parseToken: ParseTokenUseCase = mockk()
    private val validateNew: ValidateNewTokenUseCase = mockk()
    private val validateLegacy: ValidateLegacyTokenUseCase = mockk()
    private val config: ConfigPreferences = mockk(relaxed = true)
    private val isoDep: IsoDep = mockk(relaxed = true)
    private lateinit var viewModel: ReaderViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val employee = EmployeeInfo("SRBR-001", "Ana Lima", "https://cdn.corp.br/photos/001.jpg")
    private val newToken = Token.New("dev-1", "SRBR-001", "ACCESS_HQ", 1711500000L, "abc", "sig")
    private val legacyToken = Token.Legacy("CORP", "1234")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { config.channel } returns Channel.NFC
        viewModel = ReaderViewModel(nfcHelper, parseToken, validateNew, validateLegacy, config)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onNfcTagDetected com token valido aprovado emite NavigateToResult Approved`() = runTest {
        every { nfcHelper.sendSelectApdu(isoDep) } returns "raw_token"
        every { parseToken("raw_token") } returns newToken
        coEvery { validateNew(newToken) } returns ValidationResult.Approved(employee)

        viewModel.onNfcTagDetected(isoDep)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ReaderUiState.NavigateToResult)
        val result = (state as ReaderUiState.NavigateToResult).result
        assertTrue(result is ValidationResult.Approved)
        assertEquals(employee, (result as ValidationResult.Approved).employee)
    }

    @Test
    fun `onNfcTagDetected com APDU retornando null emite NavigateToResult Denied INVALID_FORMAT`() = runTest {
        every { nfcHelper.sendSelectApdu(isoDep) } returns null

        viewModel.onNfcTagDetected(isoDep)
        advanceUntilIdle()

        val state = viewModel.uiState.value as ReaderUiState.NavigateToResult
        assertEquals(DenialReason.INVALID_FORMAT, (state.result as ValidationResult.Denied).reason)
    }

    @Test
    fun `onNfcTagDetected com token invalido emite NavigateToResult Denied`() = runTest {
        every { nfcHelper.sendSelectApdu(isoDep) } returns "bad"
        every { parseToken("bad") } returns Token.Invalid

        viewModel.onNfcTagDetected(isoDep)
        advanceUntilIdle()

        val state = viewModel.uiState.value as ReaderUiState.NavigateToResult
        assertEquals(DenialReason.INVALID_FORMAT, (state.result as ValidationResult.Denied).reason)
    }

    @Test
    fun `onQrDetected com token novo aprovado emite NavigateToResult Approved`() = runTest {
        every { parseToken("raw") } returns newToken
        coEvery { validateNew(newToken) } returns ValidationResult.Approved(employee)

        viewModel.onQrDetected("raw")
        advanceUntilIdle()

        val state = viewModel.uiState.value as ReaderUiState.NavigateToResult
        assertTrue(state.result is ValidationResult.Approved)
    }

    @Test
    fun `onQrDetected com token legado usa ValidateLegacyTokenUseCase`() = runTest {
        every { parseToken("CORP.1234") } returns legacyToken
        coEvery { validateLegacy(legacyToken) } returns ValidationResult.Approved(employee)

        viewModel.onQrDetected("CORP.1234")
        advanceUntilIdle()

        val state = viewModel.uiState.value as ReaderUiState.NavigateToResult
        assertTrue(state.result is ValidationResult.Approved)
    }

    @Test
    fun `chamadas durante Processing sao ignoradas`() = runTest {
        // Bloqueia a coroutine para simular Processing contínuo
        every { nfcHelper.sendSelectApdu(isoDep) } returns "raw"
        every { parseToken("raw") } returns newToken
        coEvery { validateNew(newToken) } coAnswers {
            kotlinx.coroutines.delay(10_000) // nunca completa neste teste
            ValidationResult.Approved(employee)
        }

        viewModel.onNfcTagDetected(isoDep)
        // Avança o suficiente para o estado mudar para Processing
        testDispatcher.scheduler.advanceTimeBy(1)

        assertTrue(viewModel.uiState.value is ReaderUiState.Processing)

        // Segunda leitura deve ser ignorada (não lança exceção, não muda estado)
        viewModel.onQrDetected("outro")
        testDispatcher.scheduler.advanceTimeBy(1)

        assertTrue(viewModel.uiState.value is ReaderUiState.Processing)
    }

    @Test
    fun `onAppBackground durante Processing retorna para Idle`() = runTest {
        // Simula uma tag detectada com mocks que deixam a coroutine suspensa
        every { nfcHelper.sendSelectApdu(isoDep) } returns "raw"
        every { parseToken("raw") } returns newToken
        coEvery { validateNew(newToken) } coAnswers {
            kotlinx.coroutines.delay(Long.MAX_VALUE) // nunca completa
            ValidationResult.Approved(employee)
        }

        viewModel.onNfcTagDetected(isoDep)
        testDispatcher.scheduler.advanceTimeBy(1)

        assertTrue(viewModel.uiState.value is ReaderUiState.Processing)
        viewModel.onAppBackground()
        assertEquals(ReaderUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `onResultConsumed retorna para Idle`() = runTest {
        every { nfcHelper.sendSelectApdu(isoDep) } returns "raw"
        every { parseToken("raw") } returns Token.Invalid

        viewModel.onNfcTagDetected(isoDep)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ReaderUiState.NavigateToResult)
        viewModel.onResultConsumed()
        assertEquals(ReaderUiState.Idle, viewModel.uiState.value)
    }

    // ── Modo QR ───────────────────────────────────────────────────────────────

    @Test
    fun `onQrDetected QR com token novo aprovado emite NavigateToResult Approved`() = runTest {
        every { parseToken("raw_qr") } returns newToken
        coEvery { validateNew(newToken) } returns ValidationResult.Approved(employee)

        viewModel.onQrDetected("raw_qr")
        advanceUntilIdle()

        val state = viewModel.uiState.value as ReaderUiState.NavigateToResult
        assertTrue(state.result is ValidationResult.Approved)
        assertEquals(employee, (state.result as ValidationResult.Approved).employee)
    }

    @Test
    fun `onQrDetected com CORP ponto 1234 usa ValidateLegacyTokenUseCase`() = runTest {
        every { parseToken("CORP.1234") } returns legacyToken
        coEvery { validateLegacy(legacyToken) } returns ValidationResult.Approved(employee)

        viewModel.onQrDetected("CORP.1234")
        advanceUntilIdle()

        val state = viewModel.uiState.value as ReaderUiState.NavigateToResult
        assertTrue(state.result is ValidationResult.Approved)
    }

    @Test
    fun `onQrDetected com token invalido emite Denied INVALID_FORMAT`() = runTest {
        every { parseToken("garbage") } returns Token.Invalid

        viewModel.onQrDetected("garbage")
        advanceUntilIdle()

        val state = viewModel.uiState.value as ReaderUiState.NavigateToResult
        assertEquals(DenialReason.INVALID_FORMAT, (state.result as ValidationResult.Denied).reason)
    }

    @Test
    fun `onQrDetected durante Processing e ignorado`() = runTest {
        every { parseToken("first") } returns newToken
        coEvery { validateNew(newToken) } coAnswers {
            kotlinx.coroutines.delay(Long.MAX_VALUE)
            ValidationResult.Approved(employee)
        }

        viewModel.onQrDetected("first")
        testDispatcher.scheduler.advanceTimeBy(1)

        assertTrue(viewModel.uiState.value is ReaderUiState.Processing)

        viewModel.onQrDetected("second")
        testDispatcher.scheduler.advanceTimeBy(1)

        assertTrue(viewModel.uiState.value is ReaderUiState.Processing)
    }

    @Test
    fun `channel NFC retornado corretamente`() {
        assertEquals(Channel.NFC, viewModel.channel)
    }

    @Test
    fun `channel QR retornado corretamente quando configurado`() {
        every { config.channel } returns Channel.QR
        val qrViewModel = ReaderViewModel(nfcHelper, parseToken, validateNew, validateLegacy, config)
        assertEquals(Channel.QR, qrViewModel.channel)
    }
}
