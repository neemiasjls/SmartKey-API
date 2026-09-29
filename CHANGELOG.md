# Histórico de versões

## 0.2.0 — setembro de 2026

Revisão completa de segurança e qualidade. Detalhes em
[`docs/SEGURANCA.md`](docs/SEGURANCA.md#histórico-de-auditoria).

### Segurança
- Autenticação e autorização migradas para o Spring Security. Corrige o
  contorno do controle de acesso por variações de endereço (`/api;/admin`).
- Chave própria para cada leitor, confinada à sua porta; troca individual.
- Gerador de QR recebe o conteúdo por `POST`: tokens deixam de aparecer em logs.
- Limite de tentativas usa o IP do proxy confiável (`TRUSTED_PROXY_HOPS`).
- Content-Security-Policy e demais cabeçalhos de proteção.
- Console do H2 desligado no modo demonstração.
- Ativação recusada para credencial revogada, cancelada ou vencida.
- Validação dos códigos de porta; conteúdo externo nunca inserido como HTML.

### Correções
- A proteção contra repetição do QR não barrava repetições (`UPDATE` em vez de
  `INSERT`).
- Erros do cliente retornavam 500.
- Limpeza de desafios e códigos usados nunca executava.
- Celular com relógio errado tinha todo QR recusado.
- Cenário de demonstração fixo numa data passada; agora acompanha o dia atual.

### Testes
- De 75 para 99 testes, incluindo PostgreSQL real (sem Docker) e regressão de
  cada vulnerabilidade encontrada.
- Execução automática no GitHub a cada envio.

## 0.1.0 — setembro de 2026

- Regras de acesso por período, porta e revogação.
- Painel administrativo web.
- Assinatura ECDSA P-256 com desafio e proteção contra repetição.
- Chave do hóspede e leitor como aplicativos web instaláveis (PWA), canal QR.
