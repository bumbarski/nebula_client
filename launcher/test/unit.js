'use strict';
// Testy offline rdzenia launchera (bez prawdziwej sieci). Uruchom: node test/unit.js

const assert = require('node:assert/strict');
const http = require('node:http');
const os = require('node:os');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const U = require('../src/core/util');
const mojang = require('../src/core/mojang');
const modrinth = require('../src/core/modrinth');
const nebula = require('../src/core/nebula');
const auth = require('../src/core/auth');
const mods = require('../src/core/mods');
const { AccountStore, makeCrypt } = require('../src/core/accounts');
const { Store, slugify } = require('../src/core/store');

const tests = [];
const test = (name, fn) => tests.push({ name, fn });
const tmp = () => fs.mkdtempSync(path.join(os.tmpdir(), 'nebula-test-'));

// ---------- atrapa sieci: lokalny serwer przechodzi do prawdziwego fetch, reszta do mocka
const realFetch = globalThis.fetch;
let mock = async () => {
  throw new Error('brak mocka');
};
globalThis.fetch = (url, init) =>
  String(url).startsWith('http://127.0.0.1') ? realFetch(url, init) : mock(String(url), init || {});
const json = (status, data) => new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } });

// ---------- reguły i biblioteki
test('ruleAllows: system i brak reguł', () => {
  const { os: me } = U.platform();
  assert.equal(mojang.ruleAllows(undefined), true);
  assert.equal(mojang.ruleAllows([{ action: 'allow' }]), true);
  assert.equal(mojang.ruleAllows([{ action: 'allow', os: { name: me } }]), true);
  assert.equal(mojang.ruleAllows([{ action: 'allow', os: { name: me === 'windows' ? 'linux' : 'windows' } }]), false);
  // allow wszystko, ale zabroń na tym systemie -> zabronione (wygrywa ostatnia pasująca)
  assert.equal(mojang.ruleAllows([{ action: 'allow' }, { action: 'disallow', os: { name: me } }]), false);
  // cechy (features) bez wartości są fałszem
  assert.equal(mojang.ruleAllows([{ action: 'allow', features: { is_demo_user: true } }]), false);
});

test('libraryEntries: reguły, bez downloads (Fabric), natywne klasyfikatory, deduplikacja', () => {
  const { os: me } = U.platform();
  const other = me === 'windows' ? 'linux' : 'windows';
  const ver = {
    libraries: [
      { name: 'net.fabricmc:fabric-loader:0.19.3', url: 'https://maven.fabricmc.net/', sha1: 'abc', size: 5 },
      { name: 'org.ow2.asm:asm:9.9', url: 'https://maven.fabricmc.net/' },
      {
        name: 'com.mojang:logging:1.0',
        downloads: { artifact: { path: 'com/mojang/logging/1.0/logging-1.0.jar', url: 'https://x/l.jar', sha1: 's', size: 1 } },
      },
      {
        name: 'org.lwjgl:lwjgl:3.3.3:natives-mine',
        rules: [{ action: 'allow', os: { name: me } }],
        downloads: { artifact: { path: 'org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-mine.jar', url: 'https://x/n.jar', sha1: 'n', size: 2 } },
      },
      {
        name: 'org.lwjgl:lwjgl:3.3.3:natives-other',
        rules: [{ action: 'allow', os: { name: other } }],
        downloads: { artifact: { path: 'p', url: 'u', sha1: 'q', size: 3 } },
      },
      { name: 'old:lib:1', natives: { linux: 'natives-linux' } },
      { name: 'org.ow2.asm:asm:9.0', url: 'https://libraries.minecraft.net/' }, // duplikat asm, pierwszy wygrywa
    ],
  };
  const list = mojang.libraryEntries(ver);
  const names = list.map((l) => l.name);
  assert.deepEqual(names, [
    'net.fabricmc:fabric-loader:0.19.3',
    'org.ow2.asm:asm:9.9',
    'com.mojang:logging:1.0',
    'org.lwjgl:lwjgl:3.3.3:natives-mine',
  ]);
  assert.equal(list[0].path, 'net/fabricmc/fabric-loader/0.19.3/fabric-loader-0.19.3.jar');
  assert.equal(list[0].url, 'https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.19.3/fabric-loader-0.19.3.jar');
  assert.equal(list[1].url, 'https://maven.fabricmc.net/org/ow2/asm/asm/9.9/asm-9.9.jar');
});

test('mergeProfile: biblioteki profilu pierwsze, argumenty i mainClass łączone', () => {
  const parent = {
    id: '26.2',
    type: 'release',
    mainClass: 'net.minecraft.client.main.Main',
    assetIndex: { id: '99' },
    assets: '99',
    downloads: { client: { url: 'c' } },
    javaVersion: { component: 'java-runtime-epsilon', majorVersion: 25 },
    libraries: [{ name: 'a:b:1' }],
    arguments: { game: ['--username', '${auth_player_name}'], jvm: ['-cp', '${classpath}'] },
  };
  const child = {
    id: 'fabric-loader-0.19.3-26.2',
    inheritsFrom: '26.2',
    mainClass: 'net.fabricmc.loader.impl.launch.knot.KnotClient',
    libraries: [{ name: 'c:d:2' }],
    arguments: { game: [], jvm: ['-DFabricMcEmu= net.minecraft.client.main.Main '] },
  };
  const m = mojang.mergeProfile(parent, child);
  assert.equal(m.mainClass, 'net.fabricmc.loader.impl.launch.knot.KnotClient');
  assert.deepEqual(m.libraries.map((l) => l.name), ['c:d:2', 'a:b:1']);
  assert.deepEqual(m.arguments.jvm, ['-cp', '${classpath}', '-DFabricMcEmu= net.minecraft.client.main.Main ']);
  assert.equal(m._clientId, '26.2');
  assert.equal(m.javaVersion.majorVersion, 25);
  assert.equal(m.id, 'fabric-loader-0.19.3-26.2');
});

test('buildCommand: podstawia zmienne, pamięć, reguły, nic nie zostaje jako ${...}', () => {
  const ver = {
    id: 'fabric-loader-0.19.3-26.2',
    _clientId: '26.2',
    type: 'release',
    mainClass: 'net.fabricmc.loader.impl.launch.knot.KnotClient',
    assetIndex: { id: '30' },
    arguments: {
      jvm: [
        { rules: [{ action: 'allow', os: { name: 'nieistniejacy' } }], value: '-Dnie.powinno=byc' },
        '-Djava.library.path=${natives_directory}',
        '-Dlauncher=${launcher_name}/${launcher_version}',
        '-cp',
        '${classpath}',
      ],
      game: [
        '--username', '${auth_player_name}', '--version', '${version_name}', '--gameDir', '${game_directory}',
        '--assetsDir', '${assets_root}', '--assetIndex', '${assets_index_name}', '--uuid', '${auth_uuid}',
        '--accessToken', '${auth_access_token}', '--clientId', '${clientid}', '--xuid', '${auth_xuid}',
        '--userType', '${user_type}', '--versionType', '${version_type}',
        { rules: [{ action: 'allow', features: { has_custom_resolution: true } }], value: ['--width', '${resolution_width}'] },
      ],
    },
  };
  const c = mojang.buildCommand({
    ver, javaPath: '/java', classpath: ['/a.jar', '/b.jar'], gameDir: '/g', assetsDir: '/as', root: '/r',
    auth: { name: 'Steve', uuid: 'u-u-i-d', accessToken: 'TOKEN' }, ramMb: 8192, jvmArgs: '-XX:+UseG1GC "-Dx=a b"',
  });
  const joined = c.args.join('\u0001');
  assert.ok(!joined.includes('${'), 'zostały niepodstawione zmienne: ' + c.args.filter((a) => a.includes('${')));
  assert.ok(!c.args.includes('-Dnie.powinno=byc'));
  assert.ok(!c.args.includes('--width'));
  assert.equal(c.args[0], '-Xmx8192M');
  assert.equal(c.args[1], '-Xms1024M');
  assert.ok(c.args.includes('-XX:+UseG1GC'));
  assert.ok(c.args.includes('-Dx=a b'));
  const sep = process.platform === 'win32' ? ';' : ':';
  assert.equal(c.args[c.args.indexOf('-cp') + 1], `/a.jar${sep}/b.jar`);
  assert.equal(c.args[c.args.indexOf('--username') + 1], 'Steve');
  assert.equal(c.args[c.args.indexOf('--accessToken') + 1], 'TOKEN');
  assert.equal(c.args[c.args.indexOf('net.fabricmc.loader.impl.launch.knot.KnotClient') + 1], '--username');
  assert.throws(() => mojang.buildCommand({ ver: { id: 'old' }, auth: {}, classpath: [] }), /zbyt stara/);
});

test('parseArgs i slugify', () => {
  assert.deepEqual(U.parseArgs('-Xa  "-Db=c d" \'-Ee f\' -G'), ['-Xa', '-Db=c d', '-Ee f', '-G']);
  assert.deepEqual(U.parseArgs(''), []);
  assert.equal(slugify('Zażółć Gęślą Jaźń!!'), 'zazolc-gesla-jazn');
  assert.equal(slugify('###'), 'instalacja');
});

// ---------- Modrinth i Nebula
test('modrinth.pickVersion i nebula.pickAsset', () => {
  assert.equal(modrinth.pickVersion([]), null);
  const list = [{ version_type: 'beta', id: 1 }, { version_type: 'release', id: 2 }, { version_type: 'alpha', id: 3 }];
  assert.equal(modrinth.pickVersion(list).id, 2);
  assert.equal(modrinth.pickVersion([{ version_type: 'alpha', id: 9 }]).id, 9);
  const a = nebula.pickAsset([{ name: 'nebula-client-0.1.0-sources.jar' }, { name: 'nebula-client-0.1.0.jar' }]);
  assert.equal(a.name, 'nebula-client-0.1.0.jar');
  assert.equal(nebula.pickAsset([{ name: 'inny.jar' }]), null);
  const multi = [{ name: 'nebula-client-0.1.0-mc26.2.jar' }, { name: 'nebula-client-0.1.0-mc26.1.2.jar' }];
  assert.equal(nebula.pickAsset(multi, '26.1.2').name, 'nebula-client-0.1.0-mc26.1.2.jar');
  assert.equal(nebula.pickAsset(multi, '26.2').name, 'nebula-client-0.1.0-mc26.2.jar');
  assert.equal(nebula.pickAsset(multi, '1.21.4'), null);
});

test('modrinth.resolve: wersje, zależności wymagane, brakujący mod daje ostrzeżenie', async () => {
  const mk = (pid, name, deps = []) => [
    {
      version_type: 'release', project_id: pid, name,
      files: [{ primary: true, filename: name + '.jar', url: 'https://cdn/' + name + '.jar', size: 1, hashes: { sha512: 'x', sha1: 'y' } }],
      dependencies: deps,
    },
  ];
  const calls = [];
  mock = async (url) => {
    calls.push(url);
    if (url.includes('/project/sodium/version')) return json(200, mk('P_SODIUM', 'sodium', [{ dependency_type: 'required', project_id: 'P_LIB' }, { dependency_type: 'optional', project_id: 'P_OPT' }]));
    if (url.includes('/project/P_LIB/version')) return json(200, mk('P_LIB', 'libmod'));
    if (url.includes('/project/fabric-api/version')) return json(200, mk('P_FAPI', 'fabric-api'));
    if (url.includes('/project/brak/version')) return json(404, {});
    if (url.includes('/project/pusty/version')) return json(200, []);
    throw new Error('niespodziewane: ' + url);
  };
  const r = await modrinth.resolve(['fabric-api', 'sodium', 'brak', 'pusty'], '26.2');
  assert.deepEqual(r.files.map((f) => f.filename).sort(), ['fabric-api.jar', 'libmod.jar', 'sodium.jar']);
  assert.equal(r.warnings.length, 2);
  assert.ok(r.warnings.every((w) => /26\.2/.test(w)));
  assert.ok(calls.every((u) => !u.includes('P_OPT')), 'opcjonalna zależność nie powinna być pobierana');
  assert.ok(calls[0].includes(encodeURIComponent('["26.2"]')) && calls[0].includes(encodeURIComponent('["fabric"]')));
});

// ---------- pobieranie z weryfikacją i instalacja modów (lokalny serwer)
function startServer(files) {
  return new Promise((resolve) => {
    const hits = {};
    const srv = http.createServer((req, res) => {
      hits[req.url] = (hits[req.url] || 0) + 1;
      const body = files[req.url];
      if (!body) {
        res.writeHead(404).end();
        return;
      }
      res.writeHead(200, { 'Content-Length': body.length }).end(body);
    });
    srv.listen(0, '127.0.0.1', () => resolve({ srv, hits, base: `http://127.0.0.1:${srv.address().port}` }));
  });
}
const sha = (algo, buf) => crypto.createHash(algo).update(buf).digest('hex');

test('download: weryfikuje sumę, pomija już pobrane, odrzuca uszkodzone', async () => {
  const body = Buffer.from('zawartość pliku');
  const { srv, hits, base } = await startServer({ '/f.bin': body });
  const dir = tmp();
  try {
    const dest = path.join(dir, 'a', 'b', 'f.bin');
    assert.equal(await U.download(base + '/f.bin', dest, { sha1: sha('sha1', body), size: body.length }), true);
    assert.equal(fs.readFileSync(dest).toString(), 'zawartość pliku');
    assert.equal(await U.download(base + '/f.bin', dest, { sha1: sha('sha1', body), size: body.length }), false);
    assert.equal(hits['/f.bin'], 1);
    await assert.rejects(U.download(base + '/f.bin', path.join(dir, 'zly.bin'), { sha1: 'deadbeef' }), /Niezgodna/);
    assert.equal(fs.existsSync(path.join(dir, 'zly.bin')), false);
    assert.equal(fs.existsSync(path.join(dir, 'zly.bin.part')), false);
    await assert.rejects(U.download(base + '/nie-ma', path.join(dir, 'x')), /HTTP 404/);
  } finally {
    srv.close();
  }
});

test('installMods: pobiera mody i Nebulę, sprząta tylko swoje stare pliki', async () => {
  const sodium = Buffer.from('sodium-v1');
  const fapi = Buffer.from('fapi-v1');
  const neb = Buffer.from('nebula-v1');
  const { srv, base } = await startServer({ '/sodium.jar': sodium, '/fapi.jar': fapi, '/neb.jar': neb });
  const game = tmp();
  try {
    const mod = (pid, file, buf, url) => [{
      version_type: 'release', project_id: pid, name: file,
      files: [{ primary: true, filename: file, url, size: buf.length, hashes: { sha512: sha('sha512', buf), sha1: sha('sha1', buf) } }],
      dependencies: [],
    }];
    mock = async (url) => {
      if (url.includes('/project/sodium/version')) return json(200, mod('P1', 'sodium-1.jar', sodium, base + '/sodium.jar'));
      if (url.includes('/project/fabric-api/version')) return json(200, mod('P2', 'fabric-api-1.jar', fapi, base + '/fapi.jar'));
      if (url.includes('/releases/latest')) {
        return json(200, { tag_name: 'build-1', assets: [{ name: 'nebula-client-0.1.0.jar', browser_download_url: base + '/neb.jar', size: neb.length }] });
      }
      throw new Error('niespodziewane: ' + url);
    };

    fs.mkdirSync(path.join(game, 'mods'), { recursive: true });
    fs.writeFileSync(path.join(game, 'mods', 'moj-wlasny-mod.jar'), 'uzytkownika');
    fs.writeFileSync(path.join(game, 'mods', 'stary-sodium.jar'), 'stary');
    fs.writeFileSync(path.join(game, 'mods', '.nebula-managed.json'), JSON.stringify({ files: ['stary-sodium.jar'] }));

    const inst = { mcVersion: '26.2', fabric: true, nebula: true, mods: ['sodium'] };
    const r = await mods.installMods(game, inst);
    const files = fs.readdirSync(path.join(game, 'mods')).filter((f) => f.endsWith('.jar')).sort();
    assert.deepEqual(files, ['fabric-api-1.jar', 'moj-wlasny-mod.jar', 'nebula-client-0.1.0.jar', 'sodium-1.jar']);
    assert.deepEqual(r.warnings, []);

    // bez Fabrica: nie dociągamy modów Fabrica, a stare pliki zarządzane znikają
    const r2 = await mods.installMods(game, { ...inst, fabric: false, nebula: false });
    const after = fs.readdirSync(path.join(game, 'mods')).filter((f) => f.endsWith('.jar'));
    assert.deepEqual(after, ['moj-wlasny-mod.jar']);
    assert.deepEqual(r2.installed, []);
  } finally {
    srv.close();
  }
});

// ---------- logowanie Microsoft (atrapa)
function authMock({ xblStatus = 200, xstsStatus = 200, xstsErr, mcStatus = 200, profileStatus = 200 } = {}) {
  return async (url) => {
    if (url.includes('user.auth.xboxlive.com')) {
      return xblStatus === 200 ? json(200, { Token: 'XBL', DisplayClaims: { xui: [{ uhs: 'UHS' }] } }) : json(xblStatus, {});
    }
    if (url.includes('xsts.auth.xboxlive.com')) {
      return xstsStatus === 200 ? json(200, { Token: 'XSTS' }) : json(xstsStatus, { XErr: xstsErr });
    }
    if (url.includes('login_with_xbox')) {
      return mcStatus === 200 ? json(200, { access_token: 'MCTOKEN', expires_in: 86400 }) : json(mcStatus, { error: 'FORBIDDEN' });
    }
    if (url.includes('/minecraft/profile')) {
      return profileStatus === 200 ? json(200, { id: 'abc123', name: 'Steve' }) : json(profileStatus, {});
    }
    throw new Error('niespodziewane: ' + url);
  };
}

test('minecraftLogin: pełny łańcuch i czytelne błędy', async () => {
  mock = authMock();
  const ok = await auth.minecraftLogin('MSTOKEN');
  assert.deepEqual({ uuid: ok.uuid, name: ok.name, accessToken: ok.accessToken }, { uuid: 'abc123', name: 'Steve', accessToken: 'MCTOKEN' });

  mock = authMock({ xstsStatus: 401, xstsErr: 2148916233 });
  await assert.rejects(auth.minecraftLogin('x'), /nie ma profilu Xbox/);
  mock = authMock({ xstsStatus: 401, xstsErr: 2148916238 });
  await assert.rejects(auth.minecraftLogin('x'), /rodziny/);
  mock = authMock({ mcStatus: 403 });
  await assert.rejects(auth.minecraftLogin('x'), /Invalid app registration/);
  mock = authMock({ profileStatus: 404 });
  await assert.rejects(auth.minecraftLogin('x'), /nie ma Minecrafta/);
});

test('device flow: kod, oczekiwanie, slow_down, sukces; brak Client ID', async () => {
  await assert.rejects(auth.startDeviceFlow(''), /Client ID/);
  mock = async (url, init) => {
    if (url.endsWith('/devicecode')) {
      assert.match(String(init.body), /client_id=CID/);
      return json(200, { device_code: 'DC', user_code: 'ABCD', verification_uri: 'https://www.microsoft.com/link', expires_in: 60, interval: 0 });
    }
    throw new Error('niespodziewane: ' + url);
  };
  const flow = await auth.startDeviceFlow('CID');
  assert.equal(flow.userCode, 'ABCD');

  const answers = [{ error: 'authorization_pending' }, { error: 'slow_down' }, { access_token: 'AT', refresh_token: 'RT' }];
  let polls = 0;
  mock = async () => json(answers[polls] && polls++ >= 0 ? 400 : 200, answers[polls - 1]);
  // slow_down dodaje 5 s, więc test skraca czekanie przez podmianę sleep
  const realSleep = U.sleep;
  U.sleep = async () => {};
  try {
    const t = await auth.pollDeviceFlow('CID', { ...flow, interval: 0 });
    assert.deepEqual(t, { accessToken: 'AT', refreshToken: 'RT' });
  } finally {
    U.sleep = realSleep;
  }
});

// ---------- konta i ustawienia
test('msError: błędy konfiguracji Azure dostają polską podpowiedź', () => {
  const unknownApp = auth.msError({ error_description: "AADSTS700016: Application with identifier 'x' was not found in the directory.\r\nTrace ID: 1" });
  assert.match(unknownApp, /AADSTS700016/);
  assert.match(unknownApp, /Personal Microsoft accounts/);
  assert.doesNotMatch(unknownApp, /Trace ID/, 'bez szumu z Trace ID');
  assert.match(auth.msError({ error_description: 'AADSTS7000218: The request body must contain client_assertion' }), /Allow public client flows/);
  assert.equal(auth.msError({ error: 'authorization_pending' }), 'authorization_pending', 'inne błędy bez zmian');
  assert.match(auth.msError(null), /nieznany błąd/);
});

test('AccountStore: dodawanie, wybór, usuwanie; tokeny nie wychodzą w list()', async () => {
  const dir = tmp();
  const store = new AccountStore(path.join(dir, 'accounts.json'), makeCrypt(null));
  await store.addFromLogin({ uuid: 'u1', name: 'Jeden', refreshToken: 'REFRESH-1' });
  await store.addFromLogin({ uuid: 'u2', name: 'Dwa', refreshToken: 'REFRESH-2' });
  let l = await store.list();
  assert.equal(l.selected, 'u2');
  assert.equal(l.accounts.length, 2);
  assert.ok(!JSON.stringify(l).includes('REFRESH'));
  assert.ok(!fs.readFileSync(path.join(dir, 'accounts.json'), 'utf8').includes('REFRESH-1'), 'token nie może leżeć jawnym tekstem');
  await store.select('u1');
  assert.equal((await store.list()).selected, 'u1');
  await store.remove('u1');
  l = await store.list();
  assert.equal(l.selected, 'u2');
  assert.deepEqual(l.accounts.map((a) => a.uuid), ['u2']);

  // launchAuth: odświeża token i zapisuje nowy
  mock = async (url) => {
    if (url.endsWith('/token')) return json(200, { access_token: 'MSAT', refresh_token: 'REFRESH-NEW' });
    return authMock()(url);
  };
  const la = await store.launchAuth('', 'CID');
  assert.deepEqual(la, { uuid: 'abc123', name: 'Steve', accessToken: 'MCTOKEN' });
  assert.equal((await store.list()).accounts[0].name, 'Steve');
  await assert.rejects(new AccountStore(path.join(dir, 'puste.json')).launchAuth('', 'CID'), /dodaj konto/);
});

test('Store: domyślna instalacja, zapis, ograniczenia RAM, usuwanie', async () => {
  const s = new Store(tmp());
  const list = await s.instances();
  assert.equal(list.length, 1);
  assert.equal(list[0].mcVersion, '26.2');
  assert.ok(list[0].fabric && list[0].nebula);
  assert.ok(list[0].mods.includes('sodium') && list[0].mods.includes('in-game-account-switcher'));

  const saved = await s.saveInstance({ name: 'Test', mcVersion: '1.21.4', fabric: false, nebula: true, ramMb: 100, mods: ['sodium'] });
  assert.equal(saved.ramMb, 1024, 'minimum RAM to 1024 MB');
  assert.equal(saved.nebula, false, 'Nebula wymaga Fabrica');
  assert.match(saved.id, /^test-[0-9a-f]{4}$/);
  assert.equal((await s.instances()).length, 2);
  await s.saveInstance({ ...saved, name: 'Zmieniona', ramMb: 8192 });
  const again = (await s.instances()).find((x) => x.id === saved.id);
  assert.equal(again.name, 'Zmieniona');
  assert.equal(again.ramMb, 8192);
  await s.deleteInstance(saved.id);
  assert.equal((await s.instances()).length, 1);

  const st = await s.saveSettings({ theme: 'neon', defaultRamMb: 8192 });
  assert.equal(st.theme, 'neon');
  assert.equal((await s.settings()).defaultRamMb, 8192);
});

// ---------- uruchomienie
(async () => {
  let failed = 0;
  for (const t of tests) {
    try {
      await t.fn();
      console.log(`  ok   ${t.name}`);
    } catch (e) {
      failed++;
      console.log(`  FAIL ${t.name}\n       ${e && e.stack ? e.stack.split('\n').slice(0, 4).join('\n       ') : e}`);
    }
  }
  console.log(failed ? `\n${failed} z ${tests.length} testów nie przeszło` : `\nOK: ${tests.length} testów przeszło`);
  process.exit(failed ? 1 : 0);
})();
