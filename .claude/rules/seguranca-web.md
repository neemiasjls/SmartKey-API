---
paths:
  - "src/main/java/com/smartkey/config/**"
  - "src/main/java/com/smartkey/web/error/**"
  - "src/main/java/com/smartkey/web/ReaderScopeGuard.java"
  - "src/main/java/com/smartkey/web/controller/ReaderController.java"
---
Antes de alterar, leia `docs/memoria/seguranca-web.md` (e `docs/SEGURANCA.md` se mexer em ameaças).
Invariante principal: autorização só pelo Spring Security; endpoint novo nasce fechado; erro do cliente nunca é 500.
