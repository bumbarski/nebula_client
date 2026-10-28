'use strict';
// Nebula Client: pobieranie najnowszego jara z wydań (Releases) repozytorium GitHub.

const U = require('./util');

const REPO = 'bumbarski/nebula_client';

/** Wybiera z listy plików wydania jara dla danej wersji gry (nebula-client-<wersja>-mc<MC>.jar), bez -sources. */
function pickAsset(assets, mcVersion) {
  const jars = (assets || []).filter((a) => /^nebula-client-.*\.jar$/.test(a.name) && !/-sources\.jar$/.test(a.name));
  if (mcVersion) {
    return jars.find((a) => a.name.endsWith(`-mc${mcVersion}.jar`)) || null;
  }
  return jars[0] || null;
}

async function latestAsset(mcVersion) {
  const rel = await U.getJson(`https://api.github.com/repos/${REPO}/releases/latest`, {
    Accept: 'application/vnd.github+json',
  });
  const asset = pickAsset(rel.assets, mcVersion);
  if (!asset) return null;
  return { name: asset.name, url: asset.browser_download_url, size: asset.size, tag: rel.tag_name };
}

module.exports = { REPO, pickAsset, latestAsset };
