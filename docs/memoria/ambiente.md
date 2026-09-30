# Ambiente — máquina, build, testes, publicação

Arquivos: `pom.xml`, `Dockerfile`, `render.yaml`, `.github/workflows/tests.yml`,
`src/test/**`.

### AMB-01 · Sem Docker na máquina do usuário
[confirmado · 2026-09] Não sugerir docker-compose, Postgres local nem
Testcontainers. O `Dockerfile` existe só para o Render compilar **na nuvem**;
o usuário nunca roda comandos docker.

### AMB-02 · Máquina de desenvolvimento
[código · 2026-09] Windows 11; JDK 24 instalado compilando para Java 21 (LTS,
padrão dos servidores); Maven 3.9.16. Terminais: PowerShell e Git Bash.

### AMB-03 · Heredoc com aspas falha no Git Bash
[código · 2026-09 · armadilha real] Para scripts com aspas ou vários níveis
de escape, grave o script num arquivo no scratchpad e execute-o, em vez de
heredoc inline.

### AMB-04 · Testes
[código · 2026-09] `mvn -B test` (99 testes). Testes com banco usam
**PostgreSQL embutido** (zonky `embedded-postgres`, perfil `pgtest`): baixa o
binário como dependência Maven, sem Docker e sem Supabase. CI roda o mesmo no
GitHub a cada push. ⚠ ver PEN-04.
Onde: PostgresIntegrationTest

### AMB-05 · Rodar localmente
[documentado · 2026-09] Demo sem banco:
`mvn spring-boot:run -Dspring-boot.run.profiles=demo`. Com Supabase: perfil
`local` (padrão), exige `application-local.yml`. Celular: `ngrok http 8080` —
e ao expor o demo, definir `ADMIN_API_KEY` (demo123 é pública).

### AMB-06 · Onde ficam os segredos (nunca os valores)
[código · 2026-09] Local: `src/main/resources/application-local.yml` (no
`.gitignore`; modelo em `.example`). Produção: variáveis de ambiente do Render
(`render.yaml`, `sync: false`; `ADMIN_API_KEY` gerada). Não registrar senha,
chave de admin, chave de leitor nem o ref do projeto Supabase em arquivo
versionado.

### AMB-07 · Publicação gratuita
[confirmado · 2026-09] Alvo: Supabase (banco) + Render (API) + Cloudflare
(domínio/TLS). Configuração pronta, ainda não publicado (PEN-06). Limites do
plano grátis: README § Publicação.

### AMB-08 · Versão em três lugares
[código · 2026-09] Ao lançar versão: `pom.xml`, `OpenApiConfig` e
`CHANGELOG.md` (hoje 0.2.0).

### AMB-09 · Git e GitHub
[código · 2026-09] Repositório `github.com/neemiasjls/SmartKey-API`, branch
`main`. Commits com o e-mail noreply do GitHub já configurado no git local.
Push é feito pelo usuário (COL-02).
