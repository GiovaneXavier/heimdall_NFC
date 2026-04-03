"""
Gera docs/APRESENTACAO_HEIMDALL.docx com estilo visual similar ao Samsung Galaxy Series.
Execute: python docs/gerar_apresentacao.py
"""

from docx import Document
from docx.shared import Pt, Cm, RGBColor, Inches
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml.ns import qn
from docx.oxml import OxmlElement
import os

# ── Paleta de cores Samsung-inspired ─────────────────────────────────────────
AZUL_SAMSUNG   = RGBColor(0x00, 0x2E, 0x6E)   # azul escuro institucional
AZUL_CLARO     = RGBColor(0x12, 0x5F, 0xD4)   # azul destaque
CINZA_ESCURO   = RGBColor(0x1E, 0x1E, 0x1E)
CINZA_MEDIO    = RGBColor(0x55, 0x55, 0x55)
CINZA_LINHA    = RGBColor(0xD0, 0xD8, 0xE8)
VERDE          = RGBColor(0x1B, 0x87, 0x3E)
VERMELHO       = RGBColor(0xC0, 0x23, 0x23)
BRANCO         = RGBColor(0xFF, 0xFF, 0xFF)
AZUL_HEADER_BG = RGBColor(0x00, 0x2E, 0x6E)


def set_cell_bg(cell, hex_color: RGBColor):
    tc = cell._tc
    tcPr = tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), str(hex_color))
    tcPr.append(shd)


def set_cell_border(cell, top=None, bottom=None, left=None, right=None):
    tc = cell._tc
    tcPr = tc.get_or_add_tcPr()
    tcBorders = OxmlElement("w:tcBorders")
    for side, val in [("top", top), ("bottom", bottom), ("left", left), ("right", right)]:
        if val:
            el = OxmlElement(f"w:{side}")
            el.set(qn("w:val"), val.get("val", "single"))
            el.set(qn("w:sz"), val.get("sz", "6"))
            el.set(qn("w:color"), val.get("color", "002E6E"))
            tcBorders.append(el)
    tcPr.append(tcBorders)


def add_run(para, text, bold=False, italic=False, size=11, color=None, font="Calibri"):
    run = para.add_run(text)
    run.bold = bold
    run.italic = italic
    run.font.name = font
    run.font.size = Pt(size)
    if color:
        run.font.color.rgb = color
    return run


def heading(doc, text, level=1, color=AZUL_SAMSUNG, size=None, space_before=18, space_after=6):
    sizes = {1: 22, 2: 16, 3: 13}
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(space_before)
    p.paragraph_format.space_after  = Pt(space_after)
    run = p.add_run(text)
    run.bold = True
    run.font.name = "Calibri"
    run.font.size = Pt(size or sizes.get(level, 12))
    run.font.color.rgb = color
    return p


def body(doc, text, color=CINZA_ESCURO, size=10.5, space_after=6, bold=False, italic=False, indent=False):
    p = doc.add_paragraph()
    p.paragraph_format.space_after  = Pt(space_after)
    p.paragraph_format.space_before = Pt(0)
    if indent:
        p.paragraph_format.left_indent = Cm(0.8)
    run = p.add_run(text)
    run.bold = bold
    run.italic = italic
    run.font.name = "Calibri"
    run.font.size = Pt(size)
    run.font.color.rgb = color
    return p


def divider(doc, color=AZUL_CLARO):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after  = Pt(4)
    pPr = p._p.get_or_add_pPr()
    pBdr = OxmlElement("w:pBdr")
    bottom = OxmlElement("w:bottom")
    bottom.set(qn("w:val"), "single")
    bottom.set(qn("w:sz"), "6")
    bottom.set(qn("w:color"), str(color))
    pBdr.append(bottom)
    pPr.append(pBdr)
    return p


def add_table(doc, headers, rows, col_widths=None, stripe=True):
    table = doc.add_table(rows=1 + len(rows), cols=len(headers))
    table.style = "Table Grid"
    table.alignment = WD_TABLE_ALIGNMENT.CENTER

    # Cabeçalho
    hdr_cells = table.rows[0].cells
    for i, h in enumerate(headers):
        cell = hdr_cells[i]
        set_cell_bg(cell, AZUL_SAMSUNG)
        p = cell.paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        run = p.add_run(h)
        run.bold = True
        run.font.name = "Calibri"
        run.font.size = Pt(10)
        run.font.color.rgb = BRANCO
        cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER

    # Linhas de dados
    for r_idx, row in enumerate(rows):
        row_cells = table.rows[r_idx + 1].cells
        bg = RGBColor(0xF0, 0xF4, 0xFA) if (stripe and r_idx % 2 == 0) else BRANCO
        for c_idx, val in enumerate(row):
            cell = row_cells[c_idx]
            set_cell_bg(cell, bg)
            p = cell.paragraphs[0]
            p.alignment = WD_ALIGN_PARAGRAPH.LEFT
            # Negrito na primeira coluna
            bold = (c_idx == 0)
            run = p.add_run(val)
            run.bold = bold
            run.font.name = "Calibri"
            run.font.size = Pt(10)
            run.font.color.rgb = CINZA_ESCURO

    # Larguras de colunas
    if col_widths:
        for row in table.rows:
            for i, w in enumerate(col_widths):
                row.cells[i].width = Cm(w)

    return table


def bullet(doc, text, color=CINZA_ESCURO, size=10.5, bold=False):
    p = doc.add_paragraph(style="List Bullet")
    p.paragraph_format.space_before = Pt(1)
    p.paragraph_format.space_after  = Pt(3)
    p.paragraph_format.left_indent  = Cm(0.8)
    run = p.add_run(text)
    run.font.name = "Calibri"
    run.font.size = Pt(size)
    run.font.color.rgb = color
    run.bold = bold
    return p


def info_box(doc, label, value, label_color=AZUL_CLARO, value_color=CINZA_ESCURO):
    table = doc.add_table(rows=1, cols=2)
    table.style = "Table Grid"
    c0, c1 = table.rows[0].cells
    set_cell_bg(c0, RGBColor(0xEA, 0xF1, 0xFB))
    set_cell_bg(c1, BRANCO)
    c0.width = Cm(4)
    c1.width = Cm(12)
    p0 = c0.paragraphs[0]
    r0 = p0.add_run(label)
    r0.bold = True; r0.font.name = "Calibri"; r0.font.size = Pt(10)
    r0.font.color.rgb = label_color
    p1 = c1.paragraphs[0]
    r1 = p1.add_run(value)
    r1.font.name = "Calibri"; r1.font.size = Pt(10)
    r1.font.color.rgb = value_color
    doc.add_paragraph().paragraph_format.space_after = Pt(2)


# ═══════════════════════════════════════════════════════════════════════════════
# DOCUMENTO
# ═══════════════════════════════════════════════════════════════════════════════

doc = Document()

# Margens
for section in doc.sections:
    section.page_width   = Cm(21)
    section.page_height  = Cm(29.7)
    section.top_margin    = Cm(2.0)
    section.bottom_margin = Cm(2.0)
    section.left_margin   = Cm(2.5)
    section.right_margin  = Cm(2.5)


# ── CAPA ──────────────────────────────────────────────────────────────────────

# Barra azul superior (tabela 1×1)
cover_bar = doc.add_table(rows=1, cols=1)
cover_bar.alignment = WD_TABLE_ALIGNMENT.CENTER
cell = cover_bar.rows[0].cells[0]
cell.width = Cm(16)
set_cell_bg(cell, AZUL_SAMSUNG)
p = cell.paragraphs[0]
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
run = p.add_run("HEIMDALL")
run.bold = True
run.font.name = "Calibri Light"
run.font.size = Pt(48)
run.font.color.rgb = BRANCO
cell._tc.get_or_add_tcPr()

for _ in range(2):
    doc.add_paragraph().paragraph_format.space_after = Pt(4)

p_sub = doc.add_paragraph()
p_sub.alignment = WD_ALIGN_PARAGRAPH.CENTER
add_run(p_sub, "Sistema de Controle de Acesso Corporativo",
        bold=True, size=18, color=AZUL_SAMSUNG)

doc.add_paragraph().paragraph_format.space_after = Pt(8)

p_meta = doc.add_paragraph()
p_meta.alignment = WD_ALIGN_PARAGRAPH.CENTER
add_run(p_meta, "Versão 1.0  ·  Abril 2026  ·  Classificação: Interno",
        size=10, color=CINZA_MEDIO, italic=True)

for _ in range(4):
    doc.add_paragraph().paragraph_format.space_after = Pt(6)

divider(doc, AZUL_CLARO)

doc.add_page_break()


# ── 1. VISÃO GERAL ────────────────────────────────────────────────────────────
heading(doc, "1. Sobre o Heimdall", level=1)
divider(doc)

body(doc,
     "O Heimdall é um aplicativo Android desenvolvido internamente para modernizar e "
     "centralizar o controle de acesso corporativo. Instalado em tablets ou smartphones "
     "fixos nas entradas, o sistema lê automaticamente as credenciais dos colaboradores — "
     "seja por aproximação do celular via NFC ou escaneamento de QR Code — e valida o "
     "acesso em tempo real, exibindo o resultado com foto e nome do funcionário.",
     size=11)

doc.add_paragraph().paragraph_format.space_after = Pt(4)

body(doc, "O Heimdall é a peça final de um ecossistema completo:", bold=True, size=10.5)

ecosystem = doc.add_table(rows=1, cols=3)
ecosystem.alignment = WD_TABLE_ALIGNMENT.CENTER
labels    = ["ODIN Admin",           "HUGINN Mobile",              "HEIMDALL"]
sub_labels = ["Painel Web\nEmite cadastros", "App do Funcionário\nGera credenciais", "Ponto de Acesso\nLê e valida"]
colors    = [AZUL_SAMSUNG, AZUL_CLARO, AZUL_SAMSUNG]

for i, (lbl, sub, col) in enumerate(zip(labels, sub_labels, colors)):
    cell = ecosystem.rows[0].cells[i]
    set_cell_bg(cell, col)
    cell._tc.get_or_add_tcPr()
    p = cell.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r1 = p.add_run(lbl)
    r1.bold = True; r1.font.name = "Calibri"; r1.font.size = Pt(12)
    r1.font.color.rgb = BRANCO
    p2 = cell.add_paragraph()
    p2.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r2 = p2.add_run(sub)
    r2.font.name = "Calibri"; r2.font.size = Pt(9)
    r2.font.color.rgb = RGBColor(0xCC, 0xDD, 0xFF)
    cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER

doc.add_paragraph().paragraph_format.space_after = Pt(10)


# ── 2. COMO FUNCIONA ─────────────────────────────────────────────────────────
doc.add_page_break()
heading(doc, "2. Como Funciona", level=1)
divider(doc)

body(doc, "O acesso ocorre em três etapas automáticas, sem necessidade de operador:", size=11)
doc.add_paragraph().paragraph_format.space_after = Pt(6)

steps = doc.add_table(rows=1, cols=3)
steps.alignment = WD_TABLE_ALIGNMENT.CENTER
step_data = [
    ("① Leitura",
     "O colaborador aproxima o celular (NFC) ou exibe o QR Code do app Huginn."),
    ("② Validação",
     "O Heimdall verifica a credencial localmente e junto ao servidor. Resposta em menos de 2 segundos."),
    ("③ Resultado",
     "Tela verde (autorizado) ou vermelha (negado) com foto, nome e som de feedback. Retorna automaticamente em 3 segundos."),
]
step_colors = [AZUL_SAMSUNG, AZUL_CLARO, AZUL_SAMSUNG]

for i, ((titulo, desc), col) in enumerate(zip(step_data, step_colors)):
    cell = steps.rows[0].cells[i]
    set_cell_bg(cell, RGBColor(0xF0, 0xF4, 0xFA))
    p_title = cell.paragraphs[0]
    r = p_title.add_run(titulo)
    r.bold = True; r.font.name = "Calibri"; r.font.size = Pt(12)
    r.font.color.rgb = col
    p_desc = cell.add_paragraph()
    rd = p_desc.add_run(desc)
    rd.font.name = "Calibri"; rd.font.size = Pt(10)
    rd.font.color.rgb = CINZA_ESCURO

doc.add_paragraph().paragraph_format.space_after = Pt(10)


# ── 3. MODOS DE OPERAÇÃO ──────────────────────────────────────────────────────
heading(doc, "3. Modos de Operação", level=1)
divider(doc)

heading(doc, "NFC — Aproximação", level=2, color=AZUL_CLARO, size=13, space_before=10)
body(doc,
     "O funcionário simplesmente aproxima o celular do dispositivo Heimdall. "
     "A leitura é instantânea, sem qualquer interação adicional. "
     "Ideal para entradas de alto fluxo.", size=11)

heading(doc, "QR Code — Câmera", level=2, color=AZUL_CLARO, size=13, space_before=12)
body(doc,
     "A câmera fica em modo contínuo, detectando automaticamente o QR Code "
     "ao ser exibido na tela do Huginn. Recomendado para dispositivos sem chip NFC "
     "ou ambientes onde o contato físico não é desejável.", size=11)

doc.add_paragraph().paragraph_format.space_after = Pt(6)

heading(doc, "4. Resultado da Validação", level=1, space_before=14)
divider(doc)

results = doc.add_table(rows=1, cols=2)
results.alignment = WD_TABLE_ALIGNMENT.CENTER

res_data = [
    (VERDE,    "Acesso Autorizado",
     ["Fundo verde",
      "Foto circular do colaborador",
      "Nome completo em destaque",
      "Som curto de confirmação",
      "Retorna ao modo de leitura em 3 segundos"]),
    (VERMELHO, "Acesso Negado",
     ["Fundo vermelho",
      "Motivo específico da negação",
      "Ex: \"Token expirado\", \"Dispositivo não autorizado\"",
      "Som de alerta",
      "Retorna ao modo de leitura em 3 segundos"]),
]

for i, (col, titulo, itens) in enumerate(res_data):
    cell = results.rows[0].cells[i]
    set_cell_bg(cell, RGBColor(0xF8, 0xFA, 0xFF))
    p_t = cell.paragraphs[0]
    p_t.alignment = WD_ALIGN_PARAGRAPH.CENTER
    rt = p_t.add_run(titulo)
    rt.bold = True; rt.font.name = "Calibri"; rt.font.size = Pt(13)
    rt.font.color.rgb = col
    for item in itens:
        p_i = cell.add_paragraph()
        p_i.paragraph_format.left_indent = Cm(0.4)
        ri = p_i.add_run(f"  •  {item}")
        ri.font.name = "Calibri"; ri.font.size = Pt(10)
        ri.font.color.rgb = CINZA_ESCURO

doc.add_paragraph().paragraph_format.space_after = Pt(6)


# ── 5. SEGURANÇA ──────────────────────────────────────────────────────────────
doc.add_page_break()
heading(doc, "5. Segurança", level=1)
divider(doc)

body(doc, "O Heimdall foi projetado com segurança em múltiplas camadas independentes:", size=11)
doc.add_paragraph().paragraph_format.space_after = Pt(6)

sec_rows = [
    ("Assinatura Digital",         "Cada credencial é assinada com HMAC-SHA256. Impossível falsificar sem a chave secreta."),
    ("Validade Temporal",          "Cada token expira em ±30 segundos após a emissão. Inútil se interceptado."),
    ("Anti-replay",                "O servidor rejeita qualquer credencial já utilizada. Impossível reutilizar."),
    ("Autenticação do Dispositivo","Apenas Heimdalls registrados conseguem consultar o servidor."),
    ("Comunicação Criptografada",  "HTTPS obrigatório. Dados criptografados em trânsito."),
    ("Banco de Dados Cifrado",     "Banco local criptografado com SQLCipher. Dados protegidos em repouso."),
    ("Bloqueio de Tela",           "Captura de tela bloqueada por hardware. Sem print ou gravação."),
    ("Sem backup externo",         "Dados do app não são copiados para nuvens externas."),
]
add_table(doc, ["Camada de Proteção", "Descrição"], sec_rows,
          col_widths=[5.5, 11.0])

doc.add_paragraph().paragraph_format.space_after = Pt(6)


# ── 6. LOG DE AUDITORIA ───────────────────────────────────────────────────────
heading(doc, "6. Log de Auditoria", level=1)
divider(doc)

body(doc,
     "Cada tentativa de acesso — aprovada ou negada — é registrada automaticamente com:", size=11)

for item in ["Data e hora", "Nome e matrícula do colaborador",
             "Canal utilizado (NFC ou QR)", "Resultado e motivo (em caso de negação)"]:
    bullet(doc, item)

doc.add_paragraph().paragraph_format.space_after = Pt(4)
body(doc,
     "Os registros ficam disponíveis localmente no dispositivo e são sincronizados "
     "periodicamente com o servidor corporativo.", size=11, italic=True, color=CINZA_MEDIO)


# ── 7. OPERAÇÃO CONTÍNUA ──────────────────────────────────────────────────────
heading(doc, "7. Operação Contínua", level=1)
divider(doc)

body(doc,
     "O Heimdall foi desenvolvido para rodar 24 horas por dia, 7 dias por semana, "
     "sem interação de operador:", size=11)

for item in [
    "Tela sempre ativa — nunca entra em modo de suspensão",
    "Reinicia automaticamente após queda de energia ou reboot do dispositivo",
    "Suporte a modo quiosque — o app não pode ser fechado acidentalmente",
    "Compatível com soluções MDM corporativas para gerenciamento remoto da frota",
]:
    bullet(doc, item)


# ── 8. COMPATIBILIDADE LEGADA ─────────────────────────────────────────────────
heading(doc, "8. Compatibilidade com Sistemas Legados", level=1)
divider(doc)

body(doc,
     "O Heimdall reconhece automaticamente o formato da credencial e roteia para o "
     "sistema correto, sem qualquer configuração adicional:", size=11)

doc.add_paragraph().paragraph_format.space_after = Pt(4)

compat = doc.add_table(rows=2, cols=2)
compat.alignment = WD_TABLE_ALIGNMENT.CENTER
c_data = [
    ("Credenciais novas (Huginn)",
     "Validadas via backend moderno com todos os controles de segurança."),
    ("Credenciais legadas (CORP.XXXX / PART.XXXX)",
     "Consultadas na API legada sem interrupção do fluxo. Transparente para o funcionário."),
]
for i, (lbl, desc) in enumerate(c_data):
    cells = compat.rows[i].cells
    set_cell_bg(cells[0], RGBColor(0xEA, 0xF1, 0xFB))
    set_cell_bg(cells[1], BRANCO)
    cells[0].width = Cm(6)
    cells[1].width = Cm(10.5)
    r0 = cells[0].paragraphs[0].add_run(lbl)
    r0.bold = True; r0.font.name = "Calibri"; r0.font.size = Pt(10)
    r0.font.color.rgb = AZUL_SAMSUNG
    r1 = cells[1].paragraphs[0].add_run(desc)
    r1.font.name = "Calibri"; r1.font.size = Pt(10)
    r1.font.color.rgb = CINZA_ESCURO

doc.add_paragraph().paragraph_format.space_after = Pt(6)


# ── 9. ADMINISTRAÇÃO ─────────────────────────────────────────────────────────
doc.add_page_break()
heading(doc, "9. Administração", level=1)
divider(doc)

body(doc,
     "O acesso administrativo ao dispositivo é protegido por um PIN de 6 dígitos, "
     "acessível via 5 toques rápidos no logo da tela de leitura. Com o PIN, o "
     "administrador de TI pode:", size=11)

for item in [
    "Alterar o canal de leitura (NFC ↔ QR)",
    "Atualizar o identificador do ponto de acesso",
    "Consultar o log dos últimos 30 acessos diretamente no dispositivo",
    "Visualizar informações de diagnóstico (versão do app, ID do dispositivo)",
    "Alterar o PIN de manutenção",
]:
    bullet(doc, item)

doc.add_paragraph().paragraph_format.space_after = Pt(4)
body(doc,
     "Após 5 tentativas erradas, o diálogo é bloqueado por 60 segundos — "
     "proteção contra tentativas de acesso indevido.",
     italic=True, color=CINZA_MEDIO, size=10.5)


# ── 10. VISÃO TÉCNICA ─────────────────────────────────────────────────────────
heading(doc, "10. Visão Técnica Resumida", level=1)
divider(doc)

tech_rows = [
    ("Plataforma",          "Android 9+ (API 28 ou superior)"),
    ("Linguagem",           "Kotlin nativo"),
    ("Comunicação",         "HTTPS com servidor corporativo REST"),
    ("Banco local",         "Criptografado com SQLCipher"),
    ("NFC",                 "ISO 7816-4, protocolo IsoDep"),
    ("QR Code",             "CameraX + Google ML Kit Barcode Scanning"),
    ("Testes automatizados","107 testes — build bloqueado se algum falhar"),
    ("CI/CD",               "GitHub Actions — build e testes a cada alteração"),
    ("Versão atual",        "1.0.0"),
]
add_table(doc, ["Atributo", "Detalhe"], tech_rows, col_widths=[5.5, 11.0])

doc.add_paragraph().paragraph_format.space_after = Pt(10)


# ── 11. PRÓXIMOS PASSOS ───────────────────────────────────────────────────────
heading(doc, "11. Próximos Passos Sugeridos", level=1)
divider(doc)

next_steps = [
    ("1. Validação em dispositivo físico",
     "Teste end-to-end com NFC e QR em ponto de acesso piloto."),
    ("2. Integração com Odin Admin",
     "Painel para gerenciar dispositivos Heimdall e whitelist de dispositivos."),
    ("3. Certificate Pinning",
     "Fixar o certificado do servidor para proteção adicional contra ataques MITM."),
    ("4. Testes instrumentados",
     "Cobertura automatizada dos fluxos completos na interface do usuário."),
    ("5. Deploy via MDM",
     "Distribuição centralizada para múltiplos pontos de acesso."),
]

ns_table = doc.add_table(rows=len(next_steps), cols=2)
ns_table.alignment = WD_TABLE_ALIGNMENT.CENTER
for i, (titulo, desc) in enumerate(next_steps):
    cells = ns_table.rows[i].cells
    bg = RGBColor(0xF0, 0xF4, 0xFA) if i % 2 == 0 else BRANCO
    set_cell_bg(cells[0], RGBColor(0x00, 0x2E, 0x6E))
    set_cell_bg(cells[1], bg)
    cells[0].width = Cm(5.5)
    cells[1].width = Cm(11.0)
    rt = cells[0].paragraphs[0].add_run(titulo)
    rt.bold = True; rt.font.name = "Calibri"; rt.font.size = Pt(10)
    rt.font.color.rgb = BRANCO
    rd = cells[1].paragraphs[0].add_run(desc)
    rd.font.name = "Calibri"; rd.font.size = Pt(10)
    rd.font.color.rgb = CINZA_ESCURO

doc.add_paragraph().paragraph_format.space_after = Pt(16)


# ── RODAPÉ ────────────────────────────────────────────────────────────────────
divider(doc, AZUL_SAMSUNG)
p_footer = doc.add_paragraph()
p_footer.alignment = WD_ALIGN_PARAGRAPH.CENTER
add_run(p_footer,
        "Heimdall v1.0.0  ·  Abril 2026  ·  Documento Interno  ·  br.com.corp.heimdall",
        size=9, color=CINZA_MEDIO, italic=True)


# ── SALVAR ────────────────────────────────────────────────────────────────────
out = os.path.join(os.path.dirname(__file__), "APRESENTACAO_HEIMDALL.docx")
doc.save(out)
print(f"Documento gerado: {out}")
