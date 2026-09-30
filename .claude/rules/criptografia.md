---
paths:
  - "src/main/java/com/smartkey/domain/crypto/**"
  - "src/main/java/com/smartkey/service/ChallengeService.java"
  - "src/main/java/com/smartkey/service/QrAccessService.java"
  - "src/main/java/com/smartkey/service/QrNonceClaimer.java"
  - "src/main/java/com/smartkey/web/controller/SecureAccessController.java"
---
Antes de alterar, leia `docs/memoria/criptografia.md`.
Invariante principal: sem criptografia inventada; sorteio reservado com persist() em transação própria ANTES de conferir a assinatura.
