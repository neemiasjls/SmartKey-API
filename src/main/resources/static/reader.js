/* ==========================================================================
   LEITOR — o aplicativo que faz o papel da fechadura

   Abre a câmera, procura o QR do hóspede e pergunta ao servidor se libera.

   A leitura usa a BarcodeDetector, que já vem no Chrome do Android. No Safari
   do iPhone ela ainda não existe; nesse caso a página avisa e oferece o campo
   manual, para dar para testar assim mesmo.
   ========================================================================== */

'use strict';

const CONFIG_KEY = 'smartkey.reader.config';

/** Tempo que o resultado fica na tela antes de voltar a ler. */
const VERDICT_SECONDS = 3;

/** Ignora o mesmo código lido repetidamente enquanto a câmera não sai dele. */
const SAME_CODE_COOLDOWN_MS = 4000;

let config = null;
let detector = null;
let stream = null;
let scanning = false;
let lastCode = null;
let lastCodeAt = 0;

const $ = (sel) => document.querySelector(sel);

/* -------------------------------------------------------------------------
   Configuração
   ------------------------------------------------------------------------- */

function loadConfig() {
    try {
        return JSON.parse(localStorage.getItem(CONFIG_KEY) || 'null');
    } catch {
        return null;
    }
}

function restoreConfigFields() {
    const saved = loadConfig();
    if (!saved) return;
    $('#reader-code').value = saved.readerCode || '';
    $('#reader-key').value = saved.readerKey || '';
}

$('#save-config').addEventListener('click', async () => {
    const readerCode = $('#reader-code').value.trim().toLowerCase();
    const readerKey = $('#reader-key').value.trim();

    if (!readerCode || !readerKey) {
        return alert('Informe o código e a chave deste leitor.');
    }

    config = { readerCode, readerKey };
    localStorage.setItem(CONFIG_KEY, JSON.stringify(config));

    $('#reader-tag').textContent = readerCode;
    $('#config').hidden = true;
    $('#scanner').hidden = false;

    await startCamera();
});

$('#reconfigure').addEventListener('click', () => {
    stopCamera();
    $('#scanner').hidden = true;
    $('#config').hidden = false;
});

/* -------------------------------------------------------------------------
   Câmera
   ------------------------------------------------------------------------- */

async function startCamera() {
    if (!('BarcodeDetector' in window)) {
        $('#scan-status').innerHTML =
            'Este navegador não lê QR pela câmera.<br>'
            + 'Use o Chrome no Android, ou o campo manual abaixo.';
        return;
    }

    try {
        detector = new BarcodeDetector({ formats: ['qr_code'] });

        stream = await navigator.mediaDevices.getUserMedia({
            video: {
                // "environment" = câmera de trás, que é a que aponta para o hóspede
                facingMode: 'environment',
                width: { ideal: 1280 },
                height: { ideal: 720 }
            }
        });

        const video = $('#video');
        video.srcObject = stream;
        await video.play();

        scanning = true;
        scanLoop();

    } catch (error) {
        $('#scan-status').textContent =
            'Não foi possível abrir a câmera: ' + error.message;
    }
}

function stopCamera() {
    scanning = false;
    if (stream) {
        stream.getTracks().forEach((track) => track.stop());
        stream = null;
    }
}

async function scanLoop() {
    if (!scanning) return;

    try {
        const codes = await detector.detect($('#video'));

        if (codes.length > 0) {
            const value = codes[0].rawValue;
            const now = Date.now();

            // Sem esta trava, o mesmo código seria enviado dezenas de vezes
            // por segundo enquanto estivesse na frente da câmera - e, como
            // cada código só vale uma vez, a segunda leitura já seria negada
            // por repetição, confundindo quem está na porta.
            const isRepeat = value === lastCode
                && (now - lastCodeAt) < SAME_CODE_COOLDOWN_MS;

            if (!isRepeat) {
                lastCode = value;
                lastCodeAt = now;
                await verify(value);
            }
        }
    } catch {
        // Quadro ruim, câmera ocupada, etc. Segue tentando.
    }

    requestAnimationFrame(scanLoop);
}

/* -------------------------------------------------------------------------
   Verificação
   ------------------------------------------------------------------------- */

async function verify(qrContent) {
    $('#scan-status').textContent = 'Verificando…';

    try {
        const response = await fetch('/api/access/qr-verify', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                // A chave DESTE leitor, e não a de administração.
                // Ela só serve para perguntar sobre esta porta.
                'x-reader-key': config.readerKey
            },
            body: JSON.stringify({ readerCode: config.readerCode, qrContent })
        });

        const result = await response.json();

        if (!response.ok) {
            throw new Error(result.message || 'Erro na verificação.');
        }

        showVerdict(result);

    } catch (error) {
        showVerdict({
            decision: 'DENIED',
            message: error.message,
            reason: 'ERRO_DE_COMUNICACAO'
        });
    }
}

function showVerdict(result) {
    const granted = result.decision === 'GRANTED';
    const overlay = $('#verdict');

    overlay.className = 'verdict-overlay ' + (granted ? 'granted' : 'denied');
    overlay.innerHTML = `
        <div class="big">${granted ? '🟢' : '🔴'}</div>
        <div class="title">${granted ? 'ACESSO LIBERADO' : 'ACESSO NEGADO'}</div>
        <div class="why">${result.message || ''}</div>
        ${result.reason ? `<div class="code">${result.reason}</div>` : ''}`;
    overlay.hidden = false;

    // Vibra, para o caso de o leitor estar num lugar barulhento.
    if (navigator.vibrate) {
        navigator.vibrate(granted ? 120 : [80, 60, 80]);
    }

    setTimeout(() => {
        overlay.hidden = true;
        $('#scan-status').textContent = 'Aponte para o código do hóspede';
    }, VERDICT_SECONDS * 1000);
}

$('#manual-go').addEventListener('click', () => {
    const value = $('#manual').value.trim();
    if (value) verify(value);
});

/* -------------------------------------------------------------------------
   Início
   ------------------------------------------------------------------------- */

(async function start() {
    if ('serviceWorker' in navigator) {
        navigator.serviceWorker.register('/sw.js').catch(() => { /* opcional */ });
    }

    restoreConfigFields();

    config = loadConfig();

    if (config?.readerCode && config?.readerKey) {
        $('#reader-tag').textContent = config.readerCode;
        $('#config').hidden = true;
        $('#scanner').hidden = false;
        await startCamera();
    }
})();
