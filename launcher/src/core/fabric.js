'use strict';
// Fabric Loader: wybór wersji loadera i profil startowy z meta.fabricmc.net.

const path = require('node:path');
const U = require('./util');

const META = 'https://meta.fabricmc.net/v2';

/** Najnowszy stabilny loader dla danej wersji Minecrafta (lub null, gdy Fabric jej nie wspiera). */
async function latestLoader(mc) {
  const list = await U.getJson(`${META}/versions/loader/${encodeURIComponent(mc)}`);
  if (!Array.isArray(list) || list.length === 0) return null;
  const stable = list.find((e) => e.loader && e.loader.stable);
  return (stable || list[0]).loader.version;
}

/** Profil startowy Fabrica (JSON z inheritsFrom). Zapisuje kopię na dysku. */
async function profile(root, mc, loaderVersion) {
  const id = `fabric-loader-${loaderVersion}-${mc}`;
  const file = path.join(root, 'versions', id, id + '.json');
  try {
    const json = await U.getJson(
      `${META}/versions/loader/${encodeURIComponent(mc)}/${encodeURIComponent(loaderVersion)}/profile/json`
    );
    await U.writeJson(file, json);
    return json;
  } catch (e) {
    const cached = await U.readJson(file, null);
    if (cached) return cached;
    throw e;
  }
}

module.exports = { latestLoader, profile };
