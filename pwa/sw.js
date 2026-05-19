'use strict';

const CACHE_VERSION = 'calc-pwa-v2';
const ASSETS = [
  './',
  'index.html',
  'style.css',
  'parser.js',
  'progparser.js',
  'catalog.js',
  'toolkit.js',
  'app.js',
  'manifest.json',
  'icon.svg',
  'icon-180.png',
  'icon-192.png',
  'icon-512.png'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_VERSION).then((cache) => {
      // best-effort cache (some assets may 404 if not yet generated, that's OK)
      return Promise.allSettled(ASSETS.map(a => cache.add(a)));
    })
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter(k => k !== CACHE_VERSION).map(k => caches.delete(k)))
    )
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET') return;
  event.respondWith(
    caches.match(event.request).then((cached) => {
      if (cached) return cached;
      return fetch(event.request).then((resp) => {
        if (resp && resp.status === 200 && resp.type === 'basic') {
          const clone = resp.clone();
          caches.open(CACHE_VERSION).then(cache => cache.put(event.request, clone));
        }
        return resp;
      }).catch(() => cached);
    })
  );
});
