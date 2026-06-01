# Heimdall NFC

App Android nativo de controle de acesso corporativo. Instalado em tablets/smartphones dedicados em pontos de entrada, lê tokens de acesso via **NFC** ou **QR Code** emitidos pelo app **Huginn Mobile**, valida em tempo real contra o backend REST e exibe o resultado (verde/vermelho) com foto e nome do funcionário.

```
Odin Admin → Huginn Mobile → [NFC / QR] → Heimdall → Backend REST
```

Parte do ecossistema de controle de acesso SRBR (3 apps + módulo compartilhado).

---

## Documentação do ecossistema

| Documento | Conteúdo |
|---|---|
| [INTEGRATION_GUIDE.md](../INTEGRATION_GUIDE.md) | Contrato de comunicação: token, Base64Url, nonce, HMAC, vetores de teste |
| [BUILD_CICD.md](../BUILD_CICD.md) | Build, injeção de chaves HMAC, CI/CD, certificate pinning |
| [ARCHITECTURE.md](../ARCHITECTURE.md) | Arquitetura do ecossistema e papel deste leitor |

Documentação interna deste repositório: [Arquitetura Técnica](docs/ARCHITECTURE.md) · [Deploy e Administração](docs/DEPLOYMENT.md).

---

## Requisitos

| Item | Versão |
|------|--------|
| Android | API 28+ (Android 9) |
| Kotlin | 2.0.21 |
| AGP | 8.7.2 |
| JDK | 17 |

---

## Configuração rápida

### 1. Clone e `local.properties`

```bash
git clone git@github.com:GiovaneXavier/heimdall_NFC.git
cd heimdall_NFC
```

Crie `local.properties` na raiz (não é commitado):

```properties
sdk.dir=C\:\\Users\\<usuario>\\AppData\\Local\\Android\\Sdk
HEIMDALL_TOKEN_HMAC_KEY=sua_chave_secreta_aqui
HEIMDALL_BASE_URL=https://heimdall.corp.internal/
```

### 2. Build

```bash
# Debug
./gradlew assembleDebug

# Release
./gradlew assembleRelease

# Testes unitários
./gradlew testDebugUnitTest
```

---

## Fluxo de uso

1. **Primeira execução**: tela de setup → selecionar canal (NFC ou QR), inserir `systemId`, definir PIN de manutenção de 6 dígitos
2. **Operação normal**: tela de leitura ativa permanentemente
3. **NFC**: funcionário aproxima o celular → validação automática → resultado 3s → retorna ao modo de leitura
4. **QR**: câmera em preview contínuo → funcionário exibe QR → validação → resultado 3s

### Acesso de manutenção

5 toques rápidos no logo da tela de leitura abre o diálogo de manutenção (requer PIN). Permite:
- Alterar canal (NFC ↔ QR) e `systemId`
- Alterar PIN
- Ver log de últimos 30 acessos
- Ver `deviceId` e versão do app

---

## Documentação

- [Arquitetura Técnica](docs/ARCHITECTURE.md)
- [Guia de Deploy e Administração](docs/DEPLOYMENT.md)
- [Requisitos do Sistema](requisitos.md)
- [Backlog](backlog.md)

---

## CI

GitHub Actions executa `testDebugUnitTest` + `assembleDebug` em todo push para `main`, `dev`, `feat/**` e `bugfix/**`.

Status atual: **107 testes unitários passando**.
