'use strict';
// Ekran „Konta”: logowanie Microsoft kodem (device code), lista kont, wybór i usuwanie.

(function () {
  const { h, call, toast } = N;

  const DOCS_URL = 'https://github.com/bumbarski/nebula_client/tree/main/launcher#logowanie-microsoft';

  let flow = null; // { userCode, verificationUri } podczas logowania

  /** Wywoływane przez app.js po zdarzeniu auth:done z procesu głównego. */
  N.pages.onAuthDone = async (r) => {
    flow = null;
    if (r.ok) {
      toast('Konto dodane', 'ok');
      await N.refresh();
    } else {
      toast(r.error || 'Logowanie nie powiodło się', 'error');
      if (N.page === 'accounts') N.go('accounts');
    }
  };

  function avatar(acc) {
    const img = h('img', {
      src: `https://mc-heads.net/avatar/${acc.uuid}/40`, alt: '',
      onerror: (e) => e.target.remove(),
    });
    return h('div', { class: 'avatar' }, h('b', {}, (acc.name || '?').slice(0, 1).toUpperCase()), img);
  }

  function accountRow(acc, selected) {
    return h('div', { class: 'account' + (acc.uuid === selected ? ' sel' : '') },
      avatar(acc),
      h('div', { class: 'grow' },
        h('div', {}, h('b', {}, acc.name)),
        h('div', { class: 'muted small' }, acc.uuid === selected ? 'Aktywne konto' : 'Konto Microsoft'),
      ),
      h('button', {
        class: 'btn', disabled: acc.uuid === selected,
        onclick: async () => { await run(() => call('account:select', acc.uuid)); },
      }, acc.uuid === selected ? 'Wybrane' : 'Użyj'),
      h('button', {
        class: 'btn danger',
        onclick: async () => {
          if (confirm(`Usunąć konto ${acc.name} z launchera?`)) await run(() => call('account:remove', acc.uuid));
        },
      }, 'Usuń'),
    );
  }

  async function run(fn) {
    try {
      await fn();
      await N.refresh();
    } catch (e) {
      toast(e.message, 'error');
    }
  }

  async function startLogin() {
    try {
      flow = await call('auth:login');
      N.go('accounts');
    } catch (e) {
      flow = null;
      toast(e.message, 'error');
    }
  }

  N.pages.accounts = function accounts(view) {
    const s = N.s;
    const hasClientId = Boolean((s.settings.clientId || '').trim());
    const list = s.accounts;

    view.append(h('h1', {}, 'Konta'));

    // Bez Client ID logowanie Microsoft nie zadziała: wyjaśniamy to wprost zamiast pokazywać zagadkowy błąd.
    if (!hasClientId) {
      const input = h('input', { type: 'text', placeholder: 'np. 01234567-89ab-cdef-0123-456789abcdef' });
      view.append(h('div', { class: 'card' },
        h('h2', {}, 'Logowanie Microsoft wymaga Client ID'),
        h('p', {}, 'Mojang pozwala logować się tylko aplikacjom, które zatwierdził. Zarejestruj aplikację w Azure, złóż wniosek do Mojang i wklej tu jej Client ID (nie jest tajny). Do czasu zatwierdzenia możesz używać konta przez mod In-Game Account Switcher.'),
        h('label', { class: 'field' }, h('span', {}, 'Client ID'), input),
        h('div', { class: 'row' },
          h('button', {
            class: 'btn primary',
            onclick: async () => {
              const id = input.value.trim();
              if (!/^[0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(id)) { toast('To nie wygląda na Client ID (powinien mieć format GUID)', 'error'); return; }
              await run(() => call('settings:save', { clientId: id }));
            },
          }, 'Zapisz Client ID'),
          h('button', { class: 'btn', onclick: () => call('open:external', DOCS_URL).catch((e) => toast(e.message, 'error')) }, 'Jak to załatwić?'),
        ),
      ));
    }

    if (flow) {
      view.append(h('div', { class: 'card' },
        h('h2', {}, 'Zaloguj się w przeglądarce'),
        h('p', {}, 'Otworzyła się strona logowania Microsoft. Wpisz na niej ten kod:'),
        h('div', { class: 'code' }, flow.userCode),
        h('p', { class: 'muted small' }, 'Czekam na potwierdzenie…'),
        h('div', { class: 'row' },
          h('button', { class: 'btn', onclick: () => call('open:external', flow.verificationUri).catch((e) => toast(e.message, 'error')) }, 'Otwórz stronę ponownie'),
          h('button', { class: 'btn', onclick: async () => { await call('auth:cancel'); flow = null; N.go('accounts'); } }, 'Anuluj'),
        ),
      ));
    }

    view.append(h('div', { class: 'card' },
      h('div', { class: 'row between' },
        h('h2', {}, 'Twoje konta'),
        h('button', { class: 'btn primary', disabled: !hasClientId || Boolean(flow), onclick: startLogin }, '+ Dodaj konto Microsoft'),
      ),
      list.accounts.length
        ? list.accounts.map((a) => accountRow(a, list.selected))
        : h('p', { class: 'muted' }, 'Brak kont. Dodaj konto Microsoft, żeby móc grać.'),
      s.secureStorage ? null : h('p', { class: 'muted small' }, 'Uwaga: ten system nie oferuje szyfrowania tokenów, zapisane są tylko zakodowane.'),
    ));
  };
})();
