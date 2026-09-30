---
paths:
  - "pom.xml"
  - "Dockerfile"
  - "render.yaml"
  - ".github/**"
  - "src/test/**"
---
Antes de alterar, leia `docs/memoria/ambiente.md`.
Invariante principal: nada de Docker local; testes com banco usam PostgreSQL embutido; segredos nunca versionados.
