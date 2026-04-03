# Heimdall — Arquitetura Técnica

## Stack

| Camada | Tecnologia | Versão |
|--------|-----------|--------|
| Linguagem | Kotlin | 2.0.21 |
| UI | Jetpack Compose + Material 3 | BOM 2024.06 |
| Injeção de dependência | Hilt | 2.52 |
| Navegação | Compose Navigation | 2.7.7 |
| Assincronismo | Coroutines + Flow | 1.8.1 |
| Rede | Retrofit 2 + OkHttp 4 + Kotlin Serialization | 2.11.0 / 4.12.0 |
| Banco local | Room + SQLCipher | 2.6.1 / 4.5.4 |
| Segurança | EncryptedSharedPreferences (Jetpack Security) | 1.1.0-alpha06 |
| NFC | Android NFC API (`IsoDep`) | — |
| QR | CameraX + ML Kit Barcode Scanning | 1.3.4 / 17.3.0 |
| HMAC | `javax.crypto.Mac` (HmacSHA256) | — |
| Imagens | Coil Compose | 2.6.0 |
| Sync em background | WorkManager + Hilt-Work | 2.9.1 / 1.2.0 |
| Testes | JUnit 4 + MockK + Turbine + Robolectric | — |
| AGP | 8.7.2 | — |

---

## Estrutura de pacotes

```
br.com.corp.heimdall/
│
├── core/
│   ├── crypto/
│   │   └── HmacValidator.kt          HMAC-SHA256; comparação em tempo constante
│   ├── security/
│   │   ├── SecurePreferences.kt      Wrapper EncryptedSharedPreferences
│   │   └── DeviceIdProvider.kt       Gera/persiste UUID do dispositivo
│   └── util/
│       └── TimeProvider.kt           Interface + SystemTimeProvider; mock em testes
│
├── data/
│   ├── local/
│   │   ├── db/
│   │   │   ├── HeimdallDatabase.kt   Room + SQLCipher (chave gerada com SecureRandom)
│   │   │   ├── AccessLogEntry.kt     Entidade de auditoria
│   │   │   └── AccessLogDao.kt       insert, observeRecent, getEntriesSince, deleteOlderThan
│   │   └── preferences/
│   │       └── ConfigPreferences.kt  channel, systemId, PIN hash+salt, lastAuditSyncMs
│   ├── remote/
│   │   ├── api/
│   │   │   ├── HeimdallApi.kt        POST /api/v1/validate
│   │   │   ├── LegacyApi.kt          POST /api/v1/legacy-validate
│   │   │   └── AuditSyncApi.kt       POST /api/v1/audit-sync
│   │   └── dto/
│   │       ├── ValidateRequestDto.kt
│   │       ├── ValidateResponseDto.kt / EmployeeDto.kt
│   │       ├── LegacyValidateRequestDto.kt
│   │       └── AuditSyncDto.kt
│   ├── repository/
│   │   ├── ValidationRepositoryImpl.kt
│   │   └── AuditLogRepositoryImpl.kt
│   └── sync/
│       └── AuditSyncWorker.kt        @HiltWorker; sync periódico (15 min)
│
├── domain/
│   ├── model/
│   │   ├── Token.kt                  sealed: New | Legacy | Invalid
│   │   ├── ValidationResult.kt       sealed: Approved(employee) | Denied(reason)
│   │   ├── EmployeeInfo.kt           id, name, photoUrl
│   │   └── DenialReason.kt           enum com todos os motivos de negação
│   ├── repository/
│   │   ├── ValidationRepository.kt   interface
│   │   └── AuditLogRepository.kt     interface
│   └── usecase/
│       ├── ParseTokenUseCase.kt
│       ├── ValidateNewTokenUseCase.kt
│       └── ValidateLegacyTokenUseCase.kt
│
├── di/
│   ├── AppModule.kt                  HmacValidator, TimeProvider, ConfiguredSystemId
│   ├── NetworkModule.kt              OkHttp, Retrofit, Json, todas as APIs
│   ├── DatabaseModule.kt             Room+SQLCipher, AccessLogDao, AuditLogModule
│   ├── RepositoryModule.kt           ValidationRepositoryImpl → ValidationRepository
│   └── Qualifiers.kt                 @ConfiguredSystemId
│
├── presentation/
│   ├── setup/
│   │   ├── SetupScreen.kt
│   │   └── SetupViewModel.kt
│   ├── reader/
│   │   ├── ReaderScreen.kt           modo NFC (ícone pulsante) + modo QR (câmera full-screen)
│   │   ├── ReaderViewModel.kt        orquestra leitura → parse → validate → auditoria → UI
│   │   ├── nfc/
│   │   │   └── NfcReaderHelper.kt    IsoDep.transceive() com AID F05352425200
│   │   └── qr/
│   │       ├── QrAnalyzer.kt         ImageAnalysis.Analyzer; debounce 3s; BarcodeScanner injetável
│   │       └── QrReaderComposable.kt CameraX Preview + ImageAnalysis em AndroidView
│   ├── result/
│   │   └── ResultScreen.kt           animação scale; ToneGenerator; auto-dismiss 3s
│   └── maintenance/
│       ├── MaintenanceDialog.kt      5 steps: PIN → OPTIONS → CHANGE_CHANNEL / CHANGE_PIN / DEVICE_INFO / ACCESS_LOG
│       └── MaintenanceViewModel.kt   lockout 5 tentativas / 60s; recentLogs Flow
│
├── NavGraph.kt                       rotas: setup, reader, result/{args}
├── MainActivity.kt                   foreground NFC dispatch; kiosk mode; FLAG_SECURE
├── HeimdallApplication.kt            @HiltAndroidApp; HiltWorkerFactory; agenda AuditSyncWorker
└── BootReceiver.kt                   BOOT_COMPLETED → relança MainActivity
```

---

## Fluxo de dados principal

```
[Tag NFC detectada]                 [QR Code decodificado]
       │                                      │
       ▼                                      ▼
MainActivity.onNewIntent()       QrAnalyzer.onSuccess()
       │                                      │
       └──────────────┬───────────────────────┘
                      ▼
          ReaderViewModel (verifica se já está em Processing)
                      │
                      ▼
              ParseTokenUseCase
                      │
          ┌───────────┼──────────────┐
          ▼           ▼              ▼
      Token.New  Token.Legacy   Token.Invalid
          │           │              │
          ▼           ▼              └── Denied(INVALID_FORMAT)
  ValidateNew   ValidateLegacy
  UseCase       UseCase
    │                 │
    ├─ 1. HMAC        └─ ValidationRepository.validateLegacy()
    ├─ 2. Timestamp         [POST /api/v1/legacy-validate]
    ├─ 3. systemId
    └─ 4. ValidationRepository.validate()
         [POST /api/v1/validate]
                      │
                      ▼
              AuditLogRepository.log()  ← grava sempre, independente do resultado
                      │
                      ▼
          ReaderUiState.NavigateToResult
                      │
                      ▼
              ResultScreen (3s → onDismiss → Idle)
```

---

## Pipeline de validação local (Token.New)

As etapas 1–3 são **locais e sem rede**. Se qualquer uma falhar, o processo para imediatamente.

| Etapa | O que verifica | Motivo de falha |
|-------|---------------|-----------------|
| 1 | HMAC-SHA256 com `TOKEN_HMAC_KEY` | `INVALID_HMAC` |
| 2 | `\|timestamp - agora\| ≤ 30s` | `EXPIRED` ou `FUTURE_TOKEN` |
| 3 | `token.systemId == configuredSystemId` | `WRONG_SYSTEM` |
| 4 | Backend: whitelist, nonce, funcionário | vários (ver `DenialReason`) |

---

## Contratos de API

### POST /api/v1/validate

```json
// Request
{
  "device_id":    "uuid-do-huginn",
  "employee_id":  "SRBR-001",
  "system_id":    "ACCESS_HQ",
  "timestamp":    1711500000,
  "nonce":        "a3f8b2c1",
  "hmac":         "base64url-sem-padding"
}

// Response aprovado
{ "approved": true,  "employee": { "id": "SRBR-001", "name": "Ana Lima", "photo_url": "https://..." } }

// Response negado
{ "approved": false, "denial_reason": "NONCE_REPLAY" }
```

Mapeamento de HTTP codes: `401/403` → `DEVICE_NOT_WHITELISTED`, `404` → `EMPLOYEE_NOT_FOUND`, `409` → `NONCE_REPLAY`, `5xx` → `SERVER_UNAVAILABLE`.

### POST /api/v1/legacy-validate

```json
// Request
{ "prefix": "CORP", "code": "1234" }

// Response (mesmo schema de /validate)
```

### POST /api/v1/audit-sync

```json
// Request
{
  "device_id": "ACCESS_HQ",
  "entries": [
    {
      "id": 42,
      "timestamp_ms": 1711500000000,
      "employee_id": "SRBR-001",
      "employee_name": "Ana Lima",
      "channel": "NFC",
      "result": "APPROVED",
      "denial_reason": ""
    }
  ]
}

// Response
{ "received": 1 }
```

---

## Segurança

| Risco | Mitigação implementada |
|-------|----------------------|
| Token forjado | HMAC-SHA256 + comparação `MessageDigest.isEqual()` (tempo constante) |
| Replay de token | Janela ±30s (local) + nonce anti-replay no backend |
| Exfiltração da chave HMAC | `BuildConfig` (definida em `local.properties`); R8 obfuscation; nunca logada |
| Acesso físico indevido | PIN SHA-256 + salt aleatório 16 bytes; lockout 5 tentativas / 60s |
| MITM | HTTPS obrigatório (`cleartextTrafficPermitted=false` via Network Security Config) |
| Dados em disco | `EncryptedSharedPreferences` (AES-256-GCM) + Room SQLCipher (chave SecureRandom 32 bytes) |
| Screenshots | `FLAG_SECURE` em `MainActivity` |
| Exfiltração de dados locais | `android:allowBackup="false"` no manifesto |
| Device comprometido | Remover `deviceId` da whitelist no backend invalida imediatamente |

---

## Banco de dados local

### Tabela `access_log` (Room + SQLCipher)

| Coluna | Tipo | Descrição |
|--------|------|-----------|
| `id` | `INTEGER` PK | Auto-incremento |
| `timestamp_ms` | `INTEGER` | Unix em milissegundos |
| `employee_id` | `TEXT` | Matrícula do funcionário |
| `employee_name` | `TEXT` | Nome completo |
| `device_id` | `TEXT` | DeviceId do Huginn emissor |
| `channel` | `TEXT` | `"NFC"` ou `"QR"` |
| `result` | `TEXT` | `"APPROVED"` ou `"DENIED"` |
| `denial_reason` | `TEXT` | `DenialReason.name` ou `""` |

Chave de criptografia: gerada com `SecureRandom` (32 bytes), armazenada em `EncryptedSharedPreferences`.

---

## Testes

### Cobertura atual: 107 testes unitários

| Arquivo de teste | Casos |
|-----------------|-------|
| `HmacValidatorTest` | 7 |
| `ParseTokenUseCaseTest` | 10 |
| `ValidateNewTokenUseCaseTest` | 7 |
| `ValidateLegacyTokenUseCaseTest` | 3 |
| `DeviceIdProviderTest` | 2 |
| `TimeProviderTest` | 1 |
| `SetupViewModelTest` | 5 |
| `NfcReaderHelperTest` | 7 |
| `ReaderViewModelTest` | 15 |
| `MaintenanceViewModelTest` | 7 |
| `QrAnalyzerTest` | 5 |
| `ValidationRepositoryImplTest` | 11 |
| `AuditLogRepositoryImplTest` | 4 |

### Estratégia

- **Camada Domain/Core:** JUnit 4 + MockK puro (sem Android)
- **ViewModels:** `StandardTestDispatcher` + Turbine para flows; `mockk(relaxed = true)` para repositórios
- **QrAnalyzer:** `mockkStatic(InputImage::class)` — ML Kit não roda em JVM
- **ValidationRepositoryImpl:** MockK para APIs Retrofit; `Response.success()` / `Response.error()`
- **Sem testes instrumentados:** planejado para versão 1.1

---

## Dependências críticas — versões validadas

```toml
agp            = "8.7.2"
kotlin         = "2.0.21"
hilt           = "2.52"
kotlin-compose = "2.0.21"   # deve ser igual ao kotlin
```

> Combinações fora dessas versões podem causar falhas silenciosas no processamento kapt/Hilt.
