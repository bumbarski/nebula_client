'use strict';
// Ustawienia launchera i lista instalacji (pliki JSON w katalogu danych).

const path = require('node:path');
const crypto = require('node:crypto');
const U = require('./util');
const { DEFAULT_MODS } = require('./mods');

const DEFAULT_SETTINGS = {
  theme: 'dark', // dark | light | gray | neon
  defaultRamMb: 4096,
  clientId: '', // Client ID aplikacji Azure zatwierdzonej przez Mojang
  javaPath: '', // puste = Java pobrana od Mojang
};

function defaultInstance(ramMb = 4096) {
  return {
    id: 'nebula',
    name: 'Nebula (26.2)',
    mcVersion: '26.2',
    fabric: true,
    nebula: true,
    mods: [...DEFAULT_MODS],
    ramMb,
    jvmArgs: '',
    javaPath: '',
    loaderVersion: '',
  };
}

function slugify(s) {
  return String(s || 'instalacja')
    .toLowerCase()
    .replace(/ł/g, 'l') // "ł" nie rozkłada się przy normalizacji Unicode
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 32) || 'instalacja';
}

class Store {
  constructor(root) {
    this.root = root;
    this.settingsFile = path.join(root, 'settings.json');
    this.instancesFile = path.join(root, 'instances.json');
  }

  gameDir(id) {
    return path.join(this.root, 'instances', id);
  }

  async settings() {
    return { ...DEFAULT_SETTINGS, ...(await U.readJson(this.settingsFile, {})) };
  }

  async saveSettings(patch) {
    const next = { ...(await this.settings()), ...patch };
    next.defaultRamMb = Math.max(1024, Math.round(Number(next.defaultRamMb) || 4096));
    await U.writeJson(this.settingsFile, next);
    return next;
  }

  async instances() {
    let list = await U.readJson(this.instancesFile, null);
    if (!Array.isArray(list) || list.length === 0) {
      list = [defaultInstance((await this.settings()).defaultRamMb)];
      await U.writeJson(this.instancesFile, list);
    }
    return list;
  }

  /** Dodaje lub aktualizuje instalację. Zwraca zapisany obiekt. */
  async saveInstance(inst) {
    const list = await this.instances();
    const clean = {
      id: inst.id || `${slugify(inst.name)}-${crypto.randomBytes(2).toString('hex')}`,
      name: String(inst.name || 'Nowa instalacja').slice(0, 40),
      mcVersion: String(inst.mcVersion || '26.2'),
      fabric: Boolean(inst.fabric),
      nebula: Boolean(inst.fabric) && Boolean(inst.nebula),
      mods: Array.isArray(inst.mods) ? inst.mods.map(String) : [],
      ramMb: Math.max(1024, Math.round(Number(inst.ramMb) || 4096)),
      jvmArgs: String(inst.jvmArgs || ''),
      javaPath: String(inst.javaPath || ''),
      loaderVersion: String(inst.loaderVersion || ''),
    };
    const i = list.findIndex((x) => x.id === clean.id);
    if (i >= 0) list[i] = clean;
    else list.push(clean);
    await U.writeJson(this.instancesFile, list);
    return clean;
  }

  async deleteInstance(id) {
    const list = (await this.instances()).filter((x) => x.id !== id);
    await U.writeJson(this.instancesFile, list.length ? list : [defaultInstance()]);
  }
}

module.exports = { Store, DEFAULT_SETTINGS, defaultInstance, slugify };
