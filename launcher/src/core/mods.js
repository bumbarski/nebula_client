'use strict';
// Instalacja paczki modów do katalogu instalacji. Launcher zarządza tylko plikami, które sam dodał,
// więc Twoje własne mody w folderze mods nie są ruszane.

const fsp = require('node:fs/promises');
const path = require('node:path');
const U = require('./util');
const modrinth = require('./modrinth');
const nebula = require('./nebula');

/** Mody dostępne do wyboru w instalacji (slug z Modrinth). */
const KNOWN_MODS = [
  { slug: 'sodium', name: 'Sodium', desc: 'Dużo wyższe FPS (renderowanie)', recommended: true },
  { slug: 'lithium', name: 'Lithium', desc: 'Optymalizacja logiki gry i serwera', recommended: true },
  { slug: 'ferrite-core', name: 'FerriteCore', desc: 'Mniejsze zużycie pamięci RAM', recommended: true },
  { slug: 'in-game-account-switcher', name: 'In-Game Account Switcher', desc: 'Zmiana konta w grze', recommended: true },
  { slug: 'immediatelyfast', name: 'ImmediatelyFast', desc: 'Szybsze rysowanie interfejsu', recommended: false },
  { slug: 'entityculling', name: 'Entity Culling', desc: 'Nie rysuje niewidocznych jednostek', recommended: false },
  { slug: 'modernfix', name: 'ModernFix', desc: 'Szybszy start i mniej pamięci', recommended: false },
];

const DEFAULT_MODS = KNOWN_MODS.filter((m) => m.recommended).map((m) => m.slug);

async function installMods(gameDir, inst, onProgress = () => {}) {
  const modsDir = path.join(gameDir, 'mods');
  const manifestFile = path.join(modsDir, '.nebula-managed.json');
  await U.ensureDir(modsDir);

  const warnings = [];
  const wanted = [];

  if (inst.fabric) {
    const slugs = [...new Set(['fabric-api', ...(inst.mods || [])])];
    const r = await modrinth.resolve(slugs, inst.mcVersion);
    warnings.push(...r.warnings);
    wanted.push(...r.files);
    if (!r.files.some((f) => f.slug === 'fabric-api')) {
      warnings.push('Fabric API jest niedostępne dla tej wersji, część modów może się nie uruchomić');
    }
  }

  if (inst.nebula) {
    try {
      const a = await nebula.latestAsset();
      if (a) wanted.push({ filename: a.name, url: a.url, size: a.size, slug: 'nebula-client' });
      else warnings.push('Nebula Client: nie ma jeszcze wydania na GitHubie, pominięto');
    } catch (e) {
      warnings.push(`Nebula Client: ${e.message}`);
    }
  }

  // usuń poprzednie pliki zarządzane przez launcher, których już nie chcemy
  const prev = (await U.readJson(manifestFile, { files: [] })).files || [];
  const keep = new Set(wanted.map((w) => w.filename));
  for (const f of prev) {
    if (!keep.has(f)) await fsp.rm(path.join(modsDir, f), { force: true });
  }

  let done = 0;
  await U.pool(wanted, 4, async (w) => {
    await U.download(w.url, path.join(modsDir, w.filename), { sha512: w.sha512, sha1: w.sha1, size: w.size });
    done++;
    onProgress({ stage: 'Mody', done, total: wanted.length });
  });

  await U.writeJson(manifestFile, { files: wanted.map((w) => w.filename) });
  return { installed: wanted.map((w) => w.filename), warnings };
}

module.exports = { KNOWN_MODS, DEFAULT_MODS, installMods };
