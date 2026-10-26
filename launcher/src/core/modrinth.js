'use strict';
// Modrinth: wybór najnowszej zgodnej wersji moda razem z wymaganymi zależnościami.

const U = require('./util');

const API = 'https://api.modrinth.com/v2';

/** Wybiera najlepszą wersję z listy (release > beta > alpha). Lista z Modrinth jest od najnowszej. */
function pickVersion(list) {
  if (!Array.isArray(list) || list.length === 0) return null;
  return (
    list.find((v) => v.version_type === 'release') ||
    list.find((v) => v.version_type === 'beta') ||
    list[0]
  );
}

async function latestVersion(slugOrId, mc, loader = 'fabric') {
  const q = `game_versions=${encodeURIComponent(JSON.stringify([mc]))}&loaders=${encodeURIComponent(JSON.stringify([loader]))}`;
  let list;
  try {
    list = await U.getJson(`${API}/project/${encodeURIComponent(slugOrId)}/version?${q}`);
  } catch (e) {
    if (/HTTP 404/.test(e.message)) return null;
    throw e;
  }
  return pickVersion(list);
}

function primaryFile(version) {
  const files = version.files || [];
  return files.find((f) => f.primary) || files[0] || null;
}

/**
 * Rozwiązuje listę modów (slugi) do plików do pobrania, razem z wymaganymi zależnościami.
 * Zwraca {files: [{projectId, slug, name, filename, url, sha512, size}], warnings: [..]}.
 */
async function resolve(slugs, mc, loader = 'fabric') {
  const files = new Map(); // projectId -> plik
  const warnings = [];

  async function add(idOrSlug, label, depth) {
    const v = await latestVersion(idOrSlug, mc, loader);
    if (!v) {
      warnings.push(`${label || idOrSlug}: brak wersji na Minecrafta ${mc} (${loader}), pominięto`);
      return;
    }
    if (files.has(v.project_id)) return;
    const f = primaryFile(v);
    if (!f) {
      warnings.push(`${label || idOrSlug}: wersja bez pliku, pominięto`);
      return;
    }
    files.set(v.project_id, {
      projectId: v.project_id,
      slug: label || idOrSlug,
      name: v.name,
      filename: f.filename,
      url: f.url,
      sha512: f.hashes && f.hashes.sha512,
      sha1: f.hashes && f.hashes.sha1,
      size: f.size,
    });
    if (depth < 3) {
      for (const d of v.dependencies || []) {
        if (d.dependency_type === 'required' && d.project_id) {
          await add(d.project_id, null, depth + 1);
        }
      }
    }
  }

  for (const slug of slugs) await add(slug, slug, 0);
  return { files: [...files.values()], warnings };
}

module.exports = { pickVersion, latestVersion, primaryFile, resolve };
