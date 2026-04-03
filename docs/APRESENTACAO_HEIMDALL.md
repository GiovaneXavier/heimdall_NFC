# HEIMDALL
## Sistema de Controle de Acesso Corporativo

**Versão 1.0**
**Classificação: Interno**

---

&nbsp;

## Sobre o Heimdall

O **Heimdall** é um aplicativo Android desenvolvido internamente para modernizar e centralizar o controle de acesso corporativo. Instalado em tablets ou smartphones fixos nas entradas, o sistema lê automaticamente as credenciais dos colaboradores — seja por aproximação do celular via **NFC** ou escaneamento de **QR Code** — e valida o acesso em tempo real, exibindo o resultado com foto e nome do funcionário.

O Heimdall é a peça final de um ecossistema completo de controle de acesso:

```
  ODIN Admin          HUGINN Mobile         HEIMDALL
  (Painel Web)   →    (App Funcionário)  →   (Ponto de Acesso)
  Emite cadastro      Gera credencial         Lê e valida
```

&nbsp;

---

&nbsp;

## Como funciona

### Fluxo de acesso em 3 etapas

```
  ┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
  │                 │     │                  │     │                 │
  │   Funcionário   │────▶│    Heimdall      │────▶│    Resultado    │
  │  aproxima NFC   │     │  valida em < 2s  │     │  Verde / Vermelho│
  │  ou exibe QR    │     │                  │     │  com foto e nome │
  │                 │     │                  │     │                 │
  └─────────────────┘     └──────────────────┘     └─────────────────┘
```

1. **Leitura** — O colaborador aproxima o celular (NFC) ou exibe o QR Code do app Huginn
2. **Validação** — O Heimdall verifica a credencial localmente e junto ao servidor corporativo
3. **Resultado** — Tela verde (acesso autorizado) ou vermelha (acesso negado) com foto, nome e som de feedback

&nbsp;

---

&nbsp;

## Modos de operação

O sistema suporta dois canais de leitura, configuráveis por ponto de acesso:

&nbsp;

### NFC — Aproximação
O funcionário simplesmente **aproxima o celular** do dispositivo Heimdall. A leitura é instantânea, sem qualquer interação. Ideal para entradas de alto fluxo.

&nbsp;

### QR Code — Câmera
A câmera fica em modo contínuo, detectando automaticamente o QR Code ao ser exibido na tela do Huginn. Recomendado para dispositivos sem chip NFC ou ambientes onde o contato físico não é desejável.

&nbsp;

---

&nbsp;

## Resultado da validação

&nbsp;

### Acesso Autorizado
- Fundo **verde**
- Foto circular do colaborador
- Nome completo em destaque
- Som curto de confirmação
- Retorna automaticamente ao modo de leitura após 3 segundos

&nbsp;

### Acesso Negado
- Fundo **vermelho**
- Motivo específico da negação (ex: "Token expirado", "Dispositivo não autorizado")
- Som de alerta
- Retorna automaticamente após 3 segundos

&nbsp;

---

&nbsp;

## Segurança

O Heimdall foi projetado com segurança em múltiplas camadas:

&nbsp;

| Camada | Proteção |
|--------|----------|
| **Credencial** | Assinatura digital HMAC-SHA256 em cada token — impossível falsificar sem a chave secreta |
| **Validade temporal** | Cada token expira em ±30 segundos após emissão — inútil se interceptado |
| **Anti-replay** | O servidor rejeita qualquer credencial já utilizada — impossível reutilizar |
| **Autenticação do dispositivo** | Apenas Heimdalls registrados na whitelist conseguem consultar o servidor |
| **Comunicação** | HTTPS obrigatório — dados criptografados em trânsito |
| **Armazenamento** | Banco de dados local criptografado com SQLCipher — protegido em repouso |
| **Tela** | Bloqueio de captura de tela (FLAG_SECURE) — sem print ou gravação |

&nbsp;

---

&nbsp;

## Log de auditoria

Cada tentativa de acesso — aprovada ou negada — é **registrada automaticamente** com:

- Data e hora
- Nome e matrícula do colaborador
- Canal utilizado (NFC ou QR)
- Resultado e motivo (em caso de negação)

Os registros ficam disponíveis localmente no dispositivo para consulta pelo administrador de TI e são sincronizados periodicamente com o servidor corporativo.

&nbsp;

---

&nbsp;

## Operação contínua

O Heimdall foi desenvolvido para rodar **24 horas por dia, 7 dias por semana**, sem interação de operador:

- Tela sempre ativa, nunca entra em modo de suspensão
- Reinicia automaticamente após queda de energia ou reboot do dispositivo
- Suporte a modo quiosque — o app não pode ser fechado acidentalmente pelo operador
- Compatível com soluções MDM corporativas para gerenciamento remoto

&nbsp;

---

&nbsp;

## Compatibilidade com sistemas legados

O Heimdall reconhece automaticamente o formato de credencial e roteia para o sistema correto:

- **Credenciais novas (Huginn)** → validadas via backend moderno com todos os controles de segurança
- **Credenciais legadas (`CORP.XXXX` / `PART.XXXX`)** → consultadas na API legada sem interrupção do fluxo

A transição entre sistemas é **transparente para o funcionário** — o mesmo dispositivo atende ambos os formatos simultaneamente.

&nbsp;

---

&nbsp;

## Administração

O acesso administrativo ao dispositivo é protegido por um **PIN de 6 dígitos** (definido na instalação), acessível via 5 toques rápidos no logo da tela de leitura. Com o PIN, o administrador de TI pode:

- Alterar o canal de leitura (NFC ↔ QR)
- Atualizar o identificador do ponto de acesso
- Consultar o log dos últimos 30 acessos diretamente no dispositivo
- Visualizar informações de diagnóstico (versão do app, ID do dispositivo)
- Alterar o PIN de manutenção

Após 5 tentativas erradas, o diálogo é bloqueado por 60 segundos — proteção contra tentativas de força bruta.

&nbsp;

---

&nbsp;

## Visão técnica resumida

| Atributo | Detalhe |
|----------|---------|
| **Plataforma** | Android 9+ (API 28) |
| **Desenvolvimento** | Kotlin nativo — sem frameworks de terceiros para UI crítica |
| **Backend** | REST corporativo via HTTPS |
| **Banco local** | Room com SQLCipher (criptografado) |
| **NFC** | ISO 7816-4, AID proprietário `F05352425200` |
| **QR** | CameraX + Google ML Kit Barcode Scanning |
| **Cobertura de testes** | 107 testes automatizados — build bloqueado se algum falhar |
| **CI/CD** | GitHub Actions — build e testes executados a cada alteração |
| **Versão atual** | 1.0.0 |

&nbsp;

---

&nbsp;

## Próximos passos sugeridos

1. **Validação em dispositivo físico** — teste end-to-end com NFC e QR em ponto de acesso piloto
2. **Integração com Odin Admin** — painel para gerenciar dispositivos Heimdall e whitelist
3. **Certificate pinning** — fixar o certificado do servidor para proteção adicional contra MITM
4. **Testes instrumentados** — cobertura automatizada dos fluxos completos na UI
5. **Deploy via MDM** — distribuição centralizada para múltiplos pontos de acesso

&nbsp;

---

&nbsp;

*Documento gerado em abril de 2026 · Versão do sistema: 1.0.0 · Classificação: Interno*
