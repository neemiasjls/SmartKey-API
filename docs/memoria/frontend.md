# Frontend — painel e PWAs

Código: `src/main/resources/static/` — `index.html`/`app.js` (painel),
`key.html`/`key.js` (chave do hóspede), `reader.html`/`reader.js` (leitor),
`phone.js`, `sw.js`, manifests e `icons/`.

### FRO-01 · Tudo pelo navegador, sem instalar app
[confirmado · 2026-09] O usuário não quer app de loja no celular. Chave e
leitor são PWA instaláveis na tela inicial (Android e iPhone).
Ver PRO-03 para NFC.

### FRO-02 · CSP proíbe script inline
[código · 2026-09] Nada de `<script>` inline nem `onclick=`; todo JS em
arquivo `.js` servido pelo próprio servidor. Recursos externos (CDN) também
são bloqueados pela CSP.
Onde: SecurityConfig

### FRO-03 · Dado externo nunca vira HTML
[documentado · 2026-09] Conteúdo vindo do servidor entra com `textContent`
ou escapado (ex.: nomes das portas no app do hóspede).

### FRO-04 · Service worker: subir a versão ao mudar estáticos
[código · 2026-09] `sw.js` usa `CACHE = 'smartkey-vN'` (hoje v2). Ao alterar
arquivos de `static/`, incrementar, senão celulares continuam com a versão
antiga. O sw **nunca** guarda respostas de `/api`.

### FRO-05 · Web Crypto exige HTTPS no celular
[confirmado · 2026-09] Em `http://IP-local` a Web Crypto não existe. Testar
no celular via ngrok (`ngrok http 8080`) ou já publicado. Ver AMB-05.

### FRO-06 · Chave do hóspede não exportável
[documentado · 2026-09] Gerada com Web Crypto como não exportável e guardada
no IndexedDB; nem o próprio JS consegue lê-la, só pedir assinatura.

### FRO-07 · Leitor usa BarcodeDetector
[código · 2026-09] O leitor lê o QR pela câmera com a API BarcodeDetector e
se autentica com `x-reader-key`. O console do navegador embutido do Claude
mostra erro de service worker — limitação do navegador embutido, não do app.
