# Produto — objetivos, especificação, roadmap

Situação das fases: README § Situação do projeto.

### PRO-01 · Especificação original (resumo)
[confirmado · 2026-09 · 1ª mensagem do usuário] MVP de chave digital
temporária para hospedagens, feito passo a passo. Reserva → credencial
exclusiva por hóspede/aparelho → permissões por porta (ACE-01). Exemplo:
João, apto 804, check-in 15:00, checkout 11:00, portas `entrada_condominio`,
`apartamento_804`, `academia`; leitor do 805 deve negar.
Segurança: CRI-01. Leitor mostra 🟢 ACESSO LIBERADO / 🔴 ACESSO NEGADO + motivo.
Fases: 1 API sem NFC · 2 banco e painel · 3 criptografia · 4 app hóspede ·
5 app leitor · 6 NFC entre celulares · 7 testes completos · 8 só depois,
ESP32 + leitor NFC físico. Não avançar sem a fase anterior funcionando.

### PRO-02 · Os 10 casos de teste pedidos
[confirmado · 2026-09] 1 antes do check-in NEGADO · 2 portaria durante
LIBERADO · 3 apto correto LIBERADO · 4 outro apto NEGADO · 5 academia
autorizada LIBERADO · 6 permissão da academia removida NEGADO · 7 credencial
revogada NEGADO · 8 depois do checkout NEGADO · 9 desafio reutilizado NEGADO ·
10 assinatura alterada NEGADO. Devem continuar cobertos por testes.

### PRO-03 · Mudanças em relação ao pedido original
[confirmado · 2026-09] Linguagem trocada de Node/TypeScript para **Java**
(pedido do usuário). Apps Android das fases 4–5 viraram **PWA** (sem instalar
app, AMB-01/FRO-01); o canal NFC (fase 6) continua desejado a longo prazo via
dois apps Android nativos (hóspede com HCE e leitor). O backend NFC
(desafio/assinatura) já existe e é testado.

### PRO-04 · Wallet (Samsung/Apple/Google)
[confirmado · 2026-09] O usuário queria a chave dentro da Wallet.
[documentado] Conclusão: NFC de fechadura na Wallet exige parceria comercial
(Apple Access, Google Hotel Key, Aliro) — inviável sozinho. Wallet com QR é
possível (Google Wallet grátis; Apple exige Developer Program ou "Create a
Pass"), mas o passe não troca o QR a cada 20 s — mais frágil. Decisão: PWA.

### PRO-05 · Fechadura física (fase 8b)
[pendente · 2026-09] ESP32 + leitor NFC físico. Precisará validar localmente
e sincronizar revogações (ACE-08). Não iniciado.

### PRO-06 · Limitações aceitas no MVP
[documentado · 2026-09] QR desenhado no servidor (sem uso offline); limite de
tentativas em memória; admin por chave única; ataque de retransmissão em
tempo real não tratado. Lista oficial: README § Limitações conhecidas e
docs/SEGURANCA.md § Riscos conhecidos.
