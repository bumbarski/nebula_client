'use strict';
// Test interfejsu w Chromium z atrapą window.api (bez Electrona).
// Uruchom: node test/ui-smoke.js [katalog-na-zrzuty]
// Sprawdza, czy ekrany się rysują, kontrolki działają i w konsoli nie ma błędów. Zapisuje zrzuty ekranu.

const path = require('node:path');
const fs = require('node:fs');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const { KNOWN_MODS } = require('../src/core/mods');

const outDir = path.resolve(process.argv[2] || path.join(__dirname, '..', 'shots'));
fs.mkdirSync(outDir, { recursive: true });
const page_url = 'file://' + path.join(__dirname, '..', 'renderer', 'index.html');

/** Atrapa mostu preload.js: stan w pamięci, wywołania zwracają {ok, data}. */
function installMock(known) {
  const listeners = {};
  const state = {
    settings: { theme: 'dark', defaultRamMb: 4096, clientId: '', javaPath: '' },
    instances: [
      { id: 'nebula', name: 'Nebula (26.2)', mcVersion: '26.2', fabric: true, nebula: true, mods: ['sodium', 'lithium', 'ferrite-core', 'in-game-account-switcher'], ramMb: 4096, jvmArgs: '', javaPath: '', loaderVersion: '' },
      { id: 'survival', name: 'Survival 1.21.5', mcVersion: '1.21.5', fabric: false, nebula: false, mods: [], ramMb: 8192, jvmArgs: '', javaPath: '', loaderVersion: '' },
    ],
    accounts: { selected: '', accounts: [] },
    knownMods: known, totalRamMb: 16384, secureStorage: true, running: null, version: '0.1.0',
  };
  const calls = [];
  window.__mock = { state, calls, emit: (ch, p) => (listeners[ch] || []).forEach((f) => f(p)) };
  window.api = {
    on(ch, fn) { (listeners[ch] = listeners[ch] || []).push(fn); return () => {}; },
    async call(ch, ...args) {
      calls.push([ch, ...args]);
      const ok = (data) => ({ ok: true, data: JSON.parse(JSON.stringify(data === undefined ? null : data)) });
      switch (ch) {
        case 'state:get': return ok(state);
        case 'settings:save': Object.assign(state.settings, args[0]); return ok(state.settings);
        case 'instance:save': {
          const inst = { ...args[0], id: args[0].id || 'new-' + state.instances.length };
          const i = state.instances.findIndex((x) => x.id === inst.id);
          if (i >= 0) state.instances[i] = inst; else state.instances.push(inst);
          return ok(inst);
        }
        case 'versions:list': return ok([
          { id: '26.2', type: 'release' }, { id: '26.3-snapshot-1', type: 'snapshot' },
          { id: '1.21.5', type: 'release' }, { id: '1.8.9', type: 'release' }, { id: 'b1.7.3', type: 'old_beta' },
        ]);
        case 'auth:login': return ok({ userCode: 'ABCD-1234', verificationUri: 'https://microsoft.com/link' });
        case 'game:play': return ok(null);
        default: return ok(null);
      }
    },
  };
}

async function main() {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1080, height: 700 } });
  const problems = [];
  page.on('pageerror', (e) => problems.push('pageerror: ' + e.message));
  page.on('console', (m) => {
    // awatary graczy to zewnętrzne obrazki: brak sieci w teście jest niegroźny (onerror je usuwa)
    if (m.type() === 'error' && !m.text().startsWith('Failed to load resource')) problems.push('console: ' + m.text());
  });
  await page.addInitScript(installMock, KNOWN_MODS);

  const shot = (name) => page.screenshot({ path: path.join(outDir, name + '.png') });
  const text = (sel) => page.locator(sel).first().innerText();

  await page.goto(page_url);
  await page.waitForSelector('.hero');

  // 1. Graj bez konta: przycisk zablokowany, jasny komunikat
  assert.equal(await page.locator('.play').isDisabled(), true, 'GRAJ powinno być zablokowane bez konta');
  assert.match(await text('.status'), /dodaj konto/i);
  await shot('01-graj-bez-konta');

  // 2. Z kontem i postępem pobierania
  await page.evaluate(() => {
    const uuid = '069a79f4-44e9-4726-a5be-fca90e38aaf5';
    __mock.state.accounts = { selected: uuid, accounts: [{ uuid, name: 'Notch' }] };
    __mock.state.settings.clientId = '01234567-89ab-cdef-0123-456789abcdef';
    return N.refresh();
  });
  assert.equal(await page.locator('.play').isDisabled(), false, 'GRAJ powinno działać z kontem');
  assert.match(await text('#sidebar-foot'), /Notch/, 'pasek boczny pokazuje wybrane konto');
  await page.click('.play');
  await page.evaluate(() => __mock.emit('progress', { stage: 'Pliki gry', done: 420, total: 1800 }));
  assert.match(await text('.status'), /Pliki gry 420\/1800/);
  const w = await page.locator('.bar > i').evaluate((e) => e.style.width);
  assert.ok(Math.abs(parseFloat(w) - 23.33) < 0.5, 'pasek postępu: ' + w);
  assert.equal(await page.locator('.play').isDisabled(), true, 'przycisk zablokowany w trakcie przygotowania');
  await shot('02-graj-pobieranie');

  // 3. Gra działa, dziennik, ostrzeżenie o niedostępnym modzie
  await page.evaluate(() => {
    __mock.emit('game:status', { state: 'running', instanceId: 'nebula', warnings: ['Lithium: brak wersji dla 26.2'] });
    ['[main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.3', '[main/INFO]: Nebula Client załadowany', '[Render thread/INFO]: Setting user: Notch'].forEach((l) => __mock.emit('log', l));
  });
  assert.equal(await text('.play'), 'ZATRZYMAJ');
  assert.match(await text('.notice.warn'), /Lithium/);
  assert.match(await text('pre#log'), /Nebula Client załadowany/);
  await shot('03-graj-dziala');
  await page.evaluate(() => __mock.emit('game:status', { state: 'stopped', instanceId: 'nebula', code: 0 }));
  assert.equal(await text('.play'), 'GRAJ');

  // 4. Instalacje + edytor (RAM, wersje, mody)
  await page.click('.nav[data-page="instances"]');
  assert.equal(await page.locator('.card.inst').count(), 2);
  await shot('04-instalacje');
  await page.locator('.card.inst').nth(1).getByText('Edytuj').click();
  await page.waitForSelector('.modal');
  assert.equal(await page.locator('.modal input[type=range]').inputValue(), '8192');
  await page.getByRole('button', { name: '12 GB' }).click();
  assert.match(await text('.modal .row.between b'), /12 GB/);
  assert.equal(await page.locator('.modal input[type=range]').inputValue(), '12288');
  assert.equal(await page.getByRole('button', { name: '16 GB' }).count(), 1, 'dostępne skróty do 16 GB (komputer ma 16 GB)');
  assert.equal(await page.locator('.modal .check input').nth(3).isDisabled(), true, 'mody wyłączone bez Fabrica (Nebula)');
  await page.locator('.modal select').selectOption('1.8.9').catch(() => {});
  await shot('05-edytor-instalacji');
  await page.getByRole('button', { name: 'Zapisz' }).click();
  await page.waitForSelector('.modal', { state: 'detached' });
  const saved = await page.evaluate(() => __mock.calls.filter((c) => c[0] === 'instance:save').pop()[1]);
  assert.equal(saved.ramMb, 12288);
  assert.equal(saved.id, 'survival', 'edycja zachowuje id');

  // 5. Nowa instalacja: domyślnie zaznaczone polecane mody, suwak RAM z ustawień
  await page.getByText('+ Nowa instalacja').click();
  await page.waitForSelector('.modal');
  assert.equal(await page.locator('.modal input[type=range]').inputValue(), '4096');
  const checked = await page.locator('.modal .check input:checked').count();
  assert.ok(checked >= 5, 'polecane mody zaznaczone, jest: ' + checked);
  await page.keyboard.press('Escape');
  await page.waitForSelector('.modal', { state: 'detached' });

  // 6. Konta: bez Client ID (ostrzeżenie) i z loginem (kod)
  await page.evaluate(() => { __mock.state.settings.clientId = ''; __mock.state.accounts = { selected: '', accounts: [] }; return N.refresh(); });
  await page.click('.nav[data-page="accounts"]');
  assert.match(await text('.card h2'), /Client ID/);
  assert.equal(await page.getByText('+ Dodaj konto Microsoft').isDisabled(), true);
  await shot('06-konta-bez-client-id');
  await page.evaluate(() => {
    __mock.state.settings.clientId = '01234567-89ab-cdef-0123-456789abcdef';
    __mock.state.accounts = { selected: 'a', accounts: [{ uuid: 'a', name: 'Notch' }, { uuid: 'b', name: 'jeb_' }] };
    return N.refresh();
  });
  await page.click('.nav[data-page="accounts"]');
  assert.equal(await page.locator('.account').count(), 2);
  assert.equal(await page.locator('.account.sel').count(), 1);
  await page.getByText('+ Dodaj konto Microsoft').click();
  await page.waitForSelector('.code');
  assert.equal(await text('.code'), 'ABCD-1234');
  await shot('07-konta-logowanie');

  // 7. Ustawienia i wszystkie 4 motywy
  await page.click('.nav[data-page="settings"]');
  for (const [id, label] of [['gray', 'Szary'], ['light', 'Jasny'], ['neon', 'Neonowy'], ['dark', 'Ciemny']]) {
    await page.locator('.theme-card', { hasText: label }).click();
    assert.equal(await page.evaluate(() => document.documentElement.dataset.theme), id);
    assert.equal(await page.evaluate(() => __mock.state.settings.theme), id, 'motyw zapisany w ustawieniach');
    await shot('08-ustawienia-' + id);
  }
  // motyw na ekranie Graj
  for (const id of ['neon', 'light']) {
    await page.evaluate((t) => { __mock.state.settings.theme = t; return N.refresh(); }, id);
    await page.click('.nav[data-page="play"]');
    await shot('09-graj-' + id);
  }

  await browser.close();
  assert.deepEqual(problems, [], 'błędy w konsoli:\n' + problems.join('\n'));
  console.log('OK: interfejs działa, zrzuty w ' + outDir);
}

main().catch((e) => { console.error(e); process.exit(1); });
