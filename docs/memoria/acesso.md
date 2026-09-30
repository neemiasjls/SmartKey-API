# Acesso — regras de liberação e recusa

Código: `domain/access/AuthorizationEngine` (puro, sem banco nem rede),
`domain/enums/AccessDenyReason`, `service/AccessService`.

### ACE-01 · Casos obrigatórios de liberação e recusa
[confirmado · 2026-09 · especificação original] Antes do check-in → NEGADO;
durante a estadia, só nas portas autorizadas → LIBERADO; depois do checkout →
NEGADO automaticamente; revogação antes do checkout → NEGADO.
Onde: AuthorizationEngine · teste: AuthorizationEngineTest

### ACE-02 · Período de validade é semiaberto [início, fim)
[documentado · 2026-09 · código+Javadoc] O início entra, o fim não: no
instante exato do checkout já é AFTER_CHECK_OUT.
Por quê: checkout às 11:00 significa que às 11:00 a chave já não abre.
Onde: AuthorizationEngine · teste: AuthorizationEngineTest

### ACE-03 · Ordem das verificações
[código · 2026-09] Leitor (existe, ativo) → credencial (existe, não revogada,
reserva não cancelada) → período → permissão da porta (existe, ativa, dentro
da validade própria). O primeiro motivo encontrado é o retornado.
Onde: AuthorizationEngine

### ACE-04 · Motivos de recusa são texto de tela
[documentado · 2026-09] Cada `AccessDenyReason` carrega a mensagem em
português exibida no leitor abaixo de "ACESSO NEGADO". Mudar o texto muda a UI.
Onde: AccessDenyReason

### ACE-05 · Permissão por porta tem validade própria
[código · 2026-09] Um grant pode ter `validFrom`/`validUntil` próprios
(GRANT_NOT_YET_VALID / GRANT_EXPIRED) e pode ser removido isoladamente
(GRANT_REVOKED) sem revogar a credencial — caso 6 da especificação (academia).
Onde: AccessGrant, AuthorizationEngine

### ACE-06 · Códigos de porta reais
[código · 2026-09] Portas do cenário: `entrada_condominio` (portaria),
`apartamento_804`, `apartamento_805`, `academia`. Leitores: `reader_portaria`,
`reader_apto_804`, `reader_apto_805`, `reader_academia`. Códigos são validados
no servidor.
Onde: DemoDataSeeder

### ACE-07 · Dois relógios
[documentado · 2026-09] O "agora" simulado (`at`, só com ALLOW_TIME_TRAVEL)
vale apenas para regras de negócio. Validade de desafio e de QR usa sempre o
relógio real.
Por quê: com um relógio só, a viagem no tempo gerava CHALLENGE_EXPIRED.
Onde: ChallengeService, QrAccessService · detalhes: docs/SEGURANCA.md § Recursos de desenvolvimento

### ACE-08 · A decisão é sempre do servidor
[documentado · 2026-09] Sem rede nenhuma porta abre. Fechadura física (ESP32)
precisará validar localmente e sincronizar revogações — ver PRO-05.
