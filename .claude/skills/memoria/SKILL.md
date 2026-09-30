---
description: Mantém a memória versionada do projeto em docs/memoria. Use em "/memoria salvar", "salvar memória", "checkpoint" (antes de /clear, fim de fase) e "/memoria revisar", "revisar memória", "limpar memória" (manutenção periódica).
argument-hint: salvar | revisar
---

# Memória do SmartKey

Modo pedido: **$ARGUMENTS** (se vazio, pergunte: salvar ou revisar).
Regras de formato, etiquetas e orçamentos: `docs/memoria/README.md`.
Protocolo do dia a dia: seção "Protocolo de memória" do `CLAUDE.md`.

## Modo `salvar` — checkpoint (sempre inline, nunca subagente)

1. Percorra a conversa desde o último checkpoint (ou desde o início) e liste
   candidatos: correções do usuário, decisões, regras, exceções, preferências,
   dependências, armadilhas que custaram depuração, pendências novas.
2. Para cada candidato aplique o teste das 3 perguntas (durável 1 mês?
   impossível deduzir do código em 2 min? veio do usuário/decisão/armadilha?).
3. Compare **só** com o índice do `CLAUDE.md` + Grep pontual no arquivo do
   domínio (termos e IDs). Não releia a memória inteira.
4. Grave: edite a entrada equivalente ou crie a próxima com ID livre. Etiqueta
   correta; `confirmado` só se veio do usuário. Pendências → `pendencias.md`;
   conflitos com `confirmado` → DIV em `pendencias.md` + pergunta ao usuário.
5. Mostre ao usuário uma lista curta: gravado (ID, etiqueta), descartado (e
   por quê), perguntas abertas. Não faça commit.

## Modo `revisar` — manutenção (pode ser delegado a `complex-tasks`)

1. Contar linhas: `CLAUDE.md` ≤100, cada domínio ≤150, `pendencias.md` ≤40
   itens, entradas ≤6 linhas. Estourou → consolidar ou dividir o arquivo
   (ex.: `criptografia-qr.md` + `criptografia-nfc.md`) e atualizar o índice
   do `CLAUDE.md` e os `paths` em `.claude/rules/`.
2. IDs duplicados e entradas equivalentes entre arquivos → fundir, manter o
   ID mais antigo e corrigir referências cruzadas.
3. Contradições entre entradas → se ambas `confirmado`, criar DIV e perguntar;
   senão, prevalece a de etiqueta mais forte e mais recente.
4. Entradas `código`: conferir com Glob/Grep se classes, arquivos e valores
   citados ainda existem; corrigir ou remover as obsoletas.
5. Links e caminhos citados (arquivos, seções do README/SEGURANCA) existem.
6. Pendências resolvidas → mover conclusão para o domínio e apagar de
   `pendencias.md`.
7. `.claude/rules/*`: cada glob ainda casa com arquivos reais.
8. Varredura de segredos em `CLAUDE.md`, `docs/memoria/`, `.claude/`:
   `password`, `secret`, `api-key` com valor, `rdr_` seguido de valor,
   `jdbc:` com usuário/senha, `pooler.supabase.com` com ref de projeto,
   `postgres.<ref>`. Resultado precisa ser zero.
9. **Nunca** remover entrada `confirmado` sem perguntar ao usuário.
10. Atualizar "Última revisão:" em `docs/memoria/README.md` e relatar o que
    mudou (arquivos, entradas fundidas/removidas, perguntas).
