# Heimdall — Backlog de Desenvolvimento
**Versão:** 1.0.0
**Metodologia:** Sprints de 2 semanas
**Estimativas:** Fibonacci (1, 2, 3, 5, 8 pontos)
**Total estimado:** ~55 pontos / 5 sprints (~11 semanas)

---

## Definition of Done Global

Todo item concluído deve satisfazer **todos** os critérios antes do commit:

1. **Código implementado** seguindo Clean Architecture + MVVM + Hilt conforme `requisitos.md`
2. **Testes unitários escritos e passando** para toda lógica nas camadas Domain e Core (≥ 80% cobertura nas classes novas)
3. **Sem regressão** — todos os testes existentes continuam passando
4. **Sem warnings de lint** (`./gradlew lint` sem erros ERROR)
5. **Build release limpo** — `./gradlew assembleRelease` com R8 sem erros
6. **Sem logs com dados sensíveis** (tokens, HMAC, PIN) em código de produção
7. **KDoc** em todas as classes e funções públicas de Domain e Core

### ⚠️ Regra de Commit Obrigatória

> **Nenhum commit é aceito se algum teste unitário estiver falhando.**

Hook de pre-commit (`.git/hooks/pre-commit`):
```bash
#!/bin/sh
./gradlew testDebugUnitTest --daemon -q || { echo "❌ Testes falhando — commit bloqueado"; exit 1; }
```

Workflow de PR: branch com testes falhando não pode ser mergeada em `dev`.

---

## Sprint 1 — Fundação e Validação de Token
**Duração:** 2 semanas
**Objetivo:** Projeto compilando, Hilt configurado, toda a lógica de validação do token novo (HMAC, timestamp, systemId, parse) coberta por testes unitários. Sem UI ainda.
**Branch:** `feat/sprint-1-foundation`

---

### T-01 — Setup do projeto Android `[3 pts]`
**Descrição:**
- Criar projeto com package `br.com.corp.heimdall`, `minSdk 28`, `targetSdk 35`
- Configurar `libs.versions.toml` (Kotlin 2.0, AGP 8.4, Compose BOM, Hilt 2.51, Retrofit 2.11, Room 2.6, CameraX 1.3, ML Kit Barcode 17.3, Coil 2.6, Jetpack Security 1.1, MockK, Turbine)
- `BuildConfig.TOKEN_HMAC_KEY` via `local.properties` → `buildConfigField`
- R8 full mode em release; ProGuard rules para Retrofit + Room + Hilt
- `HeimdallApplication.kt` com `@HiltAndroidApp`
- Estrutura de pacotes completa conforme `requisitos.md` seção 5.2
- Hook de pre-commit instalado
- `.gitignore` com `local.properties`

**DoD:** `./gradlew assembleDebug`, `assembleRelease` e `testDebugUnitTest` executam sem erros.
**Testes:** N/A (infra)

---

### T-02 — Domain: modelos de domínio `[2 pts]`
**Descrição:**
Implementar em `domain/model/`:
- `Token.kt` — sealed class:
  ```kotlin
  sealed class Token {
      data class New(val deviceId: String, val employeeId: String,
                     val systemId: String, val timestamp: Long,
                     val nonce: String, val hmac: String) : Token()
      data class Legacy(val prefix: String, val code: String) : Token()
      data object Invalid : Token()
  }
  ```
- `ValidationResult.kt` — sealed class:
  ```kotlin
  sealed class ValidationResult {
      data class Approved(val employee: EmployeeInfo) : ValidationResult()
      data class Denied(val reason: DenialReason) : ValidationResult()
  }
  enum class DenialReason { INVALID_HMAC, EXPIRED, FUTURE_TOKEN, WRONG_SYSTEM,
                             DEVICE_NOT_WHITELISTED, NONCE_REPLAY, EMPLOYEE_NOT_FOUND,
                             SERVER_UNAVAILABLE, INVALID_FORMAT }
  ```
- `EmployeeInfo.kt` — `data class EmployeeInfo(val id: String, val name: String, val photoUrl: String)`

**DoD:** Classes criadas, compilam, sem dependências Android.
**Testes:** Testes de igualdade e copy nos data classes (1 arquivo de teste, ≥ 5 casos).

---

### T-03 — Core: `HmacValidator` `[3 pts]`
**Descrição:**
`core/crypto/HmacValidator.kt`:
```kotlin
class HmacValidator {
    fun verify(key: String, data: String, expectedBase64Url: String): Boolean
}
```
- Usa `javax.crypto.Mac("HmacSHA256")`
- Decodifica `expectedBase64Url` de Base64url sem padding
- Comparação em tempo constante com `MessageDigest.isEqual()`
- Nunca lança exceção para o caller — retorna `false` em caso de erro interno

**DoD:** Implementado, injetável via Hilt (`@Singleton`).
**Testes obrigatórios** (`HmacValidatorTest`):
- Vetor cross-platform: mesmo valor do Huginn (`eSJdyv9cvTX3kst9JbzhJJoiD_P60Svb2UhaXPgVbBE` para `TEST_SECRET_KEY` + payload padrão)
- HMAC correto → retorna `true`
- HMAC incorreto (1 byte diferente) → retorna `false`
- HMAC com padding `=` → retorna `true` (tolerância)
- HMAC com `+/` em vez de `-_` → retorna `false`
- Chave vazia → retorna `false`
- Payload vazio → retorna `false`

---

### T-04 — Core: `TimeProvider` `[1 pt]`
**Descrição:**
Interface `core/util/TimeProvider.kt` + implementação real:
```kotlin
interface TimeProvider {
    fun nowSeconds(): Long
}
class SystemTimeProvider : TimeProvider {
    override fun nowSeconds() = System.currentTimeMillis() / 1000L
}
```
Injetar via Hilt como `@Singleton`. Permite mock nos testes de ValidateNewTokenUseCase.

**DoD:** Interface + implementação criadas, módulo Hilt atualizado.
**Testes:** `SystemTimeProvider` retorna valor razoável (> 1_700_000_000L).

---

### T-05 — Domain: `ParseTokenUseCase` `[3 pts]`
**Descrição:**
`domain/usecase/ParseTokenUseCase.kt`:
- Parse do formato novo: split por `.` (exatamente 1), parte antes com exatamente 4 `|`
- Parse do formato legado: regex `^(CORP|PART)\.\d+$`
- Retorna `Token.New`, `Token.Legacy` ou `Token.Invalid`

**DoD:** Use case implementado, sem dependências Android ou de rede.
**Testes obrigatórios** (`ParseTokenUseCaseTest`):
- Token novo válido → `Token.New` com campos corretos
- Token novo sem `.` → `Token.Invalid`
- Token novo com 3 pipes → `Token.Invalid`
- Token novo com 5 pipes → `Token.Invalid`
- `CORP.1234` → `Token.Legacy(prefix="CORP", code="1234")`
- `PART.5678` → `Token.Legacy(prefix="PART", code="5678")`
- `CORP.abc` (não numérico) → `Token.Invalid`
- String vazia → `Token.Invalid`
- String aleatória → `Token.Invalid`
- Token novo com todos os campos vazios → `Token.Invalid`

---

### T-06 — Domain: `ValidateNewTokenUseCase` (etapas locais) `[5 pts]`
**Descrição:**
`domain/usecase/ValidateNewTokenUseCase.kt`:
- Etapa 1: HMAC via `HmacValidator.verify()`
- Etapa 2: janela temporal `|token.timestamp - timeProvider.nowSeconds()| ≤ 30`
- Etapa 3: `token.systemId == configuredSystemId`
- Etapa 4: delega para `ValidationRepository.validate(token)` (interface — mock nos testes desta task)
- Retorna `ValidationResult`

**DoD:** Use case implementado, dependências injetadas via Hilt.
**Testes obrigatórios** (`ValidateNewTokenUseCaseTest`):
- HMAC inválido → `Denied(INVALID_HMAC)` sem chamar o repositório
- Timestamp 31s no passado → `Denied(EXPIRED)` sem chamar o repositório
- Timestamp 31s no futuro → `Denied(FUTURE_TOKEN)` sem chamar o repositório
- systemId errado → `Denied(WRONG_SYSTEM)` sem chamar o repositório
- Todas as etapas locais OK + repositório retorna `Approved` → `Approved`
- Todas as etapas locais OK + repositório retorna `Denied(NONCE_REPLAY)` → `Denied(NONCE_REPLAY)`
- Todas as etapas locais OK + repositório lança timeout → `Denied(SERVER_UNAVAILABLE)`

---

### T-07 — Core: `DeviceIdProvider` e `SecurePreferences` `[2 pts]`
**Descrição:**
- `SecurePreferences.kt`: wrapper sobre `EncryptedSharedPreferences`
- `DeviceIdProvider.kt`: gera UUID v4 na primeira execução, persiste, sempre retorna o mesmo valor

**DoD:** Implementados, injetáveis via Hilt.
**Testes:** `DeviceIdProvider` retorna mesmo UUID em chamadas consecutivas (usando Robolectric + `EncryptedSharedPreferences` mock ou in-memory).

---

**Entregável Sprint 1:** Commit + push de `feat/sprint-1-foundation`. PR → `dev` com todos os testes passando. Mensagem do PR: `feat(sprint-1): foundation, token parsing and local validation logic`.

---

## Sprint 2 — Leitura NFC
**Duração:** 2 semanas
**Objetivo:** Fluxo completo de leitura NFC funcionando no dispositivo, desde o tap até a navegação para a tela de resultado (mockada). Setup screen e navegação básica.
**Branch:** `feat/sprint-2-nfc`

---

### T-08 — Navegação e tela de Setup `[5 pts]`
**Descrição:**
- `NavGraph.kt` com 3 rotas: `setup`, `reader`, `result/{isApproved}/{employeeName}/{photoUrl}/{denialReason}`
- `SetupScreen.kt` + `SetupViewModel.kt`:
  - Seleção de canal (NFC ou QR), campo `systemId`, campo PIN de manutenção (6 dígitos, confirmação)
  - Validação: `systemId` não vazio, PIN confirmado
  - Salva via `ConfigPreferences` (DataStore ou `EncryptedSharedPreferences`)
  - Ao salvar, navega para `reader`
- `ConfigPreferences.kt`: persiste `channel`, `systemId`, hash do PIN (SHA-256 do PIN + salt)
- `MainActivity.kt`: verifica se já configurado; redireciona para `setup` ou `reader`

**DoD:** Tela de setup funcional, configuração persiste entre sessões.
**Testes:** `SetupViewModel` — campo vazio não salva, PIN divergente não salva, dados válidos → `navigateToReader` emitido.

---

### T-09 — NFC: `NfcReaderHelper` e `MainActivity` dispatch `[5 pts]`
**Descrição:**
- `NfcReaderHelper.kt`:
  - `fun sendSelectApdu(isoDep: IsoDep): String?` — envia SELECT com AID `F05352425200`, retorna payload decodificado (remove os 2 bytes de status `SW_OK 90 00` do final)
  - Trata `IOException`, `TagLostException` → retorna null
- `MainActivity.kt`:
  - `enableForegroundDispatch()` em `onResume`, `disableForegroundDispatch()` em `onPause`
  - `onNewIntent()` detecta `ACTION_TECH_DISCOVERED` com `IsoDep`
  - Dispara `ReaderViewModel.onNfcTagDetected(isoDep)`

**DoD:** App detecta tag NFC em foreground e extrai string do token.
**Testes:** `NfcReaderHelper` com `IsoDep` mockado — resposta com `SW_OK` retorna payload correto; resposta sem `SW_OK` retorna null; `TagLostException` retorna null.

---

### T-10 — `ReaderViewModel` (NFC mode) `[3 pts]`
**Descrição:**
`presentation/reader/ReaderViewModel.kt`:
- `UiState`: `sealed class` com `Idle`, `Processing`, `NavigateToResult(result: ValidationResult, employee: EmployeeInfo?)`
- `fun onNfcTagDetected(isoDep: IsoDep)`: extrai token via `NfcReaderHelper`, chama `ParseTokenUseCase`, chama `ValidateNewTokenUseCase` ou `ValidateLegacyTokenUseCase`, emite `NavigateToResult`
- Ignora novas tags enquanto em `Processing`

**DoD:** ViewModel integrado, emite estados corretos.
**Testes:** `onNfcTagDetected` com token novo válido (repositório mockado como `Approved`) → `NavigateToResult(Approved)`; token inválido → `NavigateToResult(Denied)`.

---

### T-11 — `ReaderScreen` (NFC mode) `[3 pts]`
**Descrição:**
`presentation/reader/ReaderScreen.kt` para modo NFC:
- Estado `Idle`: ícone NFC animado centralizado, texto "Aproxime o celular"
- Estado `Processing`: `CircularProgressIndicator`
- Coleta `NavigateToResult` e navega para `result`
- `DisposableEffect` para `FLAG_KEEP_SCREEN_ON`
- Logo com detector de 5 toques rápidos → abre `MaintenanceDialog`

**DoD:** Screen compilando, preview funcional no Android Studio.
**Testes:** `ReaderViewModel` com estado `NavigateToResult` → Compose Test verifica navegação chamada.

---

### T-12 — `MaintenanceDialog` (PIN) `[2 pts]`
**Descrição:**
- Diálogo com campo de PIN numérico de 6 dígitos
- Verifica hash SHA-256 do PIN contra o armazenado
- Máximo de 5 tentativas; bloqueia por 60s após esgotar
- Se correto: exibe opções (alterar canal, alterar systemId, ver deviceId/versão)

**DoD:** Diálogo funcional, bloqueio por tentativas funcionando.
**Testes:** `MaintenanceViewModel` — PIN correto → `unlocked`, PIN errado 5x → `lockedUntil` no futuro.

---

**Entregável Sprint 2:** Commit + push de `feat/sprint-2-nfc`. PR → `dev`. Mensagem: `feat(sprint-2): NFC reader, setup screen and navigation`.

---

## Sprint 3 — Leitura QR
**Duração:** 2 semanas
**Objetivo:** Fluxo completo de leitura QR (Huginn novo + legado) funcionando, câmera em preview contínuo, distinção automática de formatos.
**Branch:** `feat/sprint-3-qr`

---

### T-13 — CameraX + ML Kit: `QrAnalyzer` `[5 pts]`
**Descrição:**
`presentation/reader/qr/QrAnalyzer.kt` implementando `ImageAnalysis.Analyzer`:
- `BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())`
- Callback `onQrDetected: (String) -> Unit` chamado com o raw value
- Debounce: ignora leituras por 3s após um resultado (evita leituras duplicadas)
- `proxy.close()` em todo caminho (inclusive erros)
- `QrReaderComposable.kt`: `AndroidView` com `PreviewView` do CameraX, usa `ProcessCameraProvider`, bind com `ImageAnalysis`

**DoD:** Câmera abre, detecta QR real em dispositivo físico.
**Testes:** `QrAnalyzer` com `InputImage` mockado — barcode detectado → callback chamado; segundo barcode em < 3s → callback NÃO chamado; `proxy.close()` sempre chamado.

---

### T-14 — `ReaderViewModel` (QR mode) `[3 pts]`
**Descrição:**
Adicionar ao `ReaderViewModel` o modo QR:
- `fun onQrDetected(raw: String)`: mesma lógica de `onNfcTagDetected` mas recebe string diretamente
- Ignora chamadas durante `Processing`
- Retoma leitura após `NavigateToResult` ser consumido

**DoD:** ViewModel trata ambos os modos.
**Testes:** `onQrDetected` com token novo → `NavigateToResult(Approved)`; `onQrDetected` com `CORP.1234` → usa `ValidateLegacyTokenUseCase`; chamadas durante `Processing` ignoradas.

---

### T-15 — `ReaderScreen` (QR mode) `[3 pts]`
**Descrição:**
`ReaderScreen.kt` adaptado para QR:
- Quando canal = QR: exibe `QrReaderComposable` como background full-screen
- Overlay com marca de mira centralizada
- Texto "Aponte o QR para a câmera" no topo
- Estado `Processing`: overlay semitransparente com `CircularProgressIndicator`
- Permissão de câmera: `rememberLauncherForActivityResult` com `RequestPermission`; se negada, exibe mensagem e botão de configurações

**DoD:** Câmera exibe preview, QR detectado navega para resultado.
**Testes:** Permissão negada → mensagem exibida (Compose Test).

---

### T-16 — `ValidateLegacyTokenUseCase` `[3 pts]`
**Descrição:**
`domain/usecase/ValidateLegacyTokenUseCase.kt`:
- Recebe `Token.Legacy`
- Chama `ValidationRepository.validateLegacy(token)`
- Retorna `ValidationResult`

**DoD:** Use case implementado.
**Testes:** Repositório retorna `Approved` → `Approved`; repositório retorna `Denied` → `Denied`; repositório lança timeout → `Denied(SERVER_UNAVAILABLE)`.

---

**Entregável Sprint 3:** Commit + push de `feat/sprint-3-qr`. PR → `dev`. Mensagem: `feat(sprint-3): QR reader with new and legacy token support`.

---

## Sprint 4 — Backend, Resultado e Auditoria
**Duração:** 2 semanas
**Objetivo:** Integração real com o backend REST, tela de resultado completa (verde/vermelho + som + auto-dismiss), log de auditoria em Room.
**Branch:** `feat/sprint-4-backend-result`

---

### T-17 — Retrofit: `HeimdallApi` e `LegacyApi` `[3 pts]`
**Descrição:**
- `HeimdallApi.kt`:
  ```kotlin
  @POST("/api/v1/access/validate")
  suspend fun validate(@Header("X-Device-Id") deviceId: String,
                       @Body request: TokenValidationRequest): TokenValidationResponse
  ```
- `LegacyApi.kt`:
  ```kotlin
  @GET("/api/legacy/card/{token}")
  suspend fun validateLegacy(@Path("token") token: String): LegacyValidationResponse
  ```
- DTOs com `@Serializable` (Kotlin Serialization)
- `AuthInterceptor.kt`: adiciona `X-Device-Id` em todas as requisições
- `NetworkModule.kt`: OkHttp com timeout 5s, logging interceptor (apenas em debug)

**DoD:** Módulo Hilt de rede configurado, DTOs compilam.
**Testes:** Serialização/deserialização dos DTOs com JSON de exemplo (usando `kotlinx.serialization`).

---

### T-18 — `ValidationRepositoryImpl` `[5 pts]`
**Descrição:**
`data/repository/ValidationRepositoryImpl.kt` implementando `ValidationRepository`:
- `validate(token: NewToken)`: chama `HeimdallApi.validate()`, mapeia resposta para `ValidationResult`
- `validateLegacy(token: LegacyToken)`: chama `LegacyApi.validateLegacy()`, mapeia resposta
- Trata `HttpException` (400, 401, 503), `SocketTimeoutException`, `IOException` → `Denied(SERVER_UNAVAILABLE)`
- Mapeia `reason` do backend para `DenialReason` enum

**DoD:** Repositório implementado, Hilt binding configurado.
**Testes** (com MockWebServer):
- Resposta `authorized: true` → `Approved` com `EmployeeInfo` correto
- Resposta `authorized: false, reason: NONCE_REPLAY` → `Denied(NONCE_REPLAY)`
- Timeout → `Denied(SERVER_UNAVAILABLE)`
- HTTP 503 → `Denied(SERVER_UNAVAILABLE)`
- HTTP 400 → `Denied(SERVER_UNAVAILABLE)`

---

### T-19 — `ResultScreen` e `ResultViewModel` `[5 pts]`
**Descrição:**
`presentation/result/ResultScreen.kt`:
- Recebe `ValidationResult` via nav argument (serializado)
- **Aprovado:** fundo verde, foto circular (Coil), nome do funcionário, ícone check animado
- **Negado:** fundo vermelho, ícone X animado, mensagem de erro mapeada para português
- `SoundPlayer.kt`: beep de aprovação (~880Hz, 200ms) e negação (~220Hz, 400ms) via `ToneGenerator`
- Auto-dismiss: `LaunchedEffect` com `delay(3000)` → `navController.popBackStack()`
- Se nova leitura chegar antes dos 3s (via `ReaderViewModel` shared), também faz dismiss

`ResultViewModel.kt`:
- Inicia countdown de 3s
- Expõe `UiState` com dados do resultado

**DoD:** Tela exibe verde/vermelho, som toca, auto-dismiss funciona.
**Testes:** `ResultViewModel` — após 3s emite `NavigateBack`; `SoundPlayer` mockado é chamado no `init`.

---

### T-20 — Room: `AuditLogDao` e `AuditRepositoryImpl` `[3 pts]`
**Descrição:**
- `AuditLogEntity.kt`: `@Entity` com id, timestamp, tokenType, employeeId, result, denialReason
- `AuditLogDao.kt`: `@Insert`, `@Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT 100")`, `@Query("DELETE FROM audit_log WHERE timestamp < :cutoff")`
- `HeimdallDatabase.kt`: `@Database` com `RoomDatabase()`, SQLCipher
- `AuditRepositoryImpl.kt`: salva após cada validação; purge de registros > 30 dias
- `SaveAuditLogUseCase.kt`: orquestra chamada ao repositório

**DoD:** Room compilando com SQLCipher; log persiste entre sessões.
**Testes** (Robolectric): insert → query retorna registro; purge remove registros antigos; registros recentes não são removidos.

---

**Entregável Sprint 4:** Commit + push de `feat/sprint-4-backend-result`. PR → `dev`. Mensagem: `feat(sprint-4): backend integration, result screen with sound and audit log`.

---

## Sprint 5 — Polimento, Segurança e Testes E2E
**Duração:** 2 semanas
**Objetivo:** App pronto para produção. Splash screen, reboot automático, testes instrumentados de fumaça, CLAUDE.md, apk release validado.
**Branch:** `feat/sprint-5-polish`

---

### T-21 — Splash screen e BOOT_COMPLETED `[2 pts]`
**Descrição:**
- `androidx.core:core-splashscreen`: `installSplashScreen()` antes de `super.onCreate()`; mantém splash até configuração carregada
- `BootReceiver.kt`: `BroadcastReceiver` com `BOOT_COMPLETED`, lança `MainActivity` com `FLAG_ACTIVITY_NEW_TASK`
- `AndroidManifest.xml`: declara receiver com `RECEIVE_BOOT_COMPLETED`
- `FLAG_KEEP_SCREEN_ON` em `MainActivity.onCreate()`

**DoD:** App abre em < 5s após reboot.
**Testes:** N/A (infra, verificado manualmente).

---

### T-22 — Tratamento completo de erros e estados de borda `[3 pts]`
**Descrição:**
- NFC não disponível no hardware → modo QR forçado automaticamente com toast informativo
- Permissão de câmera negada permanentemente → tela com botão "Abrir configurações"
- Backend retorna resposta inesperada (JSON malformado) → `Denied(SERVER_UNAVAILABLE)`
- App volta do background durante `Processing` → cancela e retorna ao `Idle`
- Dispositivo sem NFC tentando entrar em modo NFC → redireciona para setup com aviso

**DoD:** Todos os cenários acima testados manualmente e com testes unitários.
**Testes:** `ReaderViewModel` — `onAppBackground()` durante `Processing` → estado volta para `Idle`.

---

### T-23 — Segurança: hardening e ProGuard `[3 pts]`
**Descrição:**
- Verificar que `TOKEN_HMAC_KEY` não aparece em `strings.xml`, logs ou APK desmontado (usar `apktool`)
- Regras ProGuard: manter Hilt, Room, Retrofit, Kotlin Serialization; strip `Log.*` em release
- `FLAG_SECURE` em `MainActivity` (impede screenshots)
- Verificar que PIN hash usa SHA-256 com salt aleatório (não SHA-256 puro)
- Adicionar `android:allowBackup="false"` no `AndroidManifest.xml`

**DoD:** `./gradlew assembleRelease` + `apktool d` não revela `TOKEN_HMAC_KEY`; `FLAG_SECURE` ativo.
**Testes:** Teste unitário verificando que `HmacValidator` não loga a chave (verificação via mock do `Log`).

---

### T-24 — Testes instrumentados de fumaça `[5 pts]`
**Descrição:**
Testes instrumentados (`androidTest`) cobrindo os dois fluxos principais:

**Fluxo 1 — NFC aprovado (mock):**
- Mockar `ValidationRepository` via Hilt test module retornando `Approved`
- Simular `onNfcTagDetected` com token novo válido
- Verificar: tela verde exibida, nome do funcionário presente, dismiss após 3s

**Fluxo 2 — QR negado (mock):**
- Mockar repositório retornando `Denied(NONCE_REPLAY)`
- Simular `onQrDetected("deviceId|empId|sysId|ts|nonce.hmac")`
- Verificar: tela vermelha exibida, mensagem "Token já utilizado" presente, dismiss após 3s

**Fluxo 3 — Token legado (mock):**
- Simular `onQrDetected("CORP.1234")`
- Mockar `validateLegacy` retornando `Approved`
- Verificar: tela verde exibida

**DoD:** 3 testes instrumentados passando em emulador API 28+.
**Testes:** Os 3 fluxos acima.

---

### T-25 — `CLAUDE.md` e documentação final `[1 pt]`
**Descrição:**
Criar `CLAUDE.md` no root do projeto com:
- Comandos de build e teste
- Configuração de `local.properties` (template)
- Arquitetura resumida (referência ao `requisitos.md`)
- Decisões técnicas importantes (AID proprietário, SQLCipher, tempo constante no HMAC)
- Como registrar um novo dispositivo Heimdall no backend (passo a passo para o admin de TI)

Atualizar `backlog.md` e `requisitos.md` com quaisquer decisões tomadas durante o desenvolvimento.

**DoD:** `CLAUDE.md` revisado e aprovado em code review.
**Testes:** N/A

---

### T-26 — Validação final do APK release `[2 pts]`
**Descrição:**
- `./gradlew assembleRelease` com `TOKEN_HMAC_KEY` real (via `local.properties`)
- Verificar tamanho do APK (≤ 25 MB)
- Instalar em dispositivo físico com NFC e validar fluxo completo de ponta a ponta
- Instalar em dispositivo físico com câmera e validar fluxo QR completo
- Verificar que `adb logcat` em modo release não exibe tokens ou chaves

**DoD:** APK release ≤ 25 MB; fluxo completo validado em dispositivo físico.
**Testes:** Manual (checklist de CA-01 a CA-12 do `requisitos.md`).

---

**Entregável Sprint 5:** Commit + push de `feat/sprint-5-polish`. PR → `dev`. Mensagem: `feat(sprint-5): production hardening, smoke tests and release validation`.

---

## Resumo de Pontos por Sprint

| Sprint | Tasks | Pontos | Foco |
|--------|-------|--------|------|
| Sprint 1 | T-01 a T-07 | 19 pts | Fundação + validação de token |
| Sprint 2 | T-08 a T-12 | 18 pts | Leitura NFC + setup + navegação |
| Sprint 3 | T-13 a T-16 | 14 pts | Leitura QR + token legado |
| Sprint 4 | T-17 a T-20 | 16 pts | Backend + resultado + auditoria |
| Sprint 5 | T-21 a T-26 | 16 pts | Polimento + segurança + E2E |
| **Total** | **26 tasks** | **83 pts** | |

---

## Dependências entre Tasks

```
T-01 (setup)
  └─ T-02 (modelos)
       ├─ T-03 (HmacValidator)
       ├─ T-04 (TimeProvider)
       │    └─ T-06 (ValidateNewTokenUseCase)
       ├─ T-05 (ParseTokenUseCase)
       │    └─ T-06 → T-10, T-14
       ├─ T-07 (DeviceIdProvider)
       └─ T-08 (Setup + NavGraph)
            ├─ T-09 (NFC helper)
            │    └─ T-10 (ReaderViewModel NFC)
            │         └─ T-11 (ReaderScreen NFC)
            ├─ T-13 (QrAnalyzer)
            │    └─ T-14, T-15
            ├─ T-16 (ValidateLegacyTokenUseCase)
            │    └─ T-17 → T-18 (ValidationRepositoryImpl)
            │         └─ T-19 (ResultScreen)
            │              └─ T-20 (AuditLog)
            └─ T-21 a T-26 (polish)
```
