# Credenciais — ciclo de vida

Código: `service/AdminService`, `service/KeyEnrollmentService`,
`domain/model/{Guest,Reservation,Credential,Device,AccessGrant}`, controllers
Guest/Reservation/Credential/Device/Key.

### CRE-01 · Ciclo de vida
[confirmado · 2026-09 · especificação original] Reserva (hóspede, apartamento,
check-in, checkout) → credencial exclusiva por hóspede/aparelho → permissões
por porta → uso → expira sozinha no checkout ou é revogada antes.
Entidades pedidas: Reservation, Guest, Device, Credential, Reader,
AccessGrant, AccessEvent.

### CRE-02 · Ativação por link com token de uso único
[documentado · 2026-09] O link de ativação carrega um token sorteado (24
bytes) **depois do `#`** — o navegador não envia essa parte ao servidor — e o
token vai no **corpo** das requisições, nunca na URL. Queima na 1ª ativação.
Onde: KeyEnrollmentService, KeyController · detalhes: docs/SEGURANCA.md § Sequestrar a ativação

### CRE-03 · Ativação recusada em credencial inválida
[documentado · 2026-09] Não ativa se a credencial estiver revogada, a reserva
cancelada ou a estadia encerrada.
Onde: KeyEnrollmentService · teste: SecurityHardeningTest

### CRE-04 · O servidor só recebe a chave pública
[confirmado · 2026-09] O aparelho gera o par (não exportável no navegador;
Android Keystore nos apps nativos futuros) e envia só a pública.
Ver CRI-01.

### CRE-05 · QR desenhado no servidor por POST
[documentado · 2026-09] `POST /api/keys/qr` com o conteúdo no corpo (nunca GET
com o token na URL, que ia parar em logs). Limitado a 60/min por IP.
Consequência: o app precisa de rede para exibir o QR (ver PRO-06).
Onde: KeyController, QrCodeRenderer
