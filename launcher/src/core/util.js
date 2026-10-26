'use strict';
// Narzędzia sieciowe i plikowe. Tylko wbudowane moduły Node (fetch, fs, crypto, stream).

const fs = require('node:fs');
const fsp = require('node:fs/promises');
const path = require('node:path');
const crypto = require('node:crypto');
const { pipeline } = require('node:stream/promises');
const { Readable } = require('node:stream');

const UA = 'NebulaLauncher/0.1 (+https://github.com/bumbarski/nebula_client)';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** fetch z ponawianiem przy błędach sieci i odpowiedziach 5xx. */
async function request(url, init = {}, tries = 3) {
  let lastErr;
  for (let i = 1; i <= tries; i++) {
    try {
      const res = await fetch(url, {
        ...init,
        headers: { 'User-Agent': UA, ...(init.headers || {}) },
      });
      if (res.status >= 500 && i < tries) {
        lastErr = new Error(`HTTP ${res.status}`);
        await sleep(500 * i);
        continue;
      }
      return res;
    } catch (e) {
      lastErr = e;
      if (i < tries) await sleep(500 * i);
    }
  }
  throw new Error(`Nie można połączyć z ${url}: ${lastErr && lastErr.message}`);
}

async function getJson(url, headers = {}) {
  const res = await request(url, { headers: { Accept: 'application/json', ...headers } });
  if (!res.ok) throw new Error(`HTTP ${res.status} dla ${url}`);
  return res.json();
}

async function parseBody(res) {
  const text = await res.text();
  try {
    return text ? JSON.parse(text) : null;
  } catch {
    return { raw: text };
  }
}

/** POST JSON. Nie rzuca przy błędach HTTP: zwraca {status, data}, bo logowanie musi czytać treść błędu. */
async function postJson(url, body, headers = {}) {
  const res = await request(
    url,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...headers },
      body: JSON.stringify(body),
    },
    1
  );
  return { status: res.status, data: await parseBody(res) };
}

async function postForm(url, fields) {
  const res = await request(
    url,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/json' },
      body: new URLSearchParams(fields).toString(),
    },
    1
  );
  return { status: res.status, data: await parseBody(res) };
}

async function ensureDir(dir) {
  await fsp.mkdir(dir, { recursive: true });
}

async function readJson(file, fallback = null) {
  try {
    return JSON.parse(await fsp.readFile(file, 'utf8'));
  } catch {
    return fallback;
  }
}

async function writeJson(file, data) {
  await ensureDir(path.dirname(file));
  const tmp = file + '.tmp';
  await fsp.writeFile(tmp, JSON.stringify(data, null, 2), 'utf8');
  await fsp.rename(tmp, file);
}

function hashFile(file, algo) {
  return new Promise((resolve, reject) => {
    const h = crypto.createHash(algo);
    fs.createReadStream(file)
      .on('error', reject)
      .on('data', (c) => h.update(c))
      .on('end', () => resolve(h.digest('hex')));
  });
}

/** Czy plik istnieje i zgadza się z podanym rozmiarem / sumą kontrolną. */
async function fileMatches(file, { sha1, sha512, size } = {}) {
  let st;
  try {
    st = await fsp.stat(file);
  } catch {
    return false;
  }
  if (size != null && st.size !== size) return false;
  if (sha1) return (await hashFile(file, 'sha1')) === String(sha1).toLowerCase();
  if (sha512) return (await hashFile(file, 'sha512')) === String(sha512).toLowerCase();
  return true;
}

/** Pobiera plik (z weryfikacją). Zwraca true, jeśli faktycznie pobrano. */
async function download(url, dest, expect = {}) {
  if (await fileMatches(dest, expect)) return false;
  await ensureDir(path.dirname(dest));
  const tmp = dest + '.part';
  const res = await request(url);
  if (!res.ok || !res.body) throw new Error(`HTTP ${res.status} dla ${url}`);
  await pipeline(Readable.fromWeb(res.body), fs.createWriteStream(tmp));
  if (!(await fileMatches(tmp, expect))) {
    await fsp.rm(tmp, { force: true });
    throw new Error(`Niezgodna suma kontrolna lub rozmiar: ${url}`);
  }
  await fsp.rename(tmp, dest);
  return true;
}

/** Uruchamia fn dla elementów z limitem równoległości. */
async function pool(items, limit, fn) {
  const results = new Array(items.length);
  let next = 0;
  const worker = async () => {
    for (;;) {
      const i = next++;
      if (i >= items.length) return;
      results[i] = await fn(items[i], i);
    }
  };
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker));
  return results;
}

function platform() {
  const p = process.platform;
  const a = process.arch;
  const os = p === 'win32' ? 'windows' : p === 'darwin' ? 'osx' : 'linux';
  let runtimeKey = 'linux';
  if (p === 'win32') runtimeKey = a === 'arm64' ? 'windows-arm64' : 'windows-x64';
  else if (p === 'darwin') runtimeKey = a === 'arm64' ? 'mac-os-arm64' : 'mac-os';
  return { os, arch: a, runtimeKey };
}

/** Proste dzielenie argumentów JVM z uwzględnieniem cudzysłowów. */
function parseArgs(str) {
  if (!str) return [];
  const out = [];
  const re = /"([^"]*)"|'([^']*)'|(\S+)/g;
  let m;
  while ((m = re.exec(str))) out.push(m[1] ?? m[2] ?? m[3]);
  return out;
}

module.exports = {
  UA,
  sleep,
  request,
  getJson,
  postJson,
  postForm,
  ensureDir,
  readJson,
  writeJson,
  hashFile,
  fileMatches,
  download,
  pool,
  platform,
  parseArgs,
};
