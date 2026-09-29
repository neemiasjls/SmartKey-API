/* ==========================================================================
   MINHA CHAVE — o aplicativo do hóspede

   Funciona instalado na tela inicial do Android e do iPhone, sem loja.

   O que acontece aqui:

     1. Na primeira abertura, cria um par de chaves criptográficas. A chave
        privada é marcada como NÃO EXPORTÁVEL: nem este código consegue lê-la.
     2. Envia ao servidor apenas a chave pública, usando o token de uso único
        que veio no link.
     3. Daí em diante, monta um QR Code novo a cada poucos segundos, assinado
        com a chave privada.

   O QR muda o tempo todo de propósito. Um print da tela não serve para nada
   alguns segundos depois: o código já venceu, e o servidor só aceita cada um
   uma única vez.
   ========================================================================== */

'use strict';

const DB_NAME = 'smartkey-key';
const STORE = 'vault';
const KEYPAIR_ID = 'keypair';
const CREDENTIAL_ID = 'credential';

const REFRESH_SECONDS = 20;   // menor que o limite aceito pelo servidor

let keyPair = null;
let credential = null;

/**
 * Diferença, em milissegundos, entre o relógio do servidor e o deste celular.
 *
 * O QR carrega o horário em que foi gerado, e o servidor recusa códigos com
 * mais de 45 segundos de diferença. Um celular com o relógio adiantado ou
 * atrasado teria TODO código recusado, sem explicação para o hóspede. Por
 * isso usamos a hora do servidor, e não a do aparelho.
 */
let clockOffsetMs = 0;
let secondsLeft = 0;
let ticker = null;

/* -------------------------------------------------------------------------
   Cofre local (IndexedDB)

   Guardamos o objeto da chave diretamente, ainda lacrado. O localStorage não
   serviria: ele só aceita texto, e transformar a chave em texto significaria
   torná-la legível — exatamente o que queremos evitar.
   ------------------------------------------------------------------------- */

function openDb() {
    return new Promise((resolve, reject) => {
        const request = indexedDB.open(DB_NAME, 1);
        request.onupgradeneeded = () => request.result.createObjectStore(STORE);
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
    });
}

async function vaultGet(key) {
    const db = await openDb();
    return new Promise((resolve, reject) => {
        const request = db.transaction(STORE, 'readonly').objectStore(STORE).get(key);
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
    });
}

async function vaultPut(key, value) {
    const db = await openDb();
    return new Promise((resolve, reject) => {
        const tx = db.transaction(STORE, 'readwrite');
        tx.objectStore(STORE).put(value, key);
        tx.oncomplete = () => resolve();
        tx.onerror = () => reject(tx.error);
    });
}

/* -------------------------------------------------------------------------
   Utilidades
   ------------------------------------------------------------------------- */

const $ = (sel) => document.querySelector(sel);

function show(sectionId) {
    ['setup', 'key', 'error'].forEach((id) => {
        $('#' + id).hidden = (id !== sectionId);
    });
}

function fail(message) {
    $('#error-msg').textContent = message;
    show('error');
}

function toUrlBase64(buffer) {
    return btoa(String.fromCharCode(...new Uint8Array(buffer)))
        .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function toStandardBase64(buffer) {
    return btoa(String.fromCharCode(...new Uint8Array(buffer)));
}

function formatDate(iso) {
    return new Date(iso).toLocaleString('pt-BR', {
        day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit'
    });
}

/* -------------------------------------------------------------------------
   Criptografia
   ------------------------------------------------------------------------- */

async function createKeyPair() {
    // O "false" é o ponto central: marca a chave privada como não exportável.
    const pair = await crypto.subtle.generateKey(
        { name: 'ECDSA', namedCurve: 'P-256' },
        false,
        ['sign', 'verify']
    );
    await vaultPut(KEYPAIR_ID, pair);
    return pair;
}

async function exportPublicKey(pair) {
    return toStandardBase64(await crypto.subtle.exportKey('spki', pair.publicKey));
}

/**
 * Monta o conteúdo do QR.
 *
 * Formato: SK1.<credencial>.<sorteio>.<segundos>.<assinatura>
 *
 * O texto realmente assinado é outro, mais explícito, e precisa bater
 * exatamente com o que o servidor reconstrói do outro lado.
 */
async function buildQrContent() {
    const nonceBytes = crypto.getRandomValues(new Uint8Array(16));
    const nonce = toUrlBase64(nonceBytes);
    const issuedAt = Math.floor((Date.now() + clockOffsetMs) / 1000);

    const message = 'SMARTKEY-QR-v1|' + credential.credentialId
        + '|' + nonce + '|' + issuedAt;

    const signature = await crypto.subtle.sign(
        { name: 'ECDSA', hash: 'SHA-256' },
        keyPair.privateKey,
        new TextEncoder().encode(message)
    );

    // O id da credencial viaja como 16 bytes, e não como texto de 36
    // caracteres: encurta bastante o QR e acelera a leitura pela câmera.
    const uuidHex = credential.credentialId.replace(/-/g, '');
    const uuidBytes = new Uint8Array(16);
    for (let i = 0; i < 16; i++) {
        uuidBytes[i] = parseInt(uuidHex.substr(i * 2, 2), 16);
    }

    return ['SK1', toUrlBase64(uuidBytes), nonce, issuedAt, toUrlBase64(signature)]
        .join('.');
}

/**
 * Acerta o relógio pela hora do servidor.
 *
 * Desconta metade do tempo de ida e volta da requisição: a resposta
 * foi gerada, em média, no meio do caminho.
 */
async function syncClock() {
    try {
        const sentAt = Date.now();
        const response = await fetch('/api/health', { cache: 'no-store' });
        const receivedAt = Date.now();
        const { time } = await response.json();

        const serverNow = new Date(time).getTime();
        clockOffsetMs = serverNow - (sentAt + receivedAt) / 2;
    } catch {
        clockOffsetMs = 0;   // sem rede: segue com o relógio do aparelho
    }
}

/* -------------------------------------------------------------------------
   Exibição do QR
   ------------------------------------------------------------------------- */

async function refreshQr() {
    try {
        const content = await buildQrContent();

        // O código vai no CORPO, e não na URL: endereços ficam gravados em
        // logs de servidor e de proxy, e este conteúdo abre uma porta.
        const response = await fetch('/api/keys/qr', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ data: content, size: 420 })
        });

        if (!response.ok) {
            throw new Error('Não foi possível desenhar o código.');
        }

        $('#qr-box').innerHTML = await response.text();
        $('#k-status').textContent = 'Código renovado agora';

        secondsLeft = REFRESH_SECONDS;

    } catch (error) {
        $('#k-status').textContent = 'Sem conexão — aproxime-se do Wi-Fi';
    }
}

async function startTicker() {
    await syncClock();
    clearInterval(ticker);
    secondsLeft = 0;

    ticker = setInterval(() => {
        secondsLeft--;
        $('#bar').style.width =
            Math.max(0, (secondsLeft / REFRESH_SECONDS) * 100) + '%';

        if (secondsLeft <= 0) {
            refreshQr();
        }
    }, 1000);

    refreshQr();
}

function renderCredential() {
    $('#k-guest').textContent = credential.guestName;
    $('#k-unit').textContent = credential.unitLabel;
    $('#k-from').textContent = formatDate(credential.validFrom);
    $('#k-until').textContent = formatDate(credential.validUntil);

    // Montado com textContent, e não com innerHTML: o nome da porta vem de
    // fora deste aparelho e não pode ser interpretado como HTML.
    const doors = $('#k-doors');
    doors.replaceChildren(...credential.accessPoints.map((p) => {
        const chip = document.createElement('span');
        chip.className = 'grant-chip';
        chip.textContent = p;
        return chip;
    }));
}

/* -------------------------------------------------------------------------
   Ativação
   ------------------------------------------------------------------------- */

async function activate(token) {
    const button = $('#activate');
    button.disabled = true;
    button.textContent = 'Ativando…';

    try {
        const pair = keyPair || await createKeyPair();
        keyPair = pair;

        const response = await fetch('/api/keys/enroll', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                token,
                publicKey: await exportPublicKey(pair),
                algorithm: 'ECDSA_P256',
                deviceLabel: $('#devname').value.trim() || 'Celular do hóspede'
            })
        });

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message || 'Falha na ativação.');
        }

        credential = data;
        await vaultPut(CREDENTIAL_ID, data);

        // Tira o token da barra de endereço: ele já foi usado, e não deve
        // ficar no histórico do navegador nem em prints de tela.
        history.replaceState(null, '', '/key.html');

        renderCredential();
        show('key');
        startTicker();

    } catch (error) {
        fail(error.message);
    } finally {
        button.disabled = false;
        button.textContent = 'Ativar chave';
    }
}

/* -------------------------------------------------------------------------
   Instalação na tela inicial
   ------------------------------------------------------------------------- */

let installPrompt = null;

window.addEventListener('beforeinstallprompt', (event) => {
    event.preventDefault();
    installPrompt = event;
    $('#install').hidden = false;
});

$('#install').addEventListener('click', async () => {
    if (!installPrompt) return;
    installPrompt.prompt();
    await installPrompt.userChoice;
    installPrompt = null;
    $('#install').hidden = true;
});

/* -------------------------------------------------------------------------
   Início
   ------------------------------------------------------------------------- */

$('#activate').addEventListener('click', () => {
    const token = sessionStorage.getItem('pendingToken');
    if (token) activate(token);
});

(async function start() {
    if (!window.isSecureContext || !crypto.subtle) {
        // A Web Crypto exige HTTPS. A exceção é localhost, tratado como seguro.
        return fail('Esta página precisa ser aberta por HTTPS (ou localhost) '
            + 'para poder criar a chave com segurança.');
    }

    if ('serviceWorker' in navigator) {
        navigator.serviceWorker.register('/sw.js').catch(() => { /* opcional */ });
    }

    keyPair = await vaultGet(KEYPAIR_ID);
    credential = await vaultGet(CREDENTIAL_ID);

    // O token vem depois do # justamente para não ir ao servidor nos registros
    // de acesso: o que está após o # nunca é enviado numa requisição.
    const token = location.hash.replace(/^#/, '').trim();

    if (credential && keyPair) {
        renderCredential();
        show('key');
        startTicker();
        return;
    }

    if (token) {
        sessionStorage.setItem('pendingToken', token);
        show('setup');
        return;
    }

    fail('Abra o link que você recebeu para ativar sua chave. '
        + 'Se já ativou em outro aparelho, peça um link novo.');
})();
