# Pendências, divergências e perguntas abertas

Itens `pendente`/`hipótese` e divergências (DIV). Ao resolver, mova a
conclusão para o arquivo de domínio e apague daqui.

## Aguardando resposta do usuário

- **PEN-01** [pendente · 2026-09] Verificar se um upload anterior feito pela
  web do GitHub expôs o `application-local.yml` (arquivos e histórico). Se
  sim: trocar a senha do `smartkey_app` e a chave de admin.
- **PEN-02** [pendente · 2026-09] O usuário perguntou "não tem nenhum
  conteúdo ou arquivo sensível?". A varredura antes do push não achou
  segredos nos arquivos versionados; falta auditar o repositório remoto.
- **PEN-03** [pendente · 2026-09] O GitHub avisa que a `main` não está
  protegida. Decidir se ativa proteção de branch.
- **PEN-04** [pendente · 2026-09] Confirmar que testes com PostgreSQL
  embutido (AMB-04) estão ok. A Auto Memory antiga dizia "evitar testes com
  banco ou rodar contra o Supabase" — decisão técnica mudada sem confirmação.
- **PEN-05** [pendente · 2026-09] O usuário perguntou como testar
  fisicamente em Android e iOS (celular real, Wallet). Sem conclusão.

## Trabalho futuro

- **PEN-06** [pendente · 2026-09] Publicar no Render (fase 8).
- **PEN-07** [pendente · 2026-09] Apps Android nativos NFC/HCE (PRO-03) e
  ESP32 (PRO-05).
- **PEN-08** [documentado · 2026-09] Riscos abertos: retransmissão em tempo
  real; admin sem contas individuais; chave de admin em localStorage (ideal:
  cookie HttpOnly); RateLimiter só em memória; QR sem uso offline.

## Divergências

Nenhuma registrada.
