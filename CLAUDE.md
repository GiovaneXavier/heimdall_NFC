# CLAUDE.md — Heimdall NFC

Guia de referência rápida para desenvolvimento com Claude Code.

---

## Comandos essenciais

```bash
# Build debug
JAVA_HOME="/c/Program Files/Android/Android Studio1/jbr" ./gradlew.bat assembleDebug

# Testes unitários (deve ser o primeiro comando antes de qualquer commit)
JAVA_HOME="/c/Program Files/Android/Android Studio1/jbr" ./gradlew.bat testDebugUnitTest --no-daemon

# Build release
JAVA_HOME="/c/Program Files/Android/Android Studio1/jbr" ./gradlew.bat assembleRelease

# Instalar no dispositivo conectado
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> No Windows com Android Studio, o JAVA_HOME aponta para o JBR bundled:
> `C:\Program Files\Android\Android Studio1\jbr`

---

## Configuração local (`local.properties`)

```properties
sdk.dir=C\:\\Users\\<usuario>\\AppData\\Local\\Android\\Sdk
HEIMDALL_TOKEN_HMAC_KEY=SRBR_HEIMDALL_TOKEN_SECRET_2024
HEIMDALL_BASE_URL=https://heimdall.corp.internal/
```

---

## Arquitetura em 3 linhas

**Clean Architecture + MVVM + Hilt.**
- `domain/` — lógica pura sem Android (use cases, modelos, interfaces de repositório)
- `data/` — implementações: Room+SQLCipher, Retrofit, sync WorkManager
- `presentation/` — Composables + ViewModels (um por tela)

Detalhes completos em [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## Decisões técnicas críticas

### NFC — AID proprietário
O Heimdall lê tags via `IsoDep` com AID `F0 53 52 42 52 00`. **Não é Web NFC**. A transação é feita com `IsoDep.transceive()` enviando o SELECT APDU. Os últimos 2 bytes da resposta (`90 00` = SW_OK) são descartados.

### HMAC — `java.util.Base64` (não `android.util.Base64`)
Use sempre `java.util.Base64.getUrlDecoder()` para decodificar a assinatura. `android.util.Base64` não está disponível nos testes JVM. O decoder URL rejeita nativamente os caracteres `+/` do Base64 padrão.

### PIN — SHA-256 com salt
O PIN é armazenado como `SHA-256(salt + PIN)` onde o salt é 16 bytes aleatórios. **Nunca armazenar SHA-256 puro** — vulnerável a rainbow tables.

### SQLCipher — chave gerada em runtime
A chave do banco é gerada com `SecureRandom` (32 bytes), codificada em Base64 e armazenada em `EncryptedSharedPreferences`. Nunca hardcoded.

### Campos `@Volatile` — leitura em variável local
Em `ReaderViewModel` e helpers com campos voláteis, sempre capturar o valor em variável local antes de usar mais de uma vez no mesmo bloco. Evita race conditions.

### Testes do QrAnalyzer — `mockkStatic`
`InputImage.fromMediaImage()` é método estático do ML Kit e não funciona em JVM. Usar `mockkStatic(InputImage::class)` antes de cada teste e `unmockkStatic` no `@After`.

---

## Versões estáveis validadas

```toml
agp            = "8.7.2"
kotlin         = "2.0.21"
hilt           = "2.52"
kotlin-compose = "2.0.21"    # DEVE ser igual ao kotlin
```

Não alterar essas versões sem testar — combinações diferentes causam falhas silenciosas no kapt/Hilt.

---

## Ordem de plugins em `app/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)      // ANTES do hilt
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}
```

---

## Artefatos Hilt corretos

```toml
# CORRETO
hilt-android   = { group = "com.google.dagger", name = "hilt-android",          version.ref = "hilt" }
hilt-compiler  = { group = "com.google.dagger", name = "hilt-android-compiler",  version.ref = "hilt" }

# ERRADO — não usar
# hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", ... }
```

---

## Gradle heap (necessário com ML Kit + Room + SQLCipher)

```properties
# gradle.properties
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8
```

---

## Workflow de branches

```
main          ← releases estáveis (merge via PR de dev)
  └── dev     ← integração contínua
        ├── feat/sprint-N     ← features por sprint
        └── bugfix/descricao  ← correções
```

Regra: **nenhum commit sem todos os testes passando**. O pre-commit hook bloqueia automaticamente.

---

## Estado atual do projeto (Sprints 1–5 concluídas)

| Sprint | Status | Branch merged |
|--------|--------|--------------|
| Sprint 1 — Fundação | ✅ | `feat/sprint-1` |
| Sprint 2 — Leitura NFC | ✅ | `feat/sprint-2` |
| Sprint 3 — Leitura QR | ✅ | `feat/sprint-3` |
| Sprint 4 — Backend + Audit log | ✅ | `feat/sprint-4` |
| Sprint 5 — Polimento + CI | ✅ | `feat/sprint-5` |
| Bugfix — Versões e heap | ✅ | `bugfix/versions-and-heap` |

**107 testes unitários passando.**
