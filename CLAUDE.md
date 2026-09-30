# SmartKey — instruções para o Claude

Chave digital temporária para hospedagens: reserva → credencial por aparelho →
permissões por porta; QR assinado (ECDSA P-256) num PWA, anti-repetição pelo
banco. Java 21 + Spring Boot 3.5, PostgreSQL (Supabase), Flyway. Versão 0.2.0.
Usuário iniciante: responda em português simples (ver `docs/memoria/colaboracao.md`).

## Comandos

- Testes: `mvn -B test` (PostgreSQL embutido, sem Docker)
- Demo sem banco: `mvn spring-boot:run -Dspring-boot.run.profiles=demo` → http://localhost:8080
- Com Supabase: `mvn spring-boot:run` (exige `src/main/resources/application-local.yml`)

## Invariantes críticas (nunca quebrar)

1. Chave privada nunca sai do aparelho; o servidor só vê a pública. (CRI-01)
2. Não inventar criptografia: só algoritmos e bibliotecas padrão. (CRI-01)
3. Toda autorização pelo Spring Security; nunca comparar caminho cru. (SEG-01)
4. Endpoint novo nasce fechado: exige regra em `SecurityConfig` + teste. (SEG-02)
5. Token/segredo nunca em URL; vai no corpo ou em cabeçalho. (CRE-02)
6. Erro do cliente é 4xx; qualquer 500 é bug. (SEG-08)
7. Sorteio do QR: `persist()` em transação própria, antes da assinatura. (CRI-06, CRI-07)
8. Relógio simulado só afeta regra de negócio, nunca validade criptográfica. (ACE-07)
9. Mudança de tabela = nova migração Flyway + atualizar contagem no teste. (DAD-03, DAD-04)
10. Nada de Docker local; nunca tocar em outros projetos/schemas do Supabase. (AMB-01, DAD-01)
11. Nenhum segredo em arquivo versionado — só onde é configurado. (AMB-06)

## Memória do projeto — índice

Fonte de verdade: `docs/memoria/`. Leia **só** o arquivo do domínio da tarefa.

| Domínio | Arquivo | Leia quando… |
|---|---|---|
| Acesso | `acesso.md` | regras de liberação/recusa, períodos, portas |
| Criptografia | `criptografia.md` | assinatura, QR, NFC, anti-repetição |
| Credenciais | `credenciais.md` | reserva, ativação, aparelho, revogação |
| Segurança web | `seguranca-web.md` | endpoints, chaves, autorização, erros HTTP |
| Dados | `dados.md` | migrações, consultas, Supabase, H2 × PG |
| Frontend | `frontend.md` | painel, key/reader PWA, sw.js, CSP |
| Ambiente | `ambiente.md` | build, testes, deploy, git, segredos |
| Produto | `produto.md` | especificação original, roadmap, Wallet/NFC |
| Colaboração | `colaboracao.md` | como responder e agir com o usuário |
| Glossário | `glossario.md` | dúvida sobre um termo |
| Pendências | `pendencias.md` | retomar trabalho, "onde paramos", conflitos |

Formato, etiquetas e orçamentos: `docs/memoria/README.md`.

## Protocolo de memória (sempre ativo)

1. Se o usuário corrigir, decidir, explicar regra, exceção, preferência ou
   dependência — ou uma descoberta técnica custar depuração — teste:
   durável por 1 mês? impossível deduzir do código em 2 min? veio do usuário,
   de uma decisão ou de armadilha real? Grave só se passar nas três.
2. Índice → UM arquivo → Grep por termos/IDs → edite a entrada existente; crie
   nova (próximo ID livre) só se não houver equivalente. Sem releitura geral,
   sem subagente.
3. Etiqueta correta (`confirmado` só se veio do usuário); nunca promova
   inferência do código a `confirmado`.
4. Conflito com algo `confirmado`: não altere a regra; registre DIV em
   `pendencias.md` e pergunte se for relevante agora. Código diferente de
   regra confirmada = possível bug, não regra nova.
5. Regra mudou: reescreva o estado atual; "Mudou em AAAA-MM (antes: …)" só se
   o motivo evita repetir um erro. Nunca apague `confirmado` sem avisar.
6. Nunca grave senha, token, chave, connection string com credencial nem o
   ref do projeto Supabase.
7. Avise em uma linha: `Memória: <ID> <criada|atualizada> (<etiqueta>)`.
8. No fim de uma fase ou etapa grande, ofereça `/memoria salvar`.
9. Não faça commit da memória sozinho; o usuário decide.

## Roteamento de modelos neste projeto

Vale a regra global (classificar SIMPLES/COMPLEXA e dizer em uma linha).
Exceção aprovada: gravar memória e `/memoria salvar` são feitos inline — o
subagente não vê a conversa. `/memoria revisar` pode ir para `complex-tasks`.

## Compact instructions

Ao resumir a conversa, inclua uma seção própria chamada
"Conhecimento duradouro ainda não gravado em docs/memoria", listando cada
item (regra, decisão, correção do usuário, armadilha) com a etiqueta sugerida
e o arquivo de domínio. Se não houver nenhum, escreva "nenhum".
