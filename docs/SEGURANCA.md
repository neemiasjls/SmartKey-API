# Segurança do SmartKey

Este documento descreve contra o que o sistema se protege, como, e o que ainda
não está coberto. Serve para quem for revisar, operar ou continuar o projeto.

---

## Ativos protegidos

| Ativo | Por que importa |
|---|---|
| Acesso físico às unidades | é o propósito do sistema |
| Chave privada do hóspede | com ela, qualquer um abre as portas daquela reserva |
| Token de ativação | permite registrar uma chave própria numa credencial alheia |
| Chave de administração | controle total: reservas, portas, histórico |
| Chave de leitor | permite consultar acesso em nome de uma porta |
| Dados pessoais dos hóspedes | nome, e-mail, telefone, rotina de entradas e saídas |

---

## Ameaças e respostas

### Clonar ou capturar a chave do hóspede

A chave privada é gerada no aparelho como **não exportável** — nem o próprio
código da página consegue lê-la; só pedir que ela assine. O servidor guarda
apenas a chave pública, que serve para conferir, nunca para produzir
assinaturas.

**Algoritmo: ECDSA P-256.** O critério foi onde a chave consegue morar: P-256
tem suporte a armazenamento em hardware no Android desde a versão 6, e no Web
Crypto de todos os navegadores. Ed25519 só chegou ao Android Keystore na versão
13 e depende do fabricante. O servidor aceita ambos.

### Repetir um código capturado (replay)

Todo código carrega um número sorteado que só é aceito **uma vez**. A garantia
é do banco: o número é gravado como chave primária, e a segunda gravação falha
numa operação atômica — duas leituras simultâneas não conseguem passar as duas.

A reserva do número acontece **antes** de conferir a assinatura, para que
ninguém use um mesmo código para testar assinaturas repetidamente.

No PostgreSQL, qualquer erro inutiliza a transação em curso. Por isso a reserva
roda numa transação separada, e o teste `PostgresIntegrationTest` prova esse
comportamento no banco real.

### Reaproveitar uma resposta em outra porta ou outro canal

O texto assinado inclui um prefixo de protocolo, o código da porta e o da
credencial:

```
QR:   SMARTKEY-QR-v1     | credencial | sorteio | horário
NFC:  SMARTKEY-ACCESS-v1 | desafio | sorteio | porta | credencial
```

Prefixos diferentes impedem que uma assinatura de um canal valha no outro.

### Usar um print da tela

O QR muda a cada 20 segundos e o servidor só aceita códigos com até 45 segundos
de diferença do relógio dele. O aplicativo acerta o próprio relógio pela hora do
servidor, para que um celular com horário errado não tenha todo código recusado.

### Sequestrar a ativação de uma chave

O link de ativação traz um token de 24 bytes sorteados, de uso único, que queima
na primeira ativação. Ele:

- fica depois do `#` no link, parte que o navegador nunca envia ao servidor;
- é enviado no **corpo** das requisições, nunca na URL — URLs ficam em logs de
  servidor e de proxy;
- não ativa nada se a credencial tiver sido revogada, a reserva cancelada ou a
  estadia encerrada.

### Leitor comprometido

Cada leitor tem chave própria, e o servidor guarda apenas o hash dela. Um leitor
só consegue perguntar sobre a **própria** porta (`ReaderScopeGuard`), não lê
histórico e não tem acesso administrativo. A chave pode ser trocada
individualmente, invalidando a anterior na hora.

Sem o confinamento por porta, a chave de um leitor de fácil acesso (academia)
permitiria pedir desafios em nome de um apartamento e montar um ataque de
retransmissão.

### Força bruta e excesso de requisições

Todas as chaves têm 32 bytes sorteados; adivinhar é inviável. Ainda assim há
limites por IP em falhas de autenticação, ativações e desenho de QR — para
proteger o processamento do servidor e deixar rastro de tentativas.

O IP considerado é o que o **proxy confiável** acrescentou ao `X-Forwarded-For`,
contado a partir do final. O início do cabeçalho é escrito pelo próprio cliente
e não é usado.

### Contornar a autorização por variações de endereço

Toda autorização é decidida pelo Spring Security, sobre o mesmo caminho
normalizado que o roteador usa. Endereços ambíguos (`;`, `//`, `%2F`) são
recusados antes. A última regra nega tudo: um endpoint novo nasce fechado.

### Injeção de conteúdo no navegador

Dados vindos do servidor são inseridos com `textContent` ou escapados. A
Content-Security-Policy impede a execução de qualquer script que não venha do
próprio servidor, mesmo que algum dado escape para o HTML.

---

## Recursos de desenvolvimento

Desligados por padrão e **nunca** devem ser ligados em produção:

| Recurso | Risco se ligado em produção |
|---|---|
| `ALLOW_TIME_TRAVEL` | o cliente escolhe o "agora" e finge estar dentro da estadia |
| `ALLOW_INSECURE_CHECK` | abre portas só com o id da credencial, sem assinatura |
| perfil `demo` | chaves conhecidas (`demo123`, `demo_reader_*`) |

O relógio simulado nunca afeta a criptografia: validade de desafios e códigos
usa sempre o relógio real.

---

## Riscos conhecidos e não tratados

- **Dependência de rede.** A decisão é sempre do servidor. Uma fechadura física
  precisará validar localmente e sincronizar revogações.
- **Ataque de retransmissão em tempo real (relay).** Um atacante com dois
  aparelhos poderia retransmitir o QR de um hóspede para outra porta *em tempo
  real*, dentro da janela de segundos. Mitigações possíveis: exigir interação
  do hóspede para exibir o código, ou usar NFC com medição de tempo de resposta.
- **Administração sem contas individuais.** Uma chave compartilhada impede
  auditoria de quem fez cada alteração.
- **Chave de administração no navegador** (`localStorage`). Um script
  malicioso na página a leria — a CSP reduz esse risco, mas o ideal é sessão
  com cookie `HttpOnly`.

---

## Histórico de auditoria

### Setembro de 2026

| Gravidade | Problema | Correção |
|---|---|---|
| Crítica | `/api;/admin/...` e `/api/%61dmin/...` davam acesso administrativo completo sem chave: leitura de dados pessoais, criação de registros e roubo de tokens de ativação | autorização migrada para o Spring Security (endereço normalizado, negar por padrão, firewall de URLs) |
| Alta | o token de ativação era enviado na URL do gerador de QR, ficando em logs | geração de QR por `POST`, conteúdo no corpo |
| Alta | console do banco H2 exposto no modo demonstração | desligado |
| Alta | limite de tentativas usava o IP informado pelo cliente, contornável | uso do IP acrescentado pelo proxy confiável |
| Alta | a proteção contra repetição do QR fazia `UPDATE` em vez de `INSERT` e não barrava nada | `persist()` em transação própria, provado no PostgreSQL real |
| Média | erros do cliente (JSON inválido, id malformado, rota inexistente) retornavam 500 | tratamento específico por tipo de erro |
| Média | limpeza de desafios e códigos usados nunca executava | tarefa agendada a cada 15 minutos |
| Média | códigos de porta sem validação, inseridos sem escape no app do hóspede | validação no servidor e `textContent` no cliente |
| Média | link de ativação funcionava com a credencial revogada | recusado |
| Média | celular com relógio errado tinha todo QR recusado | sincronização pela hora do servidor |
| Média | migrações nunca haviam rodado em PostgreSQL | teste com PostgreSQL real |
| Baixa | formato do número sorteado do QR não validado; texto do desafio corrompível; dica da chave de teste exibida em produção; busca por chave de leitor sem índice | corrigidos |

Cada item tem um teste de regressão em `SecurityHardeningTest`,
`RateLimitTest`, `QrAccessFlowTest` ou `PostgresIntegrationTest`.
