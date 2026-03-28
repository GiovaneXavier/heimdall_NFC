# Heimdall — Requisitos do Sistema
**Versão:** 1.0.0
**Data:** 2026-03-27
**Status:** Aprovado para desenvolvimento

---

## 1. Visão Geral e Objetivo

O Heimdall é um aplicativo Android nativo instalado em dispositivos fixos e dedicados (tablets ou smartphones) em pontos de acesso corporativos. Seu único propósito é ler tokens de acesso emitidos pelo app Huginn Mobile via NFC ou QR Code e validá-los em tempo real contra o backend REST corporativo, exibindo o resultado junto com foto e nome do funcionário.

O Heimdall encerra o fluxo do ecossistema de controle de acesso:

```
Odin Admin (emite QR de registro)
  └─ Huginn Mobile (lê QR, armazena credencial, gera token dinâmico)
       └─ Heimdall (lê token via NFC ou QR, valida com backend, exibe resultado)
```

O dispositivo funciona 24h/dia sem interação de operador. A configuração (canal e systemId) é feita uma única vez na primeira abertura.

---

## 2. Atores e Contexto de Uso

| Ator | Papel |
|------|-------|
| Funcionário | Aproxima o celular (NFC) ou exibe QR para liberar acesso |
| Administrador de TI | Configura o dispositivo Heimdall na primeira abertura; gerencia whitelist e systemId no backend |
| Backend REST | Valida deviceId, verifica anti-replay de nonce, retorna dados do funcionário |
| API Legada | Consultada apenas para tokens no formato antigo `CORP.XXXX` / `PART.XXXX` |
| Odin Admin | Emite o QR de registro que origina a credencial no Huginn |
| Huginn Mobile | Gera e apresenta o token dinâmico que o Heimdall lê |

**Contexto físico:** dispositivo Android dedicado, sem login de operador, tela sempre ativa na tela de leitura. Sem interação humana no fluxo normal de uso.

---

## 3. Requisitos Funcionais

### RF-01 — Configuração inicial do ponto de acesso
Na primeira execução o app apresenta tela de setup solicitando:
- Canal de leitura: NFC ou QR Code (seleção exclusiva)
- `systemId` do ponto de acesso (ex: `ACCESS_HQ`)
- PIN de manutenção de 6 dígitos (definido pelo admin de TI)

Após salvar, o app nunca retorna à tela de setup automaticamente. Acesso via PIN de manutenção permite reconfigurar.

### RF-02 — Tela principal de leitura (modo NFC)
Quando configurado para NFC:
- App entra em modo de escuta `IsoDep` continuamente via `enableForegroundDispatch`
- AID proprietário: `F05352425200` (ISO 7816-4, categoria `other`)
- Ao detectar tag NFC, envia SELECT com o AID, lê resposta e decodifica o token
- Inicia validação imediatamente

### RF-03 — Tela principal de leitura (modo QR)
Quando configurado para QR:
- CameraX em modo preview contínuo com ML Kit BarcodeScanning
- Detecta QR em tempo real sem botão de captura
- Ao decodificar string, inicia validação imediatamente
- Ignora leituras duplicadas durante intervalo de 3 segundos após validação

### RF-04 — Parse e distinção de formato de token

**Token novo (Huginn):**
Formato: `deviceId|employeeId|systemId|timestamp_unix|nonce.hmacBase64url`
- Contém exatamente um `.` separando payload e assinatura
- A parte antes do `.` contém exatamente 4 pipes `|`

**Token legado:**
Formato: `CORP.XXXX` ou `PART.XXXX` onde `XXXX` são dígitos

**Lógica de distinção:**
1. Tentar parse do formato novo; se estruturalmente válido → processar como novo
2. Senão, tentar parse do formato legado; se válido → processar como legado
3. Se ambos falharem → NEGADO com "Formato de token inválido"

### RF-05 — Validação do token novo (6 etapas, nesta ordem)

1. **HMAC-SHA256:** recalcular sobre a parte antes do `.` com `TOKEN_HMAC_KEY` (Base64url sem padding); comparar em tempo constante com `MessageDigest.isEqual()`
2. **Janela temporal:** `|timestamp_unix - agora_em_segundos| ≤ 30`; rejeitar fora da janela
3. **systemId:** comparar `systemId` do token com o configurado no dispositivo; rejeitar se diferente
4. **deviceId (whitelist) + nonce (anti-replay) + employeeId:** uma única chamada ao backend REST

As etapas 1–3 são locais e não tocam a rede. Se qualquer etapa falhar, o processo para.

### RF-06 — Validação do token legado
Enviar o token para a API legada. A API retorna nome, URL da foto e status (autorizado/não autorizado).

### RF-07 — Exibição do resultado — APROVADO
- Fundo verde (`#4CAF50`)
- Foto do funcionário circular (120dp)
- Nome em texto grande (24sp, negrito)
- Ícone de check animado
- Som de aprovação (beep curto ~200ms, ~880Hz)
- Auto-dismiss após 3 segundos OU nova leitura detectada (o que vier primeiro)
- Retorna à tela de leitura ativa

### RF-08 — Exibição do resultado — NEGADO
- Fundo vermelho (`#F44336`)
- Ícone X animado
- Mensagem de erro específica:
  - `"Assinatura inválida"` — HMAC incorreto
  - `"Token expirado"` / `"Token do futuro"` — timestamp fora da janela
  - `"Sistema incorreto"` — systemId não confere
  - `"Dispositivo não autorizado"` — deviceId fora da whitelist
  - `"Token já utilizado"` — nonce repetido
  - `"Funcionário não encontrado"` — employeeId inexistente
  - `"Servidor indisponível"` — timeout/erro de rede
  - `"Formato inválido"` — token não reconhecido
- Som de negação (buzzer ~400ms, ~220Hz)
- Auto-dismiss após 3 segundos OU nova leitura

### RF-09 — PIN de manutenção
5 toques rápidos no logo (< 2s) abre diálogo de PIN numérico de 6 dígitos. Com PIN correto o admin pode:
- Alterar canal (NFC ↔ QR)
- Alterar `systemId`
- Ver versão do app e `deviceId` do dispositivo
- Alterar o próprio PIN

Máximo de 5 tentativas erradas; bloqueia por 60s após esgotar.

### RF-10 — Modo offline degradado
Se o backend estiver inacessível (timeout 5s):
- Qualquer token → NEGADO com "Servidor indisponível"
- Não há validação offline (nonce anti-replay exige estado centralizado)

### RF-11 — Log de auditoria local
Registrar cada tentativa em banco Room local:
- Timestamp, tipo de token (novo/legado/inválido), `employeeId`, resultado, motivo
- Retenção de 30 dias; purge automático

### RF-12 — Geração e persistência do `deviceId` do Heimdall
Na primeira execução, gerar UUID v4 como `deviceId` do dispositivo. Persistir em `EncryptedSharedPreferences`. Este ID identifica o ponto de acesso no backend e deve ser registrado pelo admin de TI na whitelist após instalação.

---

## 4. Requisitos Não-Funcionais

| # | Requisito | Valor |
|---|-----------|-------|
| RNF-01 | Latência total (leitura → resultado) | ≤ 2s em LAN corporativa |
| RNF-02 | Tempo de inicialização após reboot | ≤ 5s (via `BOOT_COMPLETED`) |
| RNF-03 | Android mínimo | API 28 (Android 9) |
| RNF-04 | Target SDK | API 35 (Android 15) |
| RNF-05 | `TOKEN_HMAC_KEY` | Via `BuildConfig`; nunca em logs ou UI |
| RNF-06 | Armazenamento seguro | `EncryptedSharedPreferences` + Room SQLCipher |
| RNF-07 | Telemetria externa | Proibida (sem Firebase, Crashlytics, etc.) |
| RNF-08 | Tela sempre ativa | `FLAG_KEEP_SCREEN_ON` obrigatório |
| RNF-09 | Cobertura de testes | ≥ 80% nas camadas Domain e Core |
| RNF-10 | APK release | ≤ 25 MB, R8 full mode obrigatório |
| RNF-11 | Build determinístico | Version catalog; sem wildcards `+` |

---

## 5. Arquitetura Técnica

### 5.1 Stack

| Camada | Tecnologia |
|--------|-----------|
| UI | Jetpack Compose + Material 3 |
| Injeção de dependência | Hilt 2.51+ |
| Navegação | Compose Navigation |
| Assincronismo | Coroutines + Flow |
| Rede | Retrofit 2 + OkHttp 4 + Kotlin Serialization |
| Banco local | Room + SQLCipher |
| Segurança | EncryptedSharedPreferences (Jetpack Security 1.1) |
| NFC | Android NFC API (`IsoDep`) |
| QR | CameraX 1.3+ + ML Kit Barcode Scanning 17.3+ |
| HMAC | `javax.crypto.Mac` com `HmacSHA256` |
| Imagens | Coil 2 |
| Testes unitários | JUnit 4 + MockK + Turbine |
| Testes instrumentados | Espresso + Compose Test + Robolectric |

### 5.2 Estrutura de Pacotes

```
br.com.corp.heimdall/
├── core/
│   ├── crypto/
│   │   └── HmacValidator.kt          # HMAC-SHA256 + comparação tempo constante
│   ├── security/
│   │   ├── SecurePreferences.kt      # Wrapper EncryptedSharedPreferences
│   │   └── DeviceIdProvider.kt       # Gera/persiste UUID do dispositivo
│   └── util/
│       ├── TimeProvider.kt           # Interface; permite mock em testes
│       └── SoundPlayer.kt            # Beep aprovação/negação
│
├── data/
│   ├── local/
│   │   ├── db/
│   │   │   ├── HeimdallDatabase.kt
│   │   │   └── AuditLogDao.kt
│   │   ├── entity/
│   │   │   └── AuditLogEntity.kt
│   │   └── preferences/
│   │       └── ConfigPreferences.kt  # systemId, channel, PIN hash
│   ├── remote/
│   │   ├── api/
│   │   │   ├── HeimdallApi.kt        # Endpoints backend novo
│   │   │   └── LegacyApi.kt          # Endpoint API legada
│   │   ├── dto/
│   │   │   ├── TokenValidationRequest.kt
│   │   │   ├── TokenValidationResponse.kt
│   │   │   └── LegacyValidationResponse.kt
│   │   └── interceptor/
│   │       └── AuthInterceptor.kt    # Header X-Device-Id
│   └── repository/
│       ├── ValidationRepositoryImpl.kt
│       └── AuditRepositoryImpl.kt
│
├── domain/
│   ├── model/
│   │   ├── Token.kt                  # sealed class: NewToken | LegacyToken | InvalidToken
│   │   ├── ValidationResult.kt       # sealed class: Approved(employee) | Denied(reason)
│   │   └── EmployeeInfo.kt           # id, name, photoUrl
│   ├── repository/
│   │   ├── ValidationRepository.kt   # Interface
│   │   └── AuditRepository.kt        # Interface
│   └── usecase/
│       ├── ParseTokenUseCase.kt
│       ├── ValidateNewTokenUseCase.kt
│       ├── ValidateLegacyTokenUseCase.kt
│       └── SaveAuditLogUseCase.kt
│
├── presentation/
│   ├── setup/
│   │   ├── SetupScreen.kt
│   │   └── SetupViewModel.kt
│   ├── reader/
│   │   ├── ReaderScreen.kt
│   │   ├── ReaderViewModel.kt
│   │   ├── nfc/
│   │   │   └── NfcReaderHelper.kt    # IsoDep.transceive(), bytes → String
│   │   └── qr/
│   │       ├── QrReaderComposable.kt # CameraX preview
│   │       └── QrAnalyzer.kt         # ImageAnalysis.Analyzer ML Kit
│   ├── result/
│   │   ├── ResultScreen.kt
│   │   └── ResultViewModel.kt
│   └── maintenance/
│       ├── MaintenanceDialog.kt
│       └── MaintenanceViewModel.kt
│
├── di/
│   ├── NetworkModule.kt
│   ├── DatabaseModule.kt
│   ├── CryptoModule.kt
│   └── RepositoryModule.kt
│
├── NavGraph.kt
└── HeimdallApplication.kt
```

### 5.3 Fluxo de Dados Principal

```
[NFC tag detectada / QR string decodificada]
        │
        ▼
ReaderViewModel.onTokenRead(raw: String)
        │
        ▼
ParseTokenUseCase → Token (NewToken | LegacyToken | InvalidToken)
        │
        ├─ NewToken ──→ ValidateNewTokenUseCase
        │               ├─ 1. HmacValidator.verify()         [local]
        │               ├─ 2. TimeProvider.isWithinWindow()  [local]
        │               ├─ 3. systemId check                 [local]
        │               └─ 4. ValidationRepository.validate() [remoto: whitelist + nonce + employee]
        │
        ├─ LegacyToken → ValidateLegacyTokenUseCase
        │                └─ ValidationRepository.validateLegacy() [remoto]
        │
        └─ InvalidToken → Denied("Formato inválido")
                │
                ▼
        SaveAuditLogUseCase (sempre, independente do resultado)
                │
                ▼
        navigate to ResultScreen (auto-dismiss 3s ou nova leitura)
```

---

## 6. Contratos de API

### 6.1 Backend REST — Validação de token novo

**Request:**
```
POST /api/v1/access/validate
X-Device-Id: {heimdall-device-uuid}
Content-Type: application/json

{
  "device_id":    "uuid-do-huginn",
  "employee_id":  "SRBR-0042",
  "system_id":    "ACCESS_HQ",
  "timestamp":    1711500000,
  "nonce":        "a3f8b2c1",
  "hmac":         "base64url-sem-padding"
}
```

**Resposta aprovada (200):**
```json
{
  "authorized": true,
  "employee": {
    "id": "SRBR-0042",
    "name": "Ana Lima",
    "photo_url": "https://cdn.corp.br/photos/SRBR-0042.jpg"
  }
}
```

**Resposta negada (200):**
```json
{
  "authorized": false,
  "reason": "NONCE_REPLAY"
}
```

Razões possíveis: `NONCE_REPLAY` | `DEVICE_NOT_WHITELISTED` | `EMPLOYEE_NOT_FOUND` | `SYSTEM_MISMATCH`

**Erros HTTP:** 400 (payload inválido), 401 (heimdall não autenticado), 503 (indisponível). Timeout: 5s.

### 6.2 API Legada — Validação de token legado

```
GET /api/legacy/card/{token}

200 OK
{
  "authorized": true,
  "employee_name": "Carlos Souza",
  "photo_url": "https://legacy.corp.br/fotos/1234.jpg"
}
```

---

## 7. Segurança

| Risco | Mitigação |
|-------|-----------|
| Token forjado | HMAC-SHA256 com chave secreta; validação remota (nonce, whitelist) |
| Replay de token | Nonce anti-replay no backend + janela temporal ±30s |
| Exfiltração da chave HMAC | `BuildConfig` + R8 obfuscation; nunca logada |
| Acesso físico indevido | PIN de manutenção + máximo de tentativas |
| MITM | HTTPS obrigatório; certificate pinning recomendado (v2) |
| Dados em disco | `EncryptedSharedPreferences` + Room SQLCipher |
| Device comprometido | Remover `deviceId` da whitelist invalida imediatamente |

---

## 8. Critérios de Aceite Globais

- **CA-01:** Token novo válido → tela verde com foto + nome em ≤ 2s
- **CA-02:** HMAC inválido → rejeitado localmente, sem chamada de rede
- **CA-03:** Timestamp fora de ±30s → rejeitado localmente
- **CA-04:** `systemId` diferente → rejeitado localmente
- **CA-05:** Nonce repetido → rejeitado pelo backend
- **CA-06:** Token legado `CORP.1234` → consulta API legada e exibe resultado correto
- **CA-07:** Backend inacessível → tela vermelha "Servidor indisponível" para qualquer token
- **CA-08:** Auto-dismiss de 3s funciona para aprovado e negado; retorna à tela de leitura
- **CA-09:** App inicia e fica pronto para leitura em ≤ 5s após reboot
- **CA-10:** PIN incorreto 5x → bloqueio de 60s
- **CA-11:** `./gradlew testDebugUnitTest` passa com cobertura ≥ 80% em Domain e Core
- **CA-12:** APK release ≤ 25 MB com R8 full mode
