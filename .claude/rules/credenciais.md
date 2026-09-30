---
paths:
  - "src/main/java/com/smartkey/service/KeyEnrollmentService.java"
  - "src/main/java/com/smartkey/service/AdminService.java"
  - "src/main/java/com/smartkey/domain/model/{Credential,Device,AccessGrant,Reservation,Guest}.java"
  - "src/main/java/com/smartkey/web/controller/{Credential,Device,Reservation,Guest,Key}Controller.java"
---
Antes de alterar, leia `docs/memoria/credenciais.md`.
Invariante principal: token de ativação de uso único, nunca na URL; servidor só recebe a chave pública.
