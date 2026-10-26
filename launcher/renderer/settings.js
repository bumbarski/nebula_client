'use strict';
// Ekran „Ustawienia”: motyw, domyślny RAM, Java, Client ID.

(function () {
  const { h, call, toast } = N;

  const THEMES = [
    { id: 'dark', name: 'Ciemny', a: '#8b5cf6', b: '#0f1015' },
    { id: 'light', name: 'Jasny', a: '#6d3df0', b: '#f1f2f7' },
    { id: 'gray', name: 'Szary', a: '#5aa9e6', b: '#25272c' },
    { id: 'neon', name: 'Neonowy', a: '#ff2bd6', b: '#00f0ff' },
  ];

  async function save(patch) {
    try {
      N.s.settings = await call('settings:save', patch);
    } catch (e) {
      toast(e.message, 'error');
    }
  }

  N.pages.settings = function settings(view) {
    const s = N.s.settings;
    const maxRam = Math.max(2048, Math.min(65536, Math.floor(N.s.totalRamMb / 512) * 512));

    const ramLabel = h('b', {}, N.gb(s.defaultRamMb));
    const ram = h('input', {
      type: 'range', min: 1024, max: maxRam, step: 512, value: Math.min(s.defaultRamMb, maxRam),
      oninput: (e) => { ramLabel.textContent = N.gb(Number(e.target.value)); },
      onchange: (e) => save({ defaultRamMb: Number(e.target.value) }),
    });

    view.append(
      h('h1', {}, 'Ustawienia'),

      h('div', { class: 'card' },
        h('h2', {}, 'Motyw'),
        h('div', { class: 'themes' }, THEMES.map((t) =>
          h('div', {
            class: 'theme-card' + (s.theme === t.id ? ' sel' : ''),
            onclick: async () => {
              N.s.settings.theme = t.id;
              N.applyTheme();
              await save({ theme: t.id });
              N.go('settings');
            },
          },
            h('span', { class: 'sw', style: `--a:${t.a};--b:${t.b}` }),
            t.name,
          ))),
      ),

      h('div', { class: 'card' },
        h('h2', {}, 'Nowe instalacje'),
        h('div', { class: 'field' },
          h('div', { class: 'row between' }, h('span', { class: 'label' }, 'Domyślna pamięć RAM'), ramLabel),
          ram,
          h('div', { class: 'muted small' }, `Twój komputer ma ${N.gb(N.s.totalRamMb)} RAM. Pamięć każdej instalacji ustawisz osobno w zakładce Instalacje.`),
        ),
      ),

      h('div', { class: 'card' },
        h('h2', {}, 'Zaawansowane'),
        h('label', { class: 'field' },
          h('span', {}, 'Ścieżka do Javy (dla wszystkich instalacji)'),
          h('input', { type: 'text', value: s.javaPath, placeholder: 'puste = launcher pobierze Javę sam', onchange: (e) => save({ javaPath: e.target.value }) }),
        ),
        h('label', { class: 'field' },
          h('span', {}, 'Client ID aplikacji Microsoft'),
          h('input', { type: 'text', value: s.clientId, placeholder: 'GUID z Azure, zatwierdzony przez Mojang', onchange: (e) => save({ clientId: e.target.value }) }),
        ),
      ),

      h('p', { class: 'muted small' }, 'Nebula Launcher ' + N.s.version),
    );
  };
})();
