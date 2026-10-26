'use strict';
// Proces główny Electrona: okno, IPC, logowanie, instalacje i start gry.

const { app, BrowserWindow, ipcMain, shell, safeStorage } = require('electron');
const path = require('node:path');
const os = require('node:os');
const fsp = require('node:fs/promises');

const { Store } = require('./src/core/store');
const { AccountStore, makeCrypt } = require('./src/core/accounts');
const auth = require('./src/core/auth');
const mojang = require('./src/core/mojang');
const launch = require('./src/core/launch');
const { KNOWN_MODS } = require('./src/core/mods');

const THEMES = ['dark', 'light', 'gray', 'neon'];

let win = null;
let root = '';
let store;
let accounts;
let crypt;
let loginFlowId = 0;
let busy = false;
let running = null; // { child, instanceId }

if (!app.requestSingleInstanceLock()) {
  app.quit();
}

function send(channel, payload) {
  if (win && !win.isDestroyed()) win.webContents.send(channel, payload);
}

function createWindow() {
  win = new BrowserWindow({
    width: 1080,
    height: 700,
    minWidth: 920,
    minHeight: 620,
    backgroundColor: '#0f1015',
    title: 'Nebula Launcher',
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  win.removeMenu();
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('https://')) shell.openExternal(url);
    return { action: 'deny' };
  });
  win.webContents.on('will-navigate', (e) => e.preventDefault());
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));
}

/** Rejestruje handler, który zawsze zwraca {ok, data|error}, żeby interfejs pokazał czytelny komunikat. */
function handle(channel, fn) {
  ipcMain.handle(channel, async (_event, ...args) => {
    try {
      return { ok: true, data: await fn(...args) };
    } catch (err) {
      return { ok: false, error: (err && err.message) || String(err) };
    }
  });
}

async function resolveClientId() {
  const s = await store.settings();
  return (s.clientId || process.env.NEBULA_CLIENT_ID || '').trim();
}

function registerIpc() {
  handle('state:get', async () => ({
    settings: await store.settings(),
    instances: await store.instances(),
    accounts: await accounts.list(),
    knownMods: KNOWN_MODS,
    totalRamMb: Math.floor(os.totalmem() / 1048576),
    secureStorage: crypt.secure,
    running: running ? running.instanceId : null,
    version: app.getVersion(),
  }));

  handle('settings:save', async (patch) => {
    const next = {};
    if (THEMES.includes(patch.theme)) next.theme = patch.theme;
    if (patch.defaultRamMb != null) next.defaultRamMb = Number(patch.defaultRamMb);
    if (typeof patch.clientId === 'string') next.clientId = patch.clientId.trim();
    if (typeof patch.javaPath === 'string') next.javaPath = patch.javaPath.trim();
    return store.saveSettings(next);
  });

  handle('instance:save', (inst) => store.saveInstance(inst));
  handle('instance:delete', (id) => store.deleteInstance(id));
  handle('versions:list', () => mojang.listVersions());

  handle('instance:openFolder', async (id) => {
    const dir = store.gameDir(id);
    await fsp.mkdir(dir, { recursive: true });
    await shell.openPath(dir);
  });

  handle('open:external', (url) => {
    if (typeof url === 'string' && url.startsWith('https://')) return shell.openExternal(url);
    throw new Error('Niedozwolony adres');
  });

  // ---- konta
  handle('auth:login', async () => {
    const clientId = await resolveClientId();
    const flow = await auth.startDeviceFlow(clientId);
    const myId = ++loginFlowId;
    shell.openExternal(flow.verificationUri);
    (async () => {
      try {
        const ms = await auth.pollDeviceFlow(clientId, flow, () => myId !== loginFlowId);
        const mc = await auth.minecraftLogin(ms.accessToken);
        await accounts.addFromLogin({ uuid: mc.uuid, name: mc.name, refreshToken: ms.refreshToken });
        send('auth:done', { ok: true, accounts: await accounts.list() });
      } catch (e) {
        if (myId === loginFlowId) send('auth:done', { ok: false, error: e.message });
      }
    })();
    return { userCode: flow.userCode, verificationUri: flow.verificationUri };
  });
  handle('auth:cancel', () => {
    loginFlowId++;
  });
  handle('account:select', async (uuid) => {
    await accounts.select(uuid);
    return accounts.list();
  });
  handle('account:remove', async (uuid) => {
    await accounts.remove(uuid);
    return accounts.list();
  });

  // ---- gra
  handle('game:play', async (instanceId) => {
    if (busy || running) throw new Error('Gra jest już uruchamiana lub działa.');
    busy = true;
    try {
      const settings = await store.settings();
      const inst = (await store.instances()).find((i) => i.id === instanceId);
      if (!inst) throw new Error('Nie znaleziono instalacji.');

      send('game:status', { state: 'preparing', instanceId });
      send('progress', { stage: 'Logowanie' });
      // najpierw logowanie, żeby błąd (np. brak zatwierdzonego Client ID) wyszedł przed pobieraniem plików
      const a = await accounts.launchAuth('', await resolveClientId());

      const gameDir = store.gameDir(inst.id);
      const prepared = await launch.prepare(root, inst, settings, gameDir, (p) => send('progress', p));
      if (prepared.loaderVersion && prepared.loaderVersion !== inst.loaderVersion) {
        await store.saveInstance({ ...inst, loaderVersion: prepared.loaderVersion });
      }

      send('progress', { stage: 'Uruchamiam grę' });
      const child = await launch.start(root, inst, settings, a, prepared, (line) => send('log', line));
      running = { child, instanceId };
      send('game:status', { state: 'running', instanceId, warnings: prepared.warnings });
      child.on('exit', (code) => {
        running = null;
        send('game:status', { state: 'stopped', instanceId, code });
      });
      child.on('error', (err) => {
        running = null;
        send('game:status', { state: 'error', instanceId, error: err.message });
      });
    } catch (e) {
      send('game:status', { state: 'error', instanceId, error: e.message });
      throw e;
    } finally {
      busy = false;
    }
  });

  handle('game:stop', () => {
    if (running) running.child.kill();
  });
}

app.on('second-instance', () => {
  if (win) {
    if (win.isMinimized()) win.restore();
    win.focus();
  }
});

app.whenReady().then(() => {
  root = path.join(app.getPath('appData'), 'NebulaLauncher');
  store = new Store(root);
  crypt = makeCrypt(safeStorage);
  accounts = new AccountStore(path.join(root, 'accounts.json'), crypt);
  registerIpc();
  createWindow();
});

app.on('window-all-closed', () => app.quit());
