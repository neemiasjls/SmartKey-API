# Dados — banco, migrações, JPA

Código: `src/main/resources/db/migration`, `application*.yml`, `repository/*`,
`domain/model/*`.

### DAD-01 · Supabase compartilhado: schema próprio, usuário próprio
[confirmado · 2026-09] O plano gratuito já tem 2 projetos. O SmartKey mora
no projeto **Sistema_de_Gestao**, schema separado `smartkey`, usuário dedicado
`smartkey_app` sem acesso a outros schemas. **Nunca** mexer no projeto
`nemycookies` nem no schema `public` de Sistema_de_Gestao.
Por quê: o usuário perguntou explicitamente se os projetos foram misturados.

### DAD-02 · Conexão pelo Session Pooler (IPv4)
[documentado · 2026-09] A conexão direta do Supabase gratuito é só IPv6 e
falha em operadoras brasileiras. O pooler em uso é da região us-west-2 (o
arquivo de exemplo mostra sa-east-1 só como exemplo).
Onde: application-local.yml.example

### DAD-03 · Flyway manda no schema
[código · 2026-09] Migrações V1–V5 no schema `smartkey`; Hibernate com
`ddl-auto: validate` (não cria tabela). Mudança de tabela = nova migração
`V6__...sql`, nunca editar migração aplicada.

### DAD-04 · Migração nova exige atualizar o teste
[código · 2026-09] `PostgresIntegrationTest` confere que **5** migrações SQL
foram aplicadas com sucesso. Ao criar V6, atualizar para 6.

### DAD-05 · Perfis
[código · 2026-09] `local` (padrão; Supabase, arquivo ignorado pelo git),
`demo` (H2 em memória, create-drop, sem Flyway, dados de exemplo), `prod`
(Render, variáveis de ambiente), `pgtest` (testes com PostgreSQL embutido).

### DAD-06 · Diferenças H2 × PostgreSQL
[código · 2026-09 · armadilha real] H2 não tem `ON CONFLICT`; consulta nativa
ignora `hibernate.default_schema` (o nome do schema difere entre H2 e PG).
Evitar SQL nativo dependente de schema; preferir JPA. Tudo que envolve
concorrência precisa de teste no PG real (pgtest). Ver também CRI-07.

### DAD-07 · Relações carregadas com JOIN FETCH
[código · 2026-09 · armadilha real] `open-in-view: false`; consultas que
devolvem relações para a API usam JOIN FETCH (`findByIdWithGrants`,
`findAllWithDetails`), senão LazyInitializationException.
Onde: repository/*

### DAD-08 · Limpeza periódica
[documentado · 2026-09] Desafios e sorteios usados são apagados por tarefa
agendada a cada 15 min (antes nunca rodava).
Onde: MaintenanceJobs (@EnableScheduling em SmartKeyApplication)

### DAD-09 · Dados de exemplo com datas relativas
[documentado · 2026-09] O cenário demo usa ontem 15:00 → +3 dias 11:00 para
estar sempre "durante a estadia". Testes também derivam datas, nunca fixas.
Onde: DemoDataSeeder
