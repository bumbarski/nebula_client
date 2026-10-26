'use strict';
// Nebula Client: pobieranie najnowszego jara z wydań (Releases) repozytorium GitHub.

const U = require('./util');

const REPO = 'bumbarski/nebula_client';

/** Wybiera z listy plików wydania właściwego jara (bez -sources). */
function pickAsset(assets) {
  return (
    (assets || []).find((a) => /^nebula-client-.*\.jar$/.test(a.name) && !/-sources\.jar$/.test(a.name)) || null
  );
}

async function latestAsset() {
  const rel = await U.getJson(`https://api.github.com/repos/${REPO}/releases/latest`, {
    Accept: 'application/vnd.github+json',
  });
  const asset = pickAsset(rel.assets);
  if (!asset) return null;
  return { name: asset.name, url: asset.browser_download_url, size: asset.size, tag: rel.tag_name };
}

module.exports = { REPO, pickAsset, latestAsset };
