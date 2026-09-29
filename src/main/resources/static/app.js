/* ==========================================================================
   SmartKey - painel administrativo
   JavaScript puro, sem bibliotecas. Conversa com a API por fetch().
   ========================================================================== */

'use strict';

const API = '/api';
const STORE_KEY = 'smartkey.adminKey';

let adminKey = '';
let cache = { credentials: [], readers: [], accessPoints: [] };

/* -------------------------------------------------------------------------
   Utilidades
   ------------------------------------------------------------------------- */

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => Array.from(document.querySelectorAll(sel));

/** Escapa texto antes de jogar no HTML, para nada virar código executável. */
function esc(value) {
    return String(value ?? '').replace(/[&<>"']/g, (c) => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[c]);
}

function toast(message, isError) {
    const el = document.createElement('div');
    el.className = 'toast' + (isError ? ' err' : '');
    el.textContent = message;
    document.body.appendChild(el);
    setTimeout(() => el.remove(), 3600);
}

/**
 * O input datetime-local entrega "2026-09-18T15:00" sem fuso nenhum.
 * O navegador interpreta esse texto no fuso do próprio aparelho, e o
 * toISOString converte para UTC - que é como a API espera receber.
 */
function toIso(localValue) {
    return localValue ? new Date(localValue).toISOString() : null;
}

/** Caminho inverso: de UTC para o formato que o input datetime-local aceita. */
function toLocalInput(iso) {
    const d = new Date(iso);
    return new Date(d.getTime() - d.getTimezoneOffset() * 60000)
        .toISOString().slice(0, 16);
}

function formatDateTime(iso) {
    return new Date(iso).toLocaleString('pt-BR', {
        day: '2-digit', month: '2-digit', year: 'numeric',
        hour: '2-digit', minute: '2-digit'
    });
}

function formatShort(iso) {
    return new Date(iso).toLocaleString('pt-BR', {
        day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit'
    });
}

/* -------------------------------------------------------------------------
   Comunicação com a API
   ------------------------------------------------------------------------- */

async function api(path, options = {}) {
    const response = await fetch(API + path, {
        ...options,
        headers: {
            'x-admin-key': adminKey,
            ...(options.body ? { 'Content-Type': 'application/json' } : {}),
            ...(options.headers || {})
        }
    });

    if (response.status === 401) {
        logout();
        throw new Error('Chave administrativa inválida.');
    }

    const text = await response.text();
    const data = text ? JSON.parse(text) : null;

    if (!response.ok) {
        throw new Error(data?.message || `Erro ${response.status}`);
    }
    return data;
}

/* -------------------------------------------------------------------------
   Entrada e saída
   ------------------------------------------------------------------------- */

function logout() {
    localStorage.removeItem(STORE_KEY);
    adminKey = '';
    $('#app').hidden = true;
    $('#login').hidden = false;
}

async function login(key) {
    adminKey = key;
    await api('/admin/readers');           // valida a chave de verdade
    localStorage.setItem(STORE_KEY, key);
    $('#login').hidden = true;
    $('#app').hidden = false;
    await refreshAll();
}

/* -------------------------------------------------------------------------
   Abas
   ------------------------------------------------------------------------- */

function showTab(name) {
    $$('nav button').forEach((b) =>
        b.setAttribute('aria-selected', String(b.dataset.tab === name)));
    $$('main section').forEach((s) =>
        s.hidden = s.id !== 'tab-' + name);

    if (name === 'history') loadHistory();
    if (name === 'simulator') fillSimulator();
    if (name === 'new') fillAccessPointChips();
    if (name === 'phone') {
        fillPhoneSelects();
        refreshKeyStatus();
    }
}

/* -------------------------------------------------------------------------
   Carregamento dos dados
   ------------------------------------------------------------------------- */

async function refreshAll() {
    const [credentials, readers] = await Promise.all([
        api('/admin/credentials'),
        api('/admin/readers')
    ]);

    cache.credentials = credentials;
    cache.readers = readers;
    cache.accessPoints = [...new Set(readers.map((r) => r.accessPointCode))].sort();

    renderCredentials();
    renderReaders();
}

/* -------------------------------------------------------------------------
   Aba: CHAVES
   ------------------------------------------------------------------------- */

function credentialState(credential) {
    if (credential.status === 'REVOKED') return { cls: 'no', label: 'Revogada' };

    const now = Date.now();
    if (now < new Date(credential.validFrom).getTime()) {
        return { cls: 'warn', label: 'Aguardando check-in' };
    }
    if (now >= new Date(credential.validUntil).getTime()) {
        return { cls: 'mute', label: 'Encerrada' };
    }
    return { cls: 'ok', label: 'Ativa agora' };
}

function renderCredentials() {
    const container = $('#keys-list');

    if (!cache.credentials.length) {
        container.innerHTML =
            '<div class="empty">Nenhuma chave emitida ainda.<br>' +
            'Use a aba <strong>Nova reserva</strong>.</div>';
        return;
    }

    container.innerHTML = cache.credentials.map((c) => {
        const state = credentialState(c);

        const chips = c.grants.map((g) => `
            <span class="grant-chip ${g.status === 'REVOKED' ? 'revoked' : ''}">
                ${esc(g.accessPointCode)}
                ${g.status === 'ACTIVE' && c.status === 'ACTIVE'
                    ? `<button title="Remover esta porta"
                         data-revoke-grant="${esc(c.id)}"
                         data-point="${esc(g.accessPointCode)}">&times;</button>`
                    : ''}
            </span>`).join('');

        return `
        <div class="card">
            <div class="card-head">
                <div class="card-title">
                    ${esc(c.guestName)}
                    <div class="card-sub">Apartamento ${esc(c.unitLabel)}</div>
                </div>
                <span class="tag ${state.cls}">${state.label}</span>
            </div>

            <div class="card-sub">
                ${formatDateTime(c.validFrom)} &rarr; ${formatDateTime(c.validUntil)}
            </div>

            ${c.revokedReason
                ? `<div class="card-sub" style="margin-top:6px">
                     Motivo: ${esc(c.revokedReason)}</div>`
                : ''}

            <div class="grants">${chips}</div>

            <div class="row" style="margin-top:14px">
                ${c.enrollmentToken
                    ? `<button class="btn small" data-link="${esc(c.enrollmentToken)}"
                         data-guest="${esc(c.guestName)}">
                         📲 Link da chave</button>`
                    : '<span class="tag mute">chave já ativada no aparelho</span>'}
                <button class="btn ghost small" data-add-grant="${esc(c.id)}">
                    + Porta
                </button>
                ${c.status === 'ACTIVE'
                    ? `<button class="btn danger small" data-revoke="${esc(c.id)}">
                         Revogar chave</button>`
                    : ''}
            </div>

            <div class="mono" style="margin-top:10px">${esc(c.id)}</div>
        </div>`;
    }).join('');
}

/* -------------------------------------------------------------------------
   Aba: NOVA RESERVA
   ------------------------------------------------------------------------- */

function fillAccessPointChips() {
    const container = $('#n-points');

    // Apartamentos não entram na lista: o da reserva é incluído sozinho.
    const shared = cache.accessPoints.filter((p) => !p.startsWith('apartamento_'));

    if (!shared.length) {
        container.innerHTML =
            '<span class="hint">Cadastre leitores primeiro, na aba Leitores.</span>';
        return;
    }

    container.innerHTML = shared.map((p) => `
        <label class="grant-chip" style="cursor:pointer;font-weight:400">
            <input type="checkbox" value="${esc(p)}" style="width:auto;margin:0"
                   ${p === 'entrada_condominio' ? 'checked' : ''}>
            ${esc(p)}
        </label>`).join('');
}

async function createReservation() {
    const name = $('#n-name').value.trim();
    const unit = $('#n-unit').value.trim();
    const checkIn = toIso($('#n-in').value);
    const checkOut = toIso($('#n-out').value);

    if (!name || !unit || !checkIn || !checkOut) {
        return toast('Preencha nome, apartamento e as duas datas.', true);
    }
    if (new Date(checkOut) <= new Date(checkIn)) {
        return toast('O checkout precisa ser depois do check-in.', true);
    }

    const points = $$('#n-points input:checked').map((i) => i.value);

    // "12-A" vira "apartamento_12_a": códigos de porta só aceitam letras,
    // números e underline.
    points.push('apartamento_' + unit.toLowerCase().replace(/[^a-z0-9_]/g, '_'));

    const button = $('#n-create');
    button.disabled = true;
    button.textContent = 'Criando…';

    try {
        // Os quatro passos que formam uma chave digital.
        const guest = await api('/admin/guests', {
            method: 'POST',
            body: JSON.stringify({ name })
        });

        const reservation = await api('/admin/reservations', {
            method: 'POST',
            body: JSON.stringify({
                guestId: guest.id,
                unitLabel: unit,
                checkInAt: checkIn,
                checkOutAt: checkOut
            })
        });

        const device = await api('/admin/devices', {
            method: 'POST',
            body: JSON.stringify({
                guestId: guest.id,
                platform: 'ANDROID',
                label: 'Celular de ' + name
            })
        });

        await api('/admin/credentials', {
            method: 'POST',
            body: JSON.stringify({
                reservationId: reservation.id,
                deviceId: device.id,
                accessPointCodes: [...new Set(points)]
            })
        });

        toast('Chave emitida para ' + name);
        $('#n-name').value = '';
        $('#n-unit').value = '';

        await refreshAll();
        showTab('keys');

    } catch (error) {
        toast(error.message, true);
    } finally {
        button.disabled = false;
        button.textContent = 'Criar reserva e emitir chave';
    }
}

/* -------------------------------------------------------------------------
   Aba: SIMULADOR
   ------------------------------------------------------------------------- */

function fillSimulator() {
    $('#s-cred').innerHTML = cache.credentials.map((c) =>
        `<option value="${esc(c.id)}">
            ${esc(c.guestName)} — apto ${esc(c.unitLabel)}
         </option>`).join('');

    $('#s-reader').innerHTML = cache.readers.map((r) =>
        `<option value="${esc(r.code)}">
            ${esc(r.code)} (${esc(r.accessPointCode)})
         </option>`).join('');

    if (!$('#s-when').value) {
        // Por padrão, um momento no meio da estadia da primeira chave.
        const first = cache.credentials[0];
        if (first) {
            const middle = (new Date(first.validFrom).getTime()
                + new Date(first.validUntil).getTime()) / 2;
            $('#s-when').value = toLocalInput(new Date(middle).toISOString());
        } else {
            $('#s-when').value = toLocalInput(new Date().toISOString());
        }
    }
}

async function runSimulation() {
    const credentialId = $('#s-cred').value;
    const readerCode = $('#s-reader').value;
    const at = toIso($('#s-when').value);

    if (!credentialId || !readerCode) {
        return toast('Escolha a chave e o leitor.', true);
    }

    try {
        const result = await api('/access/check', {
            method: 'POST',
            body: JSON.stringify({ readerCode, credentialId, at })
        });

        const granted = result.decision === 'GRANTED';

        $('#s-result').innerHTML = `
            <div class="result ${granted ? 'granted' : 'denied'}">
                <div class="icon">${granted ? '🟢' : '🔴'}</div>
                <div class="verdict">
                    ${granted ? 'ACESSO LIBERADO' : 'ACESSO NEGADO'}
                </div>
                <div class="reason">${esc(result.message)}</div>
                ${result.reason ? `<div class="code">${esc(result.reason)}</div>` : ''}
            </div>`;

    } catch (error) {
        toast(error.message, true);
    }
}

/* -------------------------------------------------------------------------
   Aba: LEITORES
   ------------------------------------------------------------------------- */

function renderReaders() {
    const container = $('#readers-list');

    if (!cache.readers.length) {
        container.innerHTML = '<div class="empty">Nenhum leitor cadastrado.</div>';
        return;
    }

    container.innerHTML = cache.readers.map((r) => `
        <div class="card">
            <div class="card-head">
                <div class="card-title">
                    ${esc(r.name)}
                    <div class="card-sub mono">${esc(r.code)}</div>
                </div>
                <span class="tag ${r.status === 'ACTIVE' ? 'ok' : 'mute'}">
                    ${r.status === 'ACTIVE' ? 'Ativo' : 'Inativo'}
                </span>
            </div>
            <span class="tag mute">abre: ${esc(r.accessPointCode)}</span>

            <div class="row" style="margin-top:12px">
                <button class="btn ghost small" data-reader-key="${esc(r.id)}"
                        data-reader-code="${esc(r.code)}">
                    🔑 Gerar chave deste leitor
                </button>
            </div>
        </div>`).join('');
}

/**
 * Gera uma chave nova para um leitor e a mostra UMA vez.
 *
 * O servidor guarda apenas o hash. Se esta janela for fechada sem anotar a
 * chave, não há como recuperá-la - só gerar outra.
 */
async function rotateReaderKey(readerId, readerCode) {
    if (!confirm(
            `Gerar uma chave nova para "${readerCode}"?

`
            + 'A chave atual deixa de funcionar imediatamente, e o aparelho '
            + 'precisará ser reconfigurado com a nova.')) {
        return;
    }

    try {
        const result = await api(`/admin/readers/${readerId}/api-key`, { method: 'POST' });
        showReaderKey(result);
    } catch (error) {
        toast(error.message, true);
    }
}

function showReaderKey(reader) {
    const box = document.createElement('div');
    box.className = 'modal-backdrop';
    box.innerHTML = `
        <div class="card" style="max-width:400px;margin:auto">
            <div class="card-title">Chave de ${esc(reader.code)}</div>

            <div class="warning-banner" style="margin-top:12px">
                <strong>Anote agora.</strong>
                O servidor guarda apenas o resumo criptográfico desta chave.
                Ao fechar esta janela, ela não poderá mais ser recuperada —
                só substituída por outra.
            </div>

            <div class="mono" style="background:var(--surface-2);padding:12px;
                 border-radius:8px;border:1px solid var(--border);
                 font-size:13px;user-select:all">${esc(reader.apiKey)}</div>

            <p class="hint" style="margin-top:12px;font-size:12px">
                No aparelho do leitor, abra <strong>/reader.html</strong>,
                informe o código <strong>${esc(reader.code)}</strong> e cole
                esta chave.
            </p>

            <div class="row" style="margin-top:8px">
                <button class="btn small" data-copy-key>Copiar chave</button>
                <button class="btn ghost small" data-close>Fechar</button>
            </div>
        </div>`;

    box.addEventListener('click', async (event) => {
        if (event.target.hasAttribute('data-close') || event.target === box) {
            box.remove();
        }
        if (event.target.hasAttribute('data-copy-key')) {
            try {
                await navigator.clipboard.writeText(reader.apiKey);
                toast('Chave copiada.');
            } catch {
                toast('Selecione e copie manualmente.', true);
            }
        }
    });

    document.body.appendChild(box);
}

async function createReader() {
    const code = $('#r-code').value.trim().toLowerCase();
    const name = $('#r-name').value.trim();
    const point = $('#r-point').value.trim().toLowerCase();

    if (!code || !name || !point) {
        return toast('Preencha os três campos.', true);
    }

    try {
        const created = await api('/admin/readers', {
            method: 'POST',
            body: JSON.stringify({ code, name, accessPointCode: point })
        });

        $('#r-code').value = '';
        $('#r-name').value = '';
        $('#r-point').value = '';
        await refreshAll();

        // A chave só aparece nesta resposta; mostramos antes de qualquer
        // outra coisa para o usuário não perdê-la.
        showReaderKey(created);

    } catch (error) {
        toast(error.message, true);
    }
}

/* -------------------------------------------------------------------------
   Aba: HISTÓRICO
   ------------------------------------------------------------------------- */

async function loadHistory() {
    const container = $('#history-list');
    container.innerHTML = '<div class="empty">Carregando…</div>';

    try {
        const events = await api('/access/events?size=60');

        if (!events.length) {
            container.innerHTML =
                '<div class="empty">Nenhuma tentativa registrada ainda.</div>';
            return;
        }

        container.innerHTML = events.map((e) => {
            const granted = e.decision === 'GRANTED';
            return `
            <div class="event">
                <span class="when">${formatShort(e.occurredAt)}</span>
                <span class="what">
                    <strong>${esc(e.readerCode)}</strong>
                    <div class="card-sub">${esc(e.message)}</div>
                </span>
                <span class="tag ${granted ? 'ok' : 'no'}">
                    ${granted ? 'Liberado' : 'Negado'}
                </span>
            </div>`;
        }).join('');

    } catch (error) {
        container.innerHTML = `<div class="empty">${esc(error.message)}</div>`;
    }
}

/* -------------------------------------------------------------------------
   Ações nos cartões de chave
   ------------------------------------------------------------------------- */

async function handleCardClick(event) {
    const target = event.target.closest(
        '[data-revoke], [data-revoke-grant], [data-add-grant], [data-link]');
    if (!target) return;

    // Link de ativacao: e entregue ao hospede (por mensagem, ou lido na tela).
    // O token vai depois do "#" de proposito: o que vem apos o # nunca e
    // enviado ao servidor, entao nao fica nos registros de acesso do servidor.
    if (target.dataset.link) {
        const url = location.origin + '/key.html#' + target.dataset.link;
        showEnrollmentLink(url, target.dataset.guest);
        return;
    }

    try {
        // Revogar a chave inteira
        if (target.dataset.revoke) {
            const reason = prompt('Motivo da revogação (opcional):', '');
            if (reason === null) return;

            await api(`/admin/credentials/${target.dataset.revoke}/revoke`, {
                method: 'POST',
                body: JSON.stringify({ reason: reason || 'Revogada pelo painel' })
            });
            toast('Chave revogada.');
        }

        // Retirar uma porta
        if (target.dataset.revokeGrant) {
            const point = target.dataset.point;
            if (!confirm(`Remover a permissão de "${point}"?`)) return;

            await api(
                `/admin/credentials/${target.dataset.revokeGrant}/grants/${encodeURIComponent(point)}`,
                { method: 'DELETE' });
            toast('Permissão removida.');
        }

        // Acrescentar uma porta
        if (target.dataset.addGrant) {
            const options = cache.accessPoints.join(', ');
            const point = prompt(`Qual porta liberar?\n\nDisponíveis: ${options}`, '');
            if (!point) return;

            await api(`/admin/credentials/${target.dataset.addGrant}/grants`, {
                method: 'POST',
                body: JSON.stringify({ accessPointCode: point.trim().toLowerCase() })
            });
            toast('Permissão adicionada.');
        }

        await refreshAll();

    } catch (error) {
        toast(error.message, true);
    }
}

/* -------------------------------------------------------------------------
   Link de ativação da chave
   ------------------------------------------------------------------------- */

function showEnrollmentLink(url, guestName) {
    const box = document.createElement('div');
    box.className = 'modal-backdrop';
    box.innerHTML = `
        <div class="card" style="max-width:380px;margin:auto">
            <div class="card-title">Chave de ${esc(guestName)}</div>
            <p class="hint">
                Entregue este link ao hóspede. Ele abre no celular, ativa a
                chave e o código passa a valer só naquele aparelho.
            </p>

            <div style="background:#fff;padding:12px;border-radius:10px;margin:12px 0;
                        aspect-ratio:1" data-qr aria-label="QR do link de ativação"></div>

            <div class="mono" style="word-break:break-all">${esc(url)}</div>

            <div class="row" style="margin-top:14px">
                <button class="btn small" data-copy>Copiar link</button>
                <button class="btn ghost small" data-close>Fechar</button>
            </div>
            <p class="hint" style="font-size:12px;margin-top:10px">
                Vale uma vez só. Depois de ativado, este link não serve para
                mais nenhum aparelho.
            </p>
        </div>`;

    box.addEventListener('click', async (event) => {
        if (event.target.hasAttribute('data-close') || event.target === box) {
            box.remove();
            await refreshAll();
        }
        if (event.target.hasAttribute('data-copy')) {
            try {
                await navigator.clipboard.writeText(url);
                toast('Link copiado.');
            } catch {
                toast('Copie o link manualmente.', true);
            }
        }
    });

    document.body.appendChild(box);

    // O link vai no CORPO do pedido, nunca na URL: com ele, qualquer pessoa
    // registraria a própria chave no lugar do hóspede - e URLs ficam gravadas
    // nos logs de servidores e proxies.
    fetch('/api/keys/qr', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ data: url, size: 320 })
    })
        .then((r) => (r.ok ? r.text() : Promise.reject()))
        .then((svg) => { box.querySelector('[data-qr]').innerHTML = svg; })
        .catch(() => { box.querySelector('[data-qr]').textContent = 'QR indisponível'; });
}

/* -------------------------------------------------------------------------
   Inicialização
   ------------------------------------------------------------------------- */

$('#enter').addEventListener('click', async () => {
    const key = $('#key').value.trim();
    if (!key) return toast('Informe a chave.', true);
    try {
        await login(key);
    } catch (error) {
        toast('Chave inválida.', true);
    }
});

$('#key').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') $('#enter').click();
});

$('#logout').addEventListener('click', logout);

$$('nav button').forEach((b) =>
    b.addEventListener('click', () => showTab(b.dataset.tab)));

$('#n-create').addEventListener('click', createReservation);
$('#r-create').addEventListener('click', createReader);

$('#readers-list').addEventListener('click', (event) => {
    const target = event.target.closest('[data-reader-key]');
    if (target) {
        rotateReaderKey(target.dataset.readerKey, target.dataset.readerCode);
    }
});
$('#s-run').addEventListener('click', runSimulation);
$('#keys-list').addEventListener('click', handleCardClick);

// Datas sugeridas: entrada hoje às 15:00, saída daqui a 3 dias às 11:00.
(function suggestDates() {
    const checkIn = new Date();
    checkIn.setHours(15, 0, 0, 0);
    const checkOut = new Date(checkIn);
    checkOut.setDate(checkOut.getDate() + 3);
    checkOut.setHours(11, 0, 0, 0);

    $('#n-in').value = toLocalInput(checkIn.toISOString());
    $('#n-out').value = toLocalInput(checkOut.toISOString());
})();

// A dica da chave de teste só aparece numa instalação de demonstração.
// Num servidor de verdade ela seria, no mínimo, confusa.
fetch('/api/health')
    .then((r) => r.json())
    .then((h) => { if (h.demoMode) $('#demo-hint').hidden = false; })
    .catch(() => { /* sem a dica, sem problema */ });

// Se a chave já estiver guardada neste aparelho, entra direto.
(async function autoLogin() {
    const saved = localStorage.getItem(STORE_KEY);
    if (!saved) return;
    try {
        await login(saved);
    } catch {
        logout();
    }
})();
