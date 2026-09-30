# Memória do projeto

Conhecimento duradouro do SmartKey que **não dá para deduzir lendo o código**:
regras confirmadas pelo usuário, decisões e seus motivos, armadilhas técnicas,
restrições de ambiente e pendências. Existe para que qualquer sessão nova do
Claude (ou pessoa) continue o trabalho sem depender do histórico de conversa.

Última revisão: 2026-09-30 (criação)

## Mapa

| Arquivo | Conteúdo | Prefixo |
|---|---|---|
| [acesso.md](acesso.md) | regras de liberação/recusa, períodos, portas, relógios | ACE |
| [criptografia.md](criptografia.md) | assinatura, formatos QR/NFC, anti-repetição | CRI |
| [credenciais.md](credenciais.md) | reserva → credencial → aparelho → ativação → revogação | CRE |
| [seguranca-web.md](seguranca-web.md) | Spring Security, chaves, limite de tentativas, erros | SEG |
| [dados.md](dados.md) | Flyway, Supabase, H2 × PostgreSQL, JPA | DAD |
| [frontend.md](frontend.md) | painel, chave e leitor (PWA), CSP, service worker | FRO |
| [ambiente.md](ambiente.md) | máquina, build, testes, publicação, git, segredos | AMB |
| [produto.md](produto.md) | especificação original, objetivos, roadmap | PRO |
| [colaboracao.md](colaboracao.md) | como trabalhar com o usuário | COL |
| [glossario.md](glossario.md) | termos do domínio | — |
| [pendencias.md](pendencias.md) | a confirmar, divergências, perguntas abertas | PEN, DIV |

## Formato de uma entrada

```
### XXX-00 · Título curto
[etiqueta · AAAA-MM · origem] Regra ou fato em 1–3 linhas.
Por quê: motivo, se não for óbvio.
Onde: Classe · teste: ClasseDeTeste
```

IDs são estáveis e nunca reaproveitados. Sem números de linha e sem trechos de
código (ficam desatualizados); aponte para a classe ou para o documento.

## Etiquetas de confiança

| Etiqueta | Significa |
|---|---|
| `confirmado` | o usuário disse ou aprovou explicitamente |
| `documentado` | está no README/SEGURANCA, ou decisão técnica documentada e não contestada |
| `código` | lido no código ou nos testes; o usuário não validou |
| `hipótese` | suposição ainda não verificada |
| `pendente` | aguardando resposta do usuário |

Só o usuário promove algo para `confirmado`. Na dúvida, use a etiqueta mais
fraca. Código divergente de regra confirmada é **possível bug**, não regra nova.

## Orçamentos

`CLAUDE.md` ≤ 100 linhas · arquivo de domínio ≤ 150 linhas · entrada ≤ 6
linhas · `pendencias.md` ≤ 40 itens. Estourou → `/memoria revisar` consolida
ou divide o arquivo.

## Como a memória é mantida

- **No dia a dia**: o protocolo do `CLAUDE.md` grava na hora em que o
  conhecimento aparece — edição pequena, em um único arquivo.
- **Carregamento por domínio**: `.claude/rules/*.md` têm `paths:` e só entram
  no contexto quando o Claude lê arquivos daquele domínio; eles apontam para o
  arquivo de memória correspondente.
- **`/memoria salvar`**: checkpoint antes de `/clear`, no fim de uma fase ou
  quando o usuário pedir.
- **`/memoria revisar`**: manutenção (duplicações, contradições, obsoletos,
  orçamentos, segredos). No fim de cada versão/fase ou quando um orçamento
  estourar. Nunca a cada mensagem.
- **Compactação**: o `CLAUDE.md` tem instruções para o resumo listar o que
  ainda não foi gravado, e um hook `SessionStart` (matcher `compact`) lembra de
  gravar depois.

## O que NÃO entra

Logs, saídas de terminal, erros temporários, investigações descartadas,
histórico da conversa, código copiado, o que se deduz lendo o código em poucos
minutos, e **nunca** senhas, tokens, chaves ou connection strings — registre
apenas **onde** são configurados.

## Relação com os outros documentos

`README.md` (uso), `docs/SEGURANCA.md` (modelo de ameaças) e `CHANGELOG.md`
(histórico) continuam sendo as fontes oficiais. A memória **aponta** para eles
em vez de copiar. O histórico completo de mudanças de regra é o `git log`.

## Auto Memory do Claude

A Auto Memory local (`~/.claude/projects/.../memory/`) é só auxiliar e aponta
para cá. Conhecimento do projeto vive neste diretório, versionado.
