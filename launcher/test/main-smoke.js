'use strict';
// Ładuje main.js z atrapą Electrona i wywołuje jego kanały IPC (bez sieci i bez okna).
// Wyłapuje błędy w main.js, których test interfejsu ani test rdzenia nie zobaczą.

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const Module = require('node:module');
const assert = require('node:assert/strict');

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'nebula-main-'));
const handlers = {};
const sent = [];
const opened = [];

class FakeWindow {
  constructor() {
    this.webContents = {
      setWindowOpenHandler() {}, on() {}, send: (ch, p) => sent.push([ch, p]),
    };
  }
  removeMenu() {}
  loadFile() {}
  isDestroyed() { return false; }
}

const electron = {
  app: {
    requestSingleInstanceLock: () => true, quit() {}, on() {}, whenReady: () => Promise.resolve(),
    getPath: () => tmp, getVersion: () => '9.9.9',
  },
  BrowserWindow: FakeWindow,
  ipcMain: { handle: (ch, fn) => { handlers[ch] = fn; } },
  shell: { openExternal: async (u) => { opened.push(u); }, openPath: async () => '' },
  safeStorage: { isEncryptionAvailable: () => false },
};

const origLoad = Module._load;
Module._load = function (request, ...rest) {
  if (request === 'electron') return electron;
  return origLoad.call(this, request, ...rest);
};

const call = (ch, ...args) => handlers[ch]({}, ...args);

async function main() {
  require('../main.js');
  await new Promise((r) => setTimeout(r, 50)); // whenReady().then(...)

  const expected = ['state:get', 'settings:save', 'instance:save', 'instance:delete', 'versions:list', 'instance:openFolder',
    'open:external', 'auth:login', 'auth:cancel', 'account:select', 'account:remove', 'game:play', 'game:stop'];
  for (const ch of expected) assert.equal(typeof handlers[ch], 'function', 'brak handlera ' + ch);

  // kanały z preload.js muszą pokrywać się z main.js
  const preload = fs.readFileSync(path.join(__dirname, '..', 'preload.js'), 'utf8');
  for (const ch of expected) assert.ok(preload.includes(`'${ch}'`), 'preload nie zna kanału ' + ch);

  // stan początkowy
  let r = await call('state:get');
  assert.equal(r.ok, true, r.error);
  assert.equal(r.data.instances.length, 1);
  assert.equal(r.data.instances[0].mcVersion, '26.2');
  assert.ok(r.data.knownMods.length >= 5);
  assert.deepEqual(r.data.accounts, { selected: '', accounts: [] });
  assert.equal(r.data.secureStorage, false);
  assert.equal(r.data.version, '9.9.9');
  assert.ok(r.data.totalRamMb > 256);

  // ustawienia: zły motyw ignorowany, Client ID przycinany
  r = await call('settings:save', { theme: 'neon', defaultRamMb: 8192, clientId: '  abc  ' });
  assert.equal(r.data.theme, 'neon');
  assert.equal(r.data.clientId, 'abc');
  r = await call('settings:save', { theme: 'hacker' });
  assert.equal(r.data.theme, 'neon', 'nieznany motyw nie może się zapisać');

  // instalacje
  r = await call('instance:save', { name: 'Żółć PvP', mcVersion: '1.21.5', fabric: false, ramMb: 8192, mods: ['sodium'] });
  assert.equal(r.ok, true, r.error);
  const created = r.data;
  assert.match(created.id, /^zolc-pvp-/);
  assert.equal(created.nebula, false, 'Nebula tylko z Fabricem');
  assert.equal(created.ramMb, 8192);
  r = await call('state:get');
  assert.equal(r.data.instances.length, 2);
  r = await call('instance:delete', created.id);
  assert.equal(r.ok, true);
  r = await call('state:get');
  assert.equal(r.data.instances.length, 1);

  // zabezpieczenia i czytelne błędy
  r = await call('open:external', 'http://zla.strona');
  assert.equal(r.ok, false);
  r = await call('open:external', 'https://example.com');
  assert.equal(r.ok, true);
  assert.deepEqual(opened, ['https://example.com']);

  await call('settings:save', { clientId: '' });
  r = await call('auth:login');
  assert.equal(r.ok, false);
  assert.match(r.error, /Client ID/i, 'bez Client ID: jasny komunikat, a nie zagadkowy błąd. Jest: ' + r.error);

  r = await call('game:play', 'nebula');
  assert.equal(r.ok, false);
  assert.ok(r.error && r.error.length > 5, 'game:play bez konta musi zwrócić błąd');
  assert.ok(sent.some(([ch, p]) => ch === 'game:status' && p.state === 'error'), 'interfejs dostaje status błędu');

  r = await call('game:play', 'nie-ma-takiej');
  assert.equal(r.ok, false);

  r = await call('game:stop');
  assert.equal(r.ok, true);

  console.log('OK: main.js ładuje się, wszystkie kanały IPC odpowiadają, błędy są czytelne');
  console.log('komunikat bez konta: ' + (await call('game:play', 'nebula')).error);
}

main()
  .catch((e) => { console.error(e); process.exitCode = 1; })
  .finally(() => fs.rmSync(tmp, { recursive: true, force: true }));
