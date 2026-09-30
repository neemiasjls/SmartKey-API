# Criptografia e anti-repetição

Código: `domain/crypto/*`, `service/ChallengeService`, `service/QrAccessService`,
`service/QrNonceClaimer`, `web/controller/SecureAccessController`.
Visão geral oficial: `docs/SEGURANCA.md` § Ameaças e respostas.

### CRI-01 · Requisitos inegociáveis
[confirmado · 2026-09 · especificação original] Nada de código NFC fixo;
nunca inventar algoritmo criptográfico (só bibliotecas e algoritmos
conhecidos); chave privada gerada e mantida no aparelho, **nunca** enviada ao
servidor; desafio/sorteio usado não pode ser aceito de novo.

### CRI-02 · ECDSA P-256 padrão, Ed25519 aceito
[documentado · 2026-09] P-256 tem armazenamento em hardware no Android desde
a versão 6 e existe no Web Crypto de todos os navegadores; Ed25519 só no
Keystore do Android 13+ e depende do fabricante. O servidor verifica os dois.
Onde: SignatureAlgorithm, SignatureVerifier · teste: SignatureVerifierTest

### CRI-03 · Assinatura Web Crypto vem em raw (r‖s)
[código · 2026-09] O navegador entrega a assinatura ECDSA em formato raw; o
servidor converte para DER antes de verificar com a JCA.
Onde: SignatureVerifier

### CRI-04 · Textos assinados com prefixo de domínio
[documentado · 2026-09] QR: `SMARTKEY-QR-v1|credencial|sorteio|horário`.
NFC: `SMARTKEY-ACCESS-v1|desafio|sorteio|porta|credencial`. Prefixos
diferentes impedem reaproveitar a assinatura em outro canal ou porta.
Onde: SignedMessage, QrPayload

### CRI-05 · Formato e tempos do QR
[código · 2026-09] Conteúdo `SK1.<credencial b64url>.<sorteio>.<epochSeg>.<assinatura>`.
O app troca o QR a cada 20 s (`REFRESH_SECONDS` em key.js); o servidor aceita
até 45 s de diferença (`qrTtlSeconds`). O sorteio segue `NONCE_FORMAT`
(22–43 caracteres base64url).
Onde: QrPayload, SmartKeyProperties, static/key.js

### CRI-06 · Anti-repetição garantida pelo banco
[documentado · 2026-09] Desafio NFC: consumido com UPDATE condicional
(`usedAt IS NULL`). Sorteio do QR: INSERT com chave primária; a 2ª gravação
falha. O sorteio é reservado **antes** de conferir a assinatura.
Por quê: impede usar o mesmo código para testar assinaturas repetidamente.
Onde: ChallengeRepository.consume, QrNonceClaimer · teste: QrAccessFlowTest, PostgresIntegrationTest

### CRI-07 · A reserva do sorteio exige persist() em transação própria
[documentado · 2026-09 · armadilha real] `save()` com id atribuído vira
merge → UPDATE e a proteção não protegia nada. Usar `EntityManager.persist()`
em `REQUIRES_NEW`: no PostgreSQL um erro aborta a transação inteira. Capturar
`DataIntegrityViolationException` **e** `org.hibernate...ConstraintViolationException`.
Onde: QrNonceClaimer · teste: PostgresIntegrationTest

### CRI-08 · Relógio do celular é ajustado pelo servidor
[documentado · 2026-09] O app calcula a diferença com `/api/health` e assina
com a hora do servidor; celular com hora errada tinha todo QR recusado.
Onde: static/key.js, HealthController
