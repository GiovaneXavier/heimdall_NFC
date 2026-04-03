# Heimdall — Guia de Deploy e Administração

## Pré-requisitos

- Android Studio Hedgehog (2023.1.1) ou superior
- JDK 17
- Android SDK API 28–35
- Chave HMAC compartilhada com o backend (`HEIMDALL_TOKEN_HMAC_KEY`)
- URL do backend (`HEIMDALL_BASE_URL`)

---

## 1. Compilação do APK

### 1.1 Configurar `local.properties`

```properties
sdk.dir=C\:\\Users\\<usuario>\\AppData\\Local\\Android\\Sdk

# Chave HMAC — deve ser idêntica à usada pelo Huginn e pelo backend
HEIMDALL_TOKEN_HMAC_KEY=chave_secreta_producao

# URL base do backend (com barra no final)
HEIMDALL_BASE_URL=https://heimdall.corp.internal/
```

> `local.properties` está no `.gitignore` e **nunca deve ser commitado**.

### 1.2 Gerar APK de produção

```bash
./gradlew assembleRelease
```

APK gerado em: `app/build/outputs/apk/release/app-release.apk`

### 1.3 Assinar o APK

Configure a keystore em `app/build.gradle.kts` ou via variáveis de ambiente antes da geração do APK release. Consulte a documentação oficial Android para signing configs.

---

## 2. Instalação no dispositivo

### 2.1 Via ADB (implantação manual)

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

### 2.2 Via MDM (recomendado para produção)

Distribua o APK via solução MDM da empresa (ex: Microsoft Intune, VMware Workspace ONE). Configure o dispositivo como **Device Owner** para habilitar o modo kiosk automático (ver seção 4).

---

## 3. Configuração inicial do dispositivo

Na **primeira abertura** do app, a tela de Setup é exibida. O administrador de TI deve:

1. **Selecionar o canal**: NFC (para dispositivos com chip NFC) ou QR Code
2. **Inserir o `systemId`**: identificador do ponto de acesso (ex: `ACCESS_HQ`, `PORTARIA_01`). Deve corresponder ao `systemId` emitido nos tokens Huginn para este ponto de acesso
3. **Definir o PIN de manutenção**: 6 dígitos numéricos — confirmar uma segunda vez
4. Pressionar **Salvar**

Após o setup, o app vai direto para a tela de leitura e **nunca retorna ao setup automaticamente**.

### Registrar o `deviceId` no backend

Após a configuração inicial, o `deviceId` único do dispositivo Heimdall deve ser cadastrado na whitelist do backend. Para obtê-lo:

1. Na tela de leitura, toque **5 vezes rapidamente** no logo
2. Digite o PIN de manutenção
3. Selecione **Informações do dispositivo**
4. Copie o `Device ID` exibido
5. Registre esse UUID no backend via painel Odin Admin → Dispositivos Heimdall → Adicionar

> Sem o `deviceId` registrado, todos os tokens serão negados com `DEVICE_NOT_WHITELISTED`.

---

## 4. Modo kiosk (Device Owner)

O Heimdall tenta entrar em **lock task mode** automaticamente quando o app está configurado. Para que isso funcione, o dispositivo deve ter o Heimdall configurado como Device Owner.

### Configurar Device Owner via ADB (factory reset necessário)

```bash
# 1. Resetar o dispositivo para factory default
# 2. Não fazer login com conta Google
# 3. Instalar o APK
adb install app-release.apk

# 4. Definir o Heimdall como Device Owner
adb shell dpm set-device-owner br.com.corp.heimdall/.BootReceiver
```

> Após definir como Device Owner, `startLockTask()` bloqueia o usuário no app — não é possível sair sem o PIN de manutenção ou remoção via MDM.

### Kiosk via MDM

Se usar MDM (recomendado), configure o perfil de kiosk apontando para o pacote `br.com.corp.heimdall`. Dispensa o processo de Device Owner manual.

---

## 5. Auto-inicialização após reboot

O `BootReceiver` escuta `BOOT_COMPLETED` e relança o `MainActivity` automaticamente. Nenhuma configuração adicional é necessária além da permissão já declarada no manifesto.

Para testar:
```bash
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p br.com.corp.heimdall
```

---

## 6. Acesso de manutenção

### Abrir o diálogo de manutenção

Na tela de leitura, toque **5 vezes rapidamente** (< 2s) no logo do Heimdall.

### Opções disponíveis (após PIN correto)

| Opção | Descrição |
|-------|-----------|
| Alterar canal / System ID | Muda NFC ↔ QR e/ou atualiza o `systemId` |
| Alterar PIN | Define novo PIN de 6 dígitos |
| Log de acessos | Exibe os últimos 30 registros de acesso |
| Informações do dispositivo | Mostra `deviceId` e versão do app |

### Política de tentativas

- Máximo de **5 tentativas** erradas
- Após esgotar: **bloqueio de 60 segundos**
- Contador zera após acesso bem-sucedido

---

## 7. Sincronização do audit log

O `AuditSyncWorker` (WorkManager) sincroniza automaticamente os registros de auditoria com o backend:

- **Frequência**: a cada 15 minutos (mínimo garantido pelo WorkManager)
- **Condição**: apenas quando há conexão de rede (`CONNECTED`)
- **Endpoint**: `POST /api/v1/audit-sync`
- **Estratégia de falha**: `retry()` — tentará novamente no próximo ciclo

O timestamp da última sincronização bem-sucedida é armazenado em `EncryptedSharedPreferences`. Apenas registros **novos desde a última sincronização** são enviados.

---

## 8. Segurança em produção

### Network Security Config

Por padrão, o app **bloqueia tráfego HTTP** em produção (`cleartextTrafficPermitted=false`). O backend **deve servir HTTPS**.

Para adicionar certificate pinning (recomendado em v2), edite `app/src/main/res/xml/network_security_config.xml`:

```xml
<domain-config>
    <domain includeSubdomains="true">heimdall.corp.internal</domain>
    <pin-set>
        <pin digest="SHA-256">BASE64_DO_SPKI_HASH_PRIMARIO</pin>
        <pin digest="SHA-256">BASE64_DO_SPKI_HASH_BACKUP</pin>
    </pin-set>
</domain-config>
```

Para obter o hash SPKI do certificado do servidor:

```bash
openssl s_client -connect heimdall.corp.internal:443 | \
  openssl x509 -pubkey -noout | \
  openssl pkey -pubin -outform DER | \
  openssl dgst -sha256 -binary | \
  base64
```

### Chave HMAC

A `TOKEN_HMAC_KEY` é compilada no APK via `BuildConfig` e ofuscada pelo R8. Para rotacionar a chave:

1. Atualizar `HEIMDALL_TOKEN_HMAC_KEY` em `local.properties`
2. Recompilar e redistribuir o APK
3. Atualizar a chave no backend e no Huginn **simultaneamente** (janela de manutenção)

---

## 9. Monitoramento e diagnóstico

### Logs (apenas em debug)

```bash
adb logcat -s Heimdall:D OkHttp:D
```

Em builds **release**, todos os logs `Log.d`, `Log.v`, `Log.i` são removidos pelo ProGuard. Nenhum dado sensível é registrado.

### Verificar status do WorkManager

```bash
adb shell dumpsys jobscheduler | grep heimdall
```

### Forçar sync imediato (debug)

```bash
adb shell am broadcast -a androidx.work.impl.background.systemalarm.RescheduleReceiver \
  -p br.com.corp.heimdall
```

---

## 10. Atualização do app

1. Gerar novo APK com versão incrementada (`versionCode` e `versionName` em `build.gradle.kts`)
2. Instalar via `adb install -r` ou MDM
3. A instalação `-r` (replace) preserva os dados do usuário — configuração e audit log são mantidos
4. O WorkManager será reagendado automaticamente na próxima inicialização

---

## 11. Remoção / factory reset

```bash
# Remover o app
adb uninstall br.com.corp.heimdall

# Se Device Owner, primeiro remover via
adb shell dpm remove-active-admin br.com.corp.heimdall/.BootReceiver
adb uninstall br.com.corp.heimdall
```

Após desinstalar, remover o `deviceId` do dispositivo da whitelist no backend via painel Odin Admin.

---

## 12. Checklist de validação pós-instalação

- [ ] Tela de setup aparece na primeira abertura
- [ ] `systemId` configurado corresponde ao ponto de acesso
- [ ] `deviceId` registrado na whitelist do backend
- [ ] Token novo válido → tela verde com foto e nome em ≤ 2s
- [ ] HMAC inválido → negado sem chamar o backend (verificar via log de debug)
- [ ] Backend inacessível → "Servidor indisponível" em ≤ 10s
- [ ] PIN de manutenção funciona (5 toques no logo)
- [ ] App reinicia automaticamente após reboot do dispositivo
- [ ] Tela nunca desliga (FLAG_KEEP_SCREEN_ON ativo)
- [ ] Screenshot bloqueada (FLAG_SECURE ativo)
- [ ] Log de auditoria visível no diálogo de manutenção
