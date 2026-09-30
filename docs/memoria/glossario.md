# Glossário

| Termo | Significado |
|---|---|
| **Reserva** | estadia de um hóspede num apartamento, com check-in e checkout |
| **Credencial** | chave digital de uma reserva para um aparelho; tem período e permissões |
| **Permissão (grant)** | direito de uma credencial numa porta; pode ter validade própria e ser removida sozinha |
| **Porta / ponto de acesso** | código como `entrada_condominio`, `apartamento_804`, `academia` |
| **Leitor (reader)** | dispositivo instalado numa porta (hoje: PWA com câmera); tem chave própria `rdr_…` |
| **Ativação (enrollment)** | registrar a chave pública do aparelho na credencial, via link com token de uso único |
| **Desafio (challenge)** | número criado pelo servidor a pedido do leitor, assinado pelo aparelho (canal NFC) |
| **Sorteio (nonce)** | número aleatório de uso único; no QR é gerado pelo aparelho |
| **Canal QR** | QR assinado que troca a cada 20 s, lido pela câmera do leitor |
| **Canal NFC** | desafio/assinatura por aproximação; backend pronto, apps pendentes |
| **Relógio simulado** | parâmetro `at` para testar datas; só com ALLOW_TIME_TRAVEL |
| **Perfis** | `local`, `demo`, `prod`, `pgtest` — ver DAD-05 |
| **Chave de admin** | cabeçalho `x-admin-key`; controle total |
