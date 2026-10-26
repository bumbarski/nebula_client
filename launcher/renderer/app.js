'use strict';
// Rdzeń interfejsu: stan, router, pomocniki i ekran „Graj”.
// Pozostałe ekrany rejestrują się w N.pages (instances.js, accounts.js, settings.js).

(function () {
  const N = (window.N = {
    s: null, // stan z procesu głównego (state:get)
    page: 'play',
    pages: {},
    selectedId: '',
    game: { state: 'idle', instanceId: '', stage: '', done: 0, total: 0, error: '', warnings: [], log: [] },
    play: null, // referencje do elementów ekranu Graj (aktualizowane bez przerysowania)
  });

  // ---------- pomocniki ----------

  /** Tworzy element DOM. Teksty trafiają jako węzły tekstowe, więc nic z gry ani z kont nie wykona się jako HTML. */
  N.h = function h(tag, props, ...kids) {
    const el = document.createElement(tag);
    let value;
    for (const [k, v] of Object.entries(props || {})) {
      if (v == null || v === false) continue;
      if (k === 'class') el.className = v;
      else if (k === 'value') value = v;
      else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
      else if (['checked', 'disabled', 'selected'].includes(k)) el[k] = Boolean(v);
      else el.setAttribute(k, v === true ? '' : v);
    }
    for (const kid of kids.flat(Infinity)) {
      if (kid == null || kid === false) continue;
      el.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
    }
    if (value !== undefined) el.value = value; // dla <select> dopiero po dodaniu opcji
    return el;
  };
  const h = N.h;

  N.call = async function call(channel, ...args) {
    const r = await window.api.call(channel, ...args);
    if (!r || !r.ok) throw new Error((r && r.error) || 'Nieznany błąd');
    return r.data;
  };

  N.toast = function toast(text, kind = '') {
    const box = document.getElementById('toast');
    const t = h('div', { class: 'toast ' + kind }, text);
    box.append(t);
    setTimeout(() => t.remove(), kind === 'error' ? 7000 : 3500);
  };

  N.gb = (mb) => `${Math.round((mb / 1024) * 10) / 10} GB`;

  N.current = function current() {
    const list = N.s.instances;
    return list.find((i) => i.id === N.selectedId) || list[0];
  };

  N.selectInstance = function selectInstance(id) {
    N.selectedId = id;
    try { localStorage.setItem('nebula.selected', id); } catch { /* tryb prywatny: pomijamy */ }
  };

  N.applyTheme = function applyTheme() {
    document.documentElement.dataset.theme = N.s.settings.theme || 'dark';
  };

  /** Pobiera stan z procesu głównego i odświeża bieżący ekran. */
  N.refresh = async function refresh() {
    N.s = await N.call('state:get');
    if (!N.s.instances.some((i) => i.id === N.selectedId)) N.selectedId = N.s.instances[0].id;
    N.applyTheme();
    N.renderFoot();
    N.go(N.page);
  };

  N.go = function go(page) {
    N.page = page;
    N.play = null;
    for (const b of document.querySelectorAll('.nav')) b.classList.toggle('active', b.dataset.page === page);
    const view = document.getElementById('view');
    view.replaceChildren();
    (N.pages[page] || N.pages.play)(view);
  };

  N.renderFoot = function renderFoot() {
    const foot = document.getElementById('sidebar-foot');
    const acc = N.s.accounts;
    const sel = acc.accounts.find((a) => a.uuid === acc.selected);
    foot.replaceChildren(
      h('div', {}, sel ? sel.name : 'Brak konta'),
      h('div', {}, 'Nebula Launcher ' + N.s.version),
    );
  };

  // ---------- ekran „Graj” ----------

  const busyStates = ['preparing', 'running'];

  function statusText() {
    const g = N.game;
    if (g.state === 'preparing') {
      const pct = g.total ? ` ${g.done}/${g.total}` : '';
      return (g.stage || 'Przygotowuję') + pct;
    }
    if (g.state === 'running') return 'Gra działa';
    if (g.state === 'error') return g.error || 'Błąd';
    if (g.state === 'stopped') return g.code ? `Gra zakończona (kod ${g.code})` : 'Gra zakończona';
    return 'Gotowy do gry';
  }

  /** Aktualizuje elementy ekranu Graj bez jego przerysowywania. */
  function updatePlay() {
    const p = N.play;
    if (!p) return;
    const g = N.game;
    const hasAccount = N.s.accounts.accounts.length > 0;
    const running = g.state === 'running';
    const preparing = g.state === 'preparing';

    p.btn.textContent = running ? 'ZATRZYMAJ' : preparing ? 'PRZYGOTOWANIE…' : 'GRAJ';
    p.btn.classList.toggle('stop', running);
    p.btn.disabled = preparing || (!running && !hasAccount);
    p.status.textContent = hasAccount || busyStates.includes(g.state) ? statusText() : 'Najpierw dodaj konto w zakładce Konta';
    p.status.style.color = g.state === 'error' ? 'var(--danger)' : '';
    p.bar.style.width = preparing && g.total ? Math.min(100, (g.done / g.total) * 100) + '%' : running ? '100%' : '0';
    p.select.disabled = busyStates.includes(g.state);
    p.logCard.hidden = g.log.length === 0;
    p.log.textContent = g.log.join('\n');
    p.log.scrollTop = p.log.scrollHeight;
  }

  N.pages.play = function play(view) {
    const inst = N.current();
    const modCount = (inst.fabric ? 1 : 0) + (inst.fabric && inst.nebula ? 1 : 0) + (inst.fabric ? inst.mods.length : 0);

    const select = h('select', {
      value: inst.id,
      onchange: (e) => { N.selectInstance(e.target.value); N.go('play'); },
    }, N.s.instances.map((i) => h('option', { value: i.id }, i.name)));

    const btn = h('button', { class: 'play', onclick: onPlayClick }, 'GRAJ');
    const status = h('div', { class: 'status' });
    const bar = h('i');
    const log = h('pre', { id: 'log' });
    const logCard = h('div', { class: 'card', hidden: true }, h('h2', {}, 'Dziennik gry'), log);

    const warnings = N.game.warnings || [];

    view.append(
      h('h1', {}, 'Graj'),
      h('div', { class: 'card hero' },
        h('div', { class: 'hero-left' },
          h('label', { class: 'field' }, h('span', {}, 'Instalacja'), select),
          h('div', {},
            h('span', { class: 'pill' }, 'Minecraft ' + inst.mcVersion),
            h('span', { class: 'pill' }, inst.fabric ? 'Fabric' : 'Vanilla'),
            h('span', { class: 'pill' }, N.gb(inst.ramMb) + ' RAM'),
            h('span', { class: 'pill' }, modCount + ' modów'),
            inst.fabric && inst.nebula ? h('span', { class: 'pill' }, 'Nebula Client') : null,
          ),
          h('p', { class: 'muted small' }, 'Zmienisz wersję, pamięć RAM i mody w zakładce Instalacje.'),
        ),
        h('div', { class: 'hero-right' }, btn, h('div', { class: 'bar' }, bar), status),
      ),
      warnings.length
        ? h('div', { class: 'notice warn' }, h('b', {}, 'Uwaga: '), warnings.join('; '))
        : null,
      logCard,
    );

    N.play = { btn, status, bar, log, logCard, select };
    updatePlay();
  };

  async function onPlayClick() {
    const g = N.game;
    try {
      if (g.state === 'running') {
        await N.call('game:stop');
        return;
      }
      N.game = { ...g, state: 'preparing', instanceId: N.current().id, stage: 'Start', done: 0, total: 0, error: '', warnings: [], log: [] };
      updatePlay();
      await N.call('game:play', N.current().id);
    } catch (e) {
      N.game.state = 'error';
      N.game.error = e.message;
      updatePlay();
      N.toast(e.message, 'error');
    }
  }

  // ---------- zdarzenia z procesu głównego ----------

  function subscribe() {
    window.api.on('progress', (p) => {
      N.game.stage = p.stage || N.game.stage;
      N.game.done = p.done || 0;
      N.game.total = p.total || 0;
      updatePlay();
    });
    window.api.on('log', (line) => {
      N.game.log.push(String(line));
      if (N.game.log.length > 400) N.game.log.splice(0, N.game.log.length - 400);
      updatePlay();
    });
    window.api.on('game:status', (s) => {
      N.game.state = s.state;
      N.game.instanceId = s.instanceId || N.game.instanceId;
      if (s.state === 'error') N.game.error = s.error || 'Błąd';
      if (s.state === 'stopped') N.game.code = s.code;
      if (s.warnings) N.game.warnings = s.warnings;
      if (N.s) N.s.running = s.state === 'running' ? s.instanceId : null;
      if (N.page === 'play' && (s.state === 'running' || s.state === 'stopped')) N.go('play');
      else updatePlay();
    });
    window.api.on('auth:done', async (r) => {
      if (N.pages.onAuthDone) N.pages.onAuthDone(r);
    });
  }

  async function init() {
    try { N.selectedId = localStorage.getItem('nebula.selected') || ''; } catch { N.selectedId = ''; }
    for (const b of document.querySelectorAll('.nav')) b.addEventListener('click', () => N.go(b.dataset.page));
    subscribe();
    try {
      await N.refresh();
      if (N.s.running) N.game.state = 'running';
      N.go('play');
    } catch (e) {
      document.getElementById('view').replaceChildren(h('div', { class: 'notice error' }, 'Nie udało się wczytać launchera: ' + e.message));
    }
  }

  document.addEventListener('DOMContentLoaded', init);
})();
