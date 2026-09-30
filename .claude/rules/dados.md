---
paths:
  - "src/main/resources/db/**"
  - "src/main/resources/application*.yml*"
  - "src/main/java/com/smartkey/repository/**"
---
Antes de alterar, leia `docs/memoria/dados.md`.
Invariante principal: schema `smartkey` isolado no Supabase compartilhado; mudança de tabela = nova migração + atualizar contagem no PostgresIntegrationTest.
