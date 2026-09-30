# Segurança web — autenticação, autorização, borda HTTP

Código: `config/security/*`, `config/{ClientOrigin,RateLimiter,SmartKeyProperties}`,
`web/ReaderScopeGuard`, `web/error/*`.
Fonte oficial e histórico de auditoria: `docs/SEGURANCA.md`.

### SEG-01 · Toda autorização passa pelo Spring Security
[documentado · 2026-09 · armadilha real] Proibido voltar a autorizar
comparando o caminho "cru" da requisição: filtros caseiros deixavam passar
`/api;/admin/...` e `/api/%61dmin/...` (acesso admin completo sem chave).
Onde: SecurityConfig · teste: SecurityHardeningTest

### SEG-02 · Endpoint novo nasce fechado
[documentado · 2026-09] A última regra é `denyAll` (`/api/**` e
`anyRequest`). Endpoint novo só funciona com regra explícita em
`SecurityConfig`, e precisa de teste em SecurityHardeningTest/ReaderAuthTest.
Onde: SecurityConfig

### SEG-03 · Chave de administração
[documentado · 2026-09] Cabeçalho `x-admin-key`; valor vem de `ADMIN_API_KEY`
/ `smartkey.admin-api-key`. Uma chave compartilhada (sem contas individuais);
o painel a guarda em `localStorage` — risco aberto, ver PEN-08.

### SEG-04 · Chave de leitor
[documentado · 2026-09] Cabeçalho `x-reader-key`, prefixo `rdr_`; servidor
guarda só o SHA-256 e compara em tempo constante. O leitor só pergunta sobre
a **própria** porta (ReaderScopeGuard), não lê histórico nem acessa admin
(→ 403). Troca individual invalida a anterior na hora.
Por quê: sem confinamento, a chave da academia serviria para atacar o apto.
Onde: ApiKeyAuthenticationFilter, ReaderScopeGuard · teste: ReaderAuthTest

### SEG-05 · ApiKeyAuthenticationFilter não é @Component
[código · 2026-09] De propósito: como @Component o Spring Boot também o
registraria como filtro comum, e ele rodaria duas vezes.
Onde: ApiKeyAuthenticationFilter (Javadoc explica)

### SEG-06 · IP do cliente vem do proxy confiável
[documentado · 2026-09] `ClientOrigin` conta o `X-Forwarded-For` **a partir do
fim** usando `TRUSTED_PROXY_HOPS` (Render = 1; Render + Cloudflare como proxy
= 2). O início do cabeçalho é escrito pelo cliente e é falsificável.
Onde: ClientOrigin · teste: RateLimitTest

### SEG-07 · Limite de tentativas em memória
[documentado · 2026-09] Janela fixa por IP (falhas de autenticação, ativação,
desenho de QR). Correto com **uma** instância; várias instâncias exigirão
armazenamento compartilhado.
Onde: RateLimiter

### SEG-08 · Erro do cliente é 4xx, nunca 500
[documentado · 2026-09] JSON inválido, id malformado, rota inexistente,
método errado, violação de constraint (→ 409) têm tratamento específico.
Qualquer caso novo que gere 500 é bug.
Onde: GlobalExceptionHandler · teste: SecurityHardeningTest

### SEG-09 · Cabeçalhos de proteção
[documentado · 2026-09] CSP (só scripts do próprio servidor), Referrer-Policy
no-referrer, Permissions-Policy. Console do H2 desligado. Ver FRO-02.
Onde: SecurityConfig

### SEG-10 · Recursos de desenvolvimento nunca em produção
[documentado · 2026-09] `ALLOW_TIME_TRAVEL`, `ALLOW_INSECURE_CHECK`,
`SEED_DEMO` e o perfil `demo` (chaves públicas `demo123`, `demo_reader_*`).
Detalhes: docs/SEGURANCA.md § Recursos de desenvolvimento.
