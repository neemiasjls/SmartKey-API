/* ==========================================================================
   O NAVEGADOR COMO A CHAVE DO HÓSPEDE

   Este arquivo faz, no navegador, exatamente o que o aplicativo Android vai
   fazer no celular:

     1. gera um par de chaves criptográficas
     2. guarda a chave PRIVADA aqui, sem nunca revelá-la
     3. envia ao servidor apenas a chave PÚBLICA
     4. assina os desafios que o leitor apresenta

   A peça central é a Web Crypto API, que já vem em todos os navegadores.
   É a prima da Android Keystore: a chave é criada com "extractable: false",
   ou seja, nem o próprio código JavaScript consegue lê-la depois. Dá para
   pedir que ela assine, e só.
   ========================================================================== */

'use strict';

const DB_NAME = 'smartkey-phone';
const STORE = 'keys';
const KEY_ID = 'device-key';

let lastAttempt = null;   // guarda o último desafio, para o botão de repetição

/* -------------------------------------------------------------------------
   Guardar a chave: IndexedDB, e não localStorage

   O localStorage só aceita texto - e para gravar a chave lá teríamos que
   exportá-la, ou seja, torná-la legível. Isso destruiria justamente a
   proteção que buscamos.

   O IndexedDB aceita guardar o objeto CryptoKey diretamente, ainda lacrado.
   ------------------------------------------------------------------------- */

function openDb() {
    return new Promise((resolve, reject) => {
        const request = indexedDB.open(DB_NAME, 1);
        request.onupgradeneeded = () => request.result.createObjectStore(STORE);
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
    });
}

async function dbGet(key) {
    const db = await openDb();
    return new Promise((resolve, reject) => {
        const request = db.transaction(STORE, 'readonly').objectStore(STORE).get(key);
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
    });
}

async function dbPut(key, value) {
    const db = await openDb();
    return new Promise((resolve, reject) => {
        const tx = db.transaction(STORE, 'readwrite');
        tx.objectStore(STORE).put(value, key);
        tx.oncomplete = () => resolve();
        tx.onerror = () => reject(tx.error);
    });
}

async function dbDelete(key) {
    const db = await openDb();
    return new Promise((resolve, reject) => {
        const tx = db.transaction(STORE, 'readwrite');
        tx.objectStore(STORE).delete(key);
        tx.oncomplete = () => resolve();
        tx.onerror = () => reject(tx.error);
    });
}

/* -------------------------------------------------------------------------
   Criptografia
   ------------------------------------------------------------------------- */

function bufferToBase64(buffer) {
    return btoa(String.fromCharCode(...new Uint8Array(buffer)));
}

/**
 * Gera o par de chaves.
 *
 * O segundo parâmetro (false) é o mais importante desta função: marca a
 * chave como NÃO EXPORTÁVEL. A chave pública continua legível - ela não é
 * segredo -, mas a privada fica trancada dentro do navegador para sempre.
 */
async function generateKeyPair() {
    const pair = await crypto.subtle.generateKey(
        { name: 'ECDSA', namedCurve: 'P-256' },
        false,
        ['sign', 'verify']
    );

    await dbPut(KEY_ID, pair);
    return pair;
}

async function loadKeyPair() {
    return dbGet(KEY_ID);
}

/** Exporta somente a chave PÚBLICA, no formato que o servidor espera. */
async function exportPublicKey(pair) {
    const spki = await crypto.subtle.exportKey('spki', pair.publicKey);
    return bufferToBase64(spki);
}

/**
 * Assina o texto do desafio.
 *
 * O resultado vem no formato "cru": os números r e s colados, 64 bytes.
 * O Android Keystore produz o mesmo conteúdo em outro embrulho (DER) —
 * o servidor aceita os dois.
 */
async function signMessage(pair, message) {
    const signature = await crypto.subtle.sign(
        { name: 'ECDSA', hash: 'SHA-256' },
        pair.privateKey,
        new TextEncoder().encode(message)
    );
    return bufferToBase64(signature);
}

/* -------------------------------------------------------------------------
   Tela
   ------------------------------------------------------------------------- */

async function refreshKeyStatus() {
    const pair = await loadKeyPair();
    const status = document.querySelector('#p-keystatus');

    if (!pair) {
        status.innerHTML = '<span class="tag mute">Nenhuma chave neste navegador</span>';
        return null;
    }

    const publicKey = await exportPublicKey(pair);
    status.innerHTML = `
        <span class="tag ok">Chave criada</span>
        <div class="mono" style="margin-top:8px">
            pública: ${publicKey.slice(0, 48)}…
        </div>
        <div class="card-sub" style="margin-top:4px">
            privada: guardada e não exportável
        </div>`;
    return pair;
}

function fillPhoneSelects() {
    document.querySelector('#p-cred').innerHTML = cache.credentials.map((c) =>
        `<option value="${esc(c.id)}" data-device="${esc(c.deviceId)}">
            ${esc(c.guestName)} — apto ${esc(c.unitLabel)}
         </option>`).join('');

    document.querySelector('#p-reader').innerHTML = cache.readers.map((r) =>
        `<option value="${esc(r.code)}">${esc(r.code)}</option>`).join('');

    const when = document.querySelector('#p-when');
    if (!when.value) {
        const first = cache.credentials[0];
        const middle = first
            ? (new Date(first.validFrom).getTime() + new Date(first.validUntil).getTime()) / 2
            : Date.now();
        when.value = toLocalInput(new Date(middle).toISOString());
    }
}

function showPhoneResult(result) {
    const granted = result.decision === 'GRANTED';
    document.querySelector('#p-result').innerHTML = `
        <div class="result ${granted ? 'granted' : 'denied'}">
            <div class="icon">${granted ? '🟢' : '🔴'}</div>
            <div class="verdict">${granted ? 'ACESSO LIBERADO' : 'ACESSO NEGADO'}</div>
            <div class="reason">${esc(result.message)}</div>
            ${result.reason ? `<div class="code">${esc(result.reason)}</div>` : ''}
        </div>`;
}

function showSteps(lines) {
    document.querySelector('#p-steps').innerHTML = lines.map(esc).join('<br>');
}

/* -------------------------------------------------------------------------
   O fluxo completo
   ------------------------------------------------------------------------- */

async function openDoor({ replay = false, tamper = false } = {}) {
    const pair = await loadKeyPair();
    if (!pair) {
        return toast('Gere o par de chaves primeiro.', true);
    }

    const credentialId = document.querySelector('#p-cred').value;
    const readerCode = document.querySelector('#p-reader').value;
    const at = toIso(document.querySelector('#p-when').value);

    if (!credentialId || !readerCode) {
        return toast('Escolha a credencial e o leitor.', true);
    }

    const steps = [];

    try {
        let challenge;

        if (replay) {
            if (!lastAttempt) {
                return toast('Abra a porta uma vez antes de tentar repetir.', true);
            }
            challenge = lastAttempt.challenge;
            steps.push('1. REUTILIZANDO o desafio anterior (ataque de repetição)');
        } else {
            // Passo 1: o leitor pede um desafio ao servidor
            challenge = await api('/access/challenge', {
                method: 'POST',
                body: JSON.stringify({ readerCode })
            });
            steps.push('1. Desafio recebido: ' + challenge.nonce.slice(0, 24) + '…');
        }

        // Passo 2: monta o texto exato a ser assinado
        const message = 'SMARTKEY-ACCESS-v1|' + challenge.challengeId + '|'
            + challenge.nonce + '|' + readerCode + '|' + credentialId;
        steps.push('2. Texto assinado: SMARTKEY-ACCESS-v1|…|' + readerCode + '|…');

        // Passo 3: o "celular" assina
        let signature = replay && lastAttempt
            ? lastAttempt.signature
            : await signMessage(pair, message);

        if (tamper) {
            // Troca o último caractere, simulando adulteração no caminho.
            const bytes = atob(signature).split('');
            bytes[bytes.length - 1] = String.fromCharCode(
                bytes[bytes.length - 1].charCodeAt(0) ^ 1);
            signature = btoa(bytes.join(''));
            steps.push('3. Assinatura ALTERADA de propósito');
        } else {
            steps.push('3. Assinado com a chave privada (64 bytes)');
        }

        if (!replay && !tamper) {
            lastAttempt = { challenge, signature };
        }

        // Passo 4: o servidor confere
        const result = await api('/access/verify', {
            method: 'POST',
            body: JSON.stringify({
                challengeId: challenge.challengeId,
                credentialId,
                signature,
                at
            })
        });

        steps.push('4. Servidor respondeu: ' + result.decision
            + (result.reason ? ' (' + result.reason + ')' : ''));

        showSteps(steps);
        showPhoneResult(result);

    } catch (error) {
        showSteps(steps);
        toast(error.message, true);
    }
}

/* -------------------------------------------------------------------------
   Ligações dos botões
   ------------------------------------------------------------------------- */

document.querySelector('#p-generate').addEventListener('click', async () => {
    await generateKeyPair();
    await refreshKeyStatus();
    toast('Par de chaves criado neste navegador.');
});

document.querySelector('#p-forget').addEventListener('click', async () => {
    if (!confirm('Apagar a chave deste navegador? Ela não pode ser recuperada.')) return;
    await dbDelete(KEY_ID);
    lastAttempt = null;
    await refreshKeyStatus();
    toast('Chave apagada.');
});

document.querySelector('#p-register').addEventListener('click', async () => {
    const pair = await loadKeyPair();
    if (!pair) return toast('Gere o par de chaves primeiro.', true);

    const option = document.querySelector('#p-cred').selectedOptions[0];
    if (!option) return toast('Escolha uma credencial.', true);

    try {
        await api(`/admin/devices/${option.dataset.device}/public-key`, {
            method: 'POST',
            body: JSON.stringify({
                publicKey: await exportPublicKey(pair),
                algorithm: 'ECDSA_P256'
            })
        });
        toast('Chave pública registrada no servidor.');
    } catch (error) {
        toast(error.message, true);
    }
});

document.querySelector('#p-open').addEventListener('click', () => openDoor());
document.querySelector('#p-replay').addEventListener('click', () => openDoor({ replay: true }));
document.querySelector('#p-tamper').addEventListener('click', () => openDoor({ tamper: true }));
