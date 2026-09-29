/* ==========================================================================
   Service Worker

   E o que permite o aplicativo ser INSTALADO na tela inicial do celular e
   abrir mesmo com a internet ruim.

   Estrategia: "rede primeiro, cache como reserva".

   Por que nao o contrario: uma fechadura nunca pode responder com resposta
   guardada. Entao as chamadas a API (/api/) ficam FORA do cache, sempre.
   O cache guarda apenas a aparencia do app - telas, estilos, icones.
   ========================================================================== */

const CACHE = 'smartkey-v2';

const SHELL = [
  '/key.html',
  '/reader.html',
  '/styles.css',
  '/icons/key-192.png',
  '/icons/reader-192.png'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE)
      .then((cache) => cache.addAll(SHELL))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((names) => Promise.all(
        names.filter((n) => n !== CACHE).map((n) => caches.delete(n))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);

  // NUNCA guardar respostas da API. Uma decisao de acesso guardada em cache
  // poderia liberar uma porta que ja foi revogada.
  if (url.pathname.startsWith('/api/')) {
    return;
  }

  if (event.request.method !== 'GET') {
    return;
  }

  event.respondWith(
    fetch(event.request)
      .then((response) => {
        const copy = response.clone();
        caches.open(CACHE).then((cache) => cache.put(event.request, copy));
        return response;
      })
      .catch(() => caches.match(event.request))
  );
});
