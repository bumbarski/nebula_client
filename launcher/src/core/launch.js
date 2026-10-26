'use strict';
// Przygotowanie instalacji (wersja, Fabric, biblioteki, Java, mody) i start procesu gry.

const fsp = require('node:fs/promises');
const path = require('node:path');
const { spawn } = require('node:child_process');
const U = require('./util');
const mojang = require('./mojang');
const fabric = require('./fabric');
const mods = require('./mods');

/** Zwraca złożony JSON wersji (z Fabricem, jeśli włączony). */
async function resolveVersion(root, inst) {
  const base = await mojang.getVersionJson(root, inst.mcVersion);
  if (!inst.fabric) return { ...base, _clientId: base.id, _loaderVersion: '' };

  // zawsze najnowszy stabilny loader; zapamiętana wersja służy tylko jako zapas bez internetu
  let loaderVersion;
  try {
    loaderVersion = await fabric.latestLoader(inst.mcVersion);
  } catch (e) {
    if (!inst.loaderVersion) throw e;
    loaderVersion = inst.loaderVersion;
  }
  if (!loaderVersion) throw new Error(`Fabric nie wspiera jeszcze Minecrafta ${inst.mcVersion}`);
  const prof = await fabric.profile(root, inst.mcVersion, loaderVersion);
  const merged = mojang.mergeProfile(base, prof);
  merged._loaderVersion = loaderVersion;
  return merged;
}

/**
 * Pobiera wszystko, co potrzebne do startu. emit({stage, done, total}) raportuje postęp.
 * Przy braku internetu pliki już pobrane są używane dalej.
 */
async function prepare(root, inst, settings, gameDir, emit = () => {}) {
  emit({ stage: 'Sprawdzam wersję' });
  const ver = await resolveVersion(root, inst);

  const game = await mojang.installGame(root, ver, emit);

  let javaPath = inst.javaPath || settings.javaPath;
  if (!javaPath) {
    emit({ stage: 'Sprawdzam Javę' });
    javaPath = (await mojang.ensureJava(root, ver, emit)).javaPath;
  }

  await U.ensureDir(gameDir);
  const warnings = [];
  try {
    const r = await mods.installMods(gameDir, inst, emit);
    warnings.push(...r.warnings);
  } catch (e) {
    warnings.push(`Mody nie zostały zaktualizowane: ${e.message}`);
  }

  return { ver, game, javaPath, gameDir, warnings, loaderVersion: ver._loaderVersion };
}

/** Uruchamia grę. Zwraca proces potomny. Tokeny nigdy nie trafiają do logów. */
async function start(root, inst, settings, auth, prepared, onLog = () => {}) {
  const cmd = mojang.buildCommand({
    ver: prepared.ver,
    javaPath: prepared.javaPath,
    classpath: prepared.game.classpath,
    gameDir: prepared.gameDir,
    assetsDir: prepared.game.assetsDir,
    root,
    auth,
    ramMb: inst.ramMb || settings.defaultRamMb,
    jvmArgs: inst.jvmArgs,
  });
  await U.ensureDir(cmd.nativesDir);
  await fsp.mkdir(cmd.cwd, { recursive: true });

  const child = spawn(cmd.command, cmd.args, { cwd: cmd.cwd, windowsHide: true });
  child.stdout.on('data', (d) => onLog(d.toString()));
  child.stderr.on('data', (d) => onLog(d.toString()));
  return child;
}

module.exports = { resolveVersion, prepare, start };
