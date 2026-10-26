'use strict';
// Ekran „Instalacje”: lista oraz okno edycji (wersja, RAM, mody, Java).

(function () {
  const { h, call, toast } = N;

  let versionsCache = null;
  async function loadVersions() {
    if (!versionsCache) versionsCache = await call('versions:list');
    return versionsCache;
  }

  function pills(inst) {
    return h('div', {},
      h('span', { class: 'pill' }, 'Minecraft ' + inst.mcVersion),
      h('span', { class: 'pill' }, inst.fabric ? 'Fabric' : 'Vanilla'),
      h('span', { class: 'pill' }, N.gb(inst.ramMb) + ' RAM'),
      inst.fabric && inst.nebula ? h('span', { class: 'pill' }, 'Nebula Client') : null,
    );
  }

  function card(inst) {
    const selected = inst.id === N.current().id;
    const running = N.s.running === inst.id;
    return h('div', { class: 'card inst' },
      h('div', { class: 'grow' },
        h('h3', {}, inst.name, selected ? '  ✓' : ''),
        pills(inst),
      ),
      h('div', { class: 'row' },
        h('button', {
          class: 'btn', disabled: selected,
          onclick: () => { N.selectInstance(inst.id); N.go('instances'); toast('Wybrano: ' + inst.name, 'ok'); },
        }, selected ? 'Wybrana' : 'Wybierz'),
        h('button', { class: 'btn', onclick: () => openEditor(inst) }, 'Edytuj'),
        h('button', { class: 'btn', onclick: () => call('instance:openFolder', inst.id).catch((e) => toast(e.message, 'error')) }, 'Folder'),
        h('button', {
          class: 'btn danger', disabled: running,
          onclick: async () => {
            if (!confirm(`Usunąć instalację „${inst.name}” z listy?\nPliki gry (światy, ustawienia) zostaną na dysku.`)) return;
            try { await call('instance:delete', inst.id); await N.refresh(); } catch (e) { toast(e.message, 'error'); }
          },
        }, 'Usuń'),
      ),
    );
  }

  N.pages.instances = function instances(view) {
    view.append(
      h('div', { class: 'row between' },
        h('h1', {}, 'Instalacje'),
        h('button', { class: 'btn primary', onclick: () => openEditor(null) }, '+ Nowa instalacja'),
      ),
      ...N.s.instances.map(card),
    );
  };

  // ---------- okno edycji ----------

  function openEditor(inst) {
    const known = N.s.knownMods;
    const draft = inst
      ? { ...inst, mods: [...inst.mods] }
      : {
          id: '', name: 'Nowa instalacja', mcVersion: '26.2', fabric: true, nebula: true,
          mods: known.filter((m) => m.recommended).map((m) => m.slug),
          ramMb: N.s.settings.defaultRamMb, jvmArgs: '', javaPath: '', loaderVersion: '',
        };

    // RAM: od 1 GB do ~całej pamięci komputera (co 0,5 GB), ale nie mniej niż 2 GB zakresu.
    const maxRam = Math.max(2048, Math.min(65536, Math.floor(N.s.totalRamMb / 512) * 512));
    draft.ramMb = Math.min(Math.max(draft.ramMb, 1024), maxRam);

    // --- nazwa
    const name = h('input', { type: 'text', value: draft.name, maxlength: '40', oninput: (e) => { draft.name = e.target.value; } });

    // --- wersja
    const versionSelect = h('select', { onchange: (e) => { draft.mcVersion = e.target.value; syncNebulaNote(); } });
    let showOld = false;
    let versions = null;
    function fillVersions() {
      const list = versions
        ? versions.filter((v) => showOld || v.type === 'release' || v.id === draft.mcVersion)
        : [{ id: draft.mcVersion, type: 'release' }];
      versionSelect.replaceChildren(
        ...list.map((v) => h('option', { value: v.id }, v.type === 'release' ? v.id : `${v.id} (${v.type})`)),
      );
      versionSelect.value = draft.mcVersion;
    }
    fillVersions();
    loadVersions().then((v) => { versions = v; fillVersions(); }).catch(() => toast('Nie udało się pobrać listy wersji (brak sieci?)', 'error'));

    // --- Fabric / Nebula / mody
    const nebulaNote = h('small', {});
    const nebulaBox = h('input', { type: 'checkbox', checked: draft.nebula, onchange: (e) => { draft.nebula = e.target.checked; } });
    const modBoxes = [];
    function syncNebulaNote() {
      nebulaNote.textContent = draft.mcVersion === '26.2' ? '' : 'Nebula jest zbudowana pod Minecrafta 26.2';
    }
    function syncFabric() {
      nebulaBox.disabled = !draft.fabric;
      for (const b of modBoxes) b.disabled = !draft.fabric;
    }
    const fabricBox = h('input', { type: 'checkbox', checked: draft.fabric, onchange: (e) => { draft.fabric = e.target.checked; syncFabric(); } });

    const modRows = known.map((m) => {
      const box = h('input', {
        type: 'checkbox', checked: draft.mods.includes(m.slug),
        onchange: (e) => {
          draft.mods = e.target.checked ? [...new Set([...draft.mods, m.slug])] : draft.mods.filter((s) => s !== m.slug);
        },
      });
      modBoxes.push(box);
      return h('label', { class: 'check' }, box, m.name, h('small', {}, m.desc));
    });

    // --- RAM
    const ramValue = h('b', {});
    const ramWarn = h('div', { class: 'small', style: 'color: var(--warn); min-height: 18px' });
    const ramRange = h('input', {
      type: 'range', min: 1024, max: maxRam, step: 512, value: draft.ramMb,
      oninput: (e) => setRam(Number(e.target.value)),
    });
    function setRam(mb) {
      draft.ramMb = mb;
      ramRange.value = mb;
      ramValue.textContent = N.gb(mb);
      ramWarn.textContent = mb > N.s.totalRamMb * 0.75 ? 'To ponad 75% pamięci komputera: system może zwalniać.' : '';
    }
    const quick = [2, 4, 6, 8, 12, 16].filter((g) => g * 1024 <= maxRam).map((g) =>
      h('button', { type: 'button', class: 'btn', onclick: () => setRam(g * 1024) }, g + ' GB'));
    setRam(draft.ramMb);

    // --- Java
    const javaPath = h('input', { type: 'text', value: draft.javaPath, placeholder: 'puste = launcher pobierze Javę sam', oninput: (e) => { draft.javaPath = e.target.value; } });
    const jvmArgs = h('input', { type: 'text', value: draft.jvmArgs, placeholder: 'np. -XX:+UseG1GC', oninput: (e) => { draft.jvmArgs = e.target.value; } });

    const overlay = h('div', { class: 'overlay', onmousedown: (e) => { if (e.target === overlay) close(); } });
    function close() {
      document.removeEventListener('keydown', onKey);
      overlay.remove();
    }
    function onKey(e) { if (e.key === 'Escape') close(); }
    document.addEventListener('keydown', onKey);

    async function save() {
      if (!draft.name.trim()) { toast('Podaj nazwę instalacji', 'error'); return; }
      try {
        const saved = await call('instance:save', draft);
        if (!inst) N.selectInstance(saved.id);
        close();
        await N.refresh();
        toast('Zapisano: ' + saved.name, 'ok');
      } catch (e) {
        toast(e.message, 'error');
      }
    }

    overlay.append(h('div', { class: 'modal' },
      h('h2', {}, inst ? 'Edytuj instalację' : 'Nowa instalacja'),
      h('label', { class: 'field' }, h('span', {}, 'Nazwa'), name),
      h('div', { class: 'grid2' },
        h('div', { class: 'field' },
          h('span', {}, 'Wersja Minecrafta'), versionSelect,
          h('label', { class: 'check' },
            h('input', { type: 'checkbox', onchange: (e) => { showOld = e.target.checked; fillVersions(); } }),
            'Pokaż snapshoty i stare wersje'),
        ),
        h('div', { class: 'field' },
          h('span', {}, 'Loader'),
          h('label', { class: 'check' }, fabricBox, 'Fabric (wymagany do modów)'),
          h('label', { class: 'check' }, nebulaBox, 'Nebula Client', nebulaNote),
        ),
      ),
      h('div', { class: 'field' },
        h('div', { class: 'row between' }, h('span', { class: 'label' }, 'Pamięć RAM'), ramValue),
        ramRange,
        h('div', { class: 'row' }, quick),
        ramWarn,
      ),
      h('div', { class: 'field' }, h('span', {}, 'Mody (Fabric API dodaje się automatycznie)'), modRows),
      h('div', { class: 'grid2' },
        h('label', { class: 'field' }, h('span', {}, 'Ścieżka do Javy'), javaPath),
        h('label', { class: 'field' }, h('span', {}, 'Dodatkowe argumenty JVM'), jvmArgs),
      ),
      h('div', { class: 'actions' },
        h('button', { class: 'btn', onclick: close }, 'Anuluj'),
        h('button', { class: 'btn primary', onclick: save }, 'Zapisz'),
      ),
    ));

    syncNebulaNote();
    syncFabric();
    document.body.append(overlay);
    name.focus();
    name.select();
  }
})();
