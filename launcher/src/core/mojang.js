'use strict';
// Wersje Minecrafta, biblioteki, assety, Java od Mojang i składanie polecenia startowego.

const fsp = require('node:fs/promises');
const path = require('node:path');
const U = require('./util');

const MANIFEST_URL = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';
const RUNTIME_ALL_URL =
  'https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json';

let manifestCache = null;
async function manifest() {
  if (!manifestCache) manifestCache = await U.getJson(MANIFEST_URL);
  return manifestCache;
}

async function listVersions() {
  const m = await manifest();
  return m.versions.map((v) => ({ id: v.id, type: v.type, releaseTime: v.releaseTime }));
}

/** JSON wersji z Mojang. Gdy brak sieci, używa kopii z dysku. */
async function getVersionJson(root, id) {
  const file = path.join(root, 'versions', id, id + '.json');
  try {
    const m = await manifest();
    const entry = m.versions.find((v) => v.id === id);
    if (!entry) throw new Error(`Mojang nie ma wersji "${id}"`);
    const json = await U.getJson(entry.url);
    await U.writeJson(file, json);
    return json;
  } catch (e) {
    const cached = await U.readJson(file, null);
    if (cached) return cached;
    throw e;
  }
}

/** Łączy profil Fabrica (inheritsFrom) z wersją bazową. Biblioteki z profilu mają pierwszeństwo. */
function mergeProfile(parent, child) {
  const out = { ...parent, ...child };
  out.libraries = [...(child.libraries || []), ...(parent.libraries || [])];
  out.arguments = {
    game: [...((parent.arguments && parent.arguments.game) || []), ...((child.arguments && child.arguments.game) || [])],
    jvm: [...((parent.arguments && parent.arguments.jvm) || []), ...((child.arguments && child.arguments.jvm) || [])],
  };
  out.mainClass = child.mainClass || parent.mainClass;
  out.assetIndex = parent.assetIndex;
  out.assets = parent.assets;
  out.downloads = parent.downloads;
  out.javaVersion = parent.javaVersion;
  out.type = parent.type;
  out._clientId = parent.id;
  return out;
}

function archMatches(want) {
  if (want === 'x86') return process.arch === 'ia32';
  return want === process.arch;
}

/** Reguły Mojang: domyślnie zabronione, wygrywa ostatnia pasująca. */
function ruleAllows(rules, features = {}) {
  if (!rules || rules.length === 0) return true;
  const { os } = U.platform();
  let allowed = false;
  for (const r of rules) {
    let match = true;
    if (r.os) {
      if (r.os.name && r.os.name !== os) match = false;
      if (r.os.arch && !archMatches(r.os.arch)) match = false;
    }
    if (r.features) {
      for (const [k, v] of Object.entries(r.features)) {
        if (Boolean(features[k]) !== v) match = false;
      }
    }
    if (match) allowed = r.action === 'allow';
  }
  return allowed;
}

/** Lista bibliotek do pobrania i dodania do classpath dla tego systemu. */
function libraryEntries(ver) {
  const out = [];
  const seen = new Set();
  for (const lib of ver.libraries || []) {
    if (!ruleAllows(lib.rules)) continue;
    if (lib.natives) continue; // stary format natywnych bibliotek (przed 1.19) nie jest wspierany
    const [g, a, v, c] = lib.name.split(':');
    const key = `${g}:${a}:${c || ''}`;
    if (seen.has(key)) continue;
    seen.add(key);

    let art = lib.downloads && lib.downloads.artifact;
    if (!art) {
      const rel = `${g.replace(/\./g, '/')}/${a}/${v}/${a}-${v}${c ? '-' + c : ''}.jar`;
      const base = (lib.url || 'https://libraries.minecraft.net/').replace(/\/?$/, '/');
      art = { path: rel, url: base + rel, sha1: lib.sha1, size: lib.size };
    }
    out.push({ name: lib.name, path: art.path, url: art.url, sha1: art.sha1, size: art.size });
  }
  return out;
}

/** Pobiera bibliotekę, plik klienta i (opcjonalnie) assety. */
async function installGame(root, ver, onProgress = () => {}, opts = {}) {
  const libs = libraryEntries(ver);
  const files = libs.map((l) => ({
    url: l.url,
    dest: path.join(root, 'libraries', l.path),
    sha1: l.sha1,
    size: l.size,
  }));

  const clientDl = ver.downloads && ver.downloads.client;
  if (!clientDl) throw new Error('Ta wersja nie ma pliku klienta do pobrania');
  const clientJar = path.join(root, 'versions', ver._clientId || ver.id, (ver._clientId || ver.id) + '.jar');
  files.push({ url: clientDl.url, dest: clientJar, sha1: clientDl.sha1, size: clientDl.size });

  const ai = ver.assetIndex;
  const indexFile = path.join(root, 'assets', 'indexes', ai.id + '.json');
  await U.download(ai.url, indexFile, { sha1: ai.sha1, size: ai.size });
  if (opts.assets !== false) {
    const index = await U.readJson(indexFile, { objects: {} });
    for (const obj of Object.values(index.objects)) {
      const h = obj.hash;
      files.push({
        url: `https://resources.download.minecraft.net/${h.slice(0, 2)}/${h}`,
        dest: path.join(root, 'assets', 'objects', h.slice(0, 2), h),
        sha1: h,
        size: obj.size,
      });
    }
  }

  let done = 0;
  await U.pool(files, 16, async (f) => {
    await U.download(f.url, f.dest, { sha1: f.sha1, size: f.size });
    done++;
    if (done % 25 === 0 || done === files.length) onProgress({ stage: 'Pliki gry', done, total: files.length });
  });

  return {
    classpath: [...libs.map((l) => path.join(root, 'libraries', l.path)), clientJar],
    clientJar,
    assetsDir: path.join(root, 'assets'),
  };
}

/** Java od Mojang (komponent z version.json, np. Java 25 dla 26.x). */
async function ensureJava(root, ver, onProgress = () => {}) {
  const jv = ver.javaVersion || { component: 'jre-legacy', majorVersion: 8 };
  const plat = U.platform();
  const all = await U.getJson(RUNTIME_ALL_URL);
  const entry = all && all[plat.runtimeKey] && all[plat.runtimeKey][jv.component] && all[plat.runtimeKey][jv.component][0];
  if (!entry) {
    throw new Error(
      `Mojang nie udostępnia Javy ${jv.majorVersion} (${jv.component}) dla ${plat.runtimeKey}. Wskaż własną Javę w ustawieniach instalacji.`
    );
  }

  const base = path.join(root, 'runtime', jv.component, plat.runtimeKey);
  const marker = path.join(base, `.complete-${entry.version.name}`);
  const javaRel =
    plat.os === 'windows' ? 'bin/java.exe' : plat.os === 'osx' ? 'jre.bundle/Contents/Home/bin/java' : 'bin/java';
  const javaPath = path.join(base, javaRel);

  try {
    await fsp.access(marker);
    await fsp.access(javaPath);
    return { javaPath, major: jv.majorVersion };
  } catch {
    /* trzeba pobrać */
  }

  const mani = await U.getJson(entry.manifest.url);
  const files = Object.entries(mani.files).filter(([, f]) => f.type === 'file');
  let done = 0;
  await U.pool(files, 12, async ([rel, f]) => {
    const raw = f.downloads && f.downloads.raw;
    if (!raw) return;
    const dest = path.join(base, rel);
    await U.download(raw.url, dest, { sha1: raw.sha1, size: raw.size });
    if (f.executable && plat.os !== 'windows') await fsp.chmod(dest, 0o755);
    done++;
    if (done % 25 === 0 || done === files.length) onProgress({ stage: `Java ${jv.majorVersion}`, done, total: files.length });
  });
  await fsp.writeFile(marker, new Date().toISOString());
  return { javaPath, major: jv.majorVersion };
}

/** Składa polecenie startowe (java + argumenty). */
function buildCommand({ ver, javaPath, classpath, gameDir, assetsDir, root, auth, ramMb, jvmArgs, launcherVersion }) {
  if (!ver.arguments) {
    throw new Error('Ta wersja Minecrafta jest zbyt stara dla tego launchera (obsługiwane są wersje od 1.13).');
  }
  const sep = process.platform === 'win32' ? ';' : ':';
  const nativesDir = path.join(root, 'versions', ver._clientId || ver.id, 'natives');
  const vars = {
    auth_player_name: auth.name,
    version_name: ver.id,
    game_directory: gameDir,
    assets_root: assetsDir,
    assets_index_name: ver.assetIndex.id,
    auth_uuid: auth.uuid,
    auth_access_token: auth.accessToken,
    clientid: '',
    auth_xuid: '',
    user_type: 'msa',
    version_type: ver.type || 'release',
    natives_directory: nativesDir,
    launcher_name: 'NebulaLauncher',
    launcher_version: launcherVersion || '0.1.0',
    classpath: classpath.join(sep),
    classpath_separator: sep,
    library_directory: path.join(root, 'libraries'),
    user_properties: '{}',
  };
  const sub = (s) => s.replace(/\$\{(\w+)\}/g, (m, k) => (k in vars ? vars[k] : m));
  const flat = (list) =>
    (list || []).flatMap((a) => (typeof a === 'string' ? [a] : ruleAllows(a.rules) ? [].concat(a.value) : []));

  const jvm = flat(ver.arguments.jvm).map(sub);
  const game = flat(ver.arguments.game).map(sub);
  const mem = Math.max(512, Math.round(ramMb || 2048));
  const args = [
    `-Xmx${mem}M`,
    `-Xms${Math.min(mem, 1024)}M`,
    ...U.parseArgs(jvmArgs),
    ...jvm,
    ver.mainClass,
    ...game,
  ];
  return { command: javaPath, args, cwd: gameDir, nativesDir };
}

module.exports = {
  manifest,
  listVersions,
  getVersionJson,
  mergeProfile,
  ruleAllows,
  libraryEntries,
  installGame,
  ensureJava,
  buildCommand,
};
