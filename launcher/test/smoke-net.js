'use strict';
// Test na PRAWDZIWYCH serwerach (Mojang, Fabric, Modrinth, GitHub). Odpalany w CI, bo wymaga internetu.
// Uruchom: node test/smoke-net.js   (opcjonalnie SMOKE_MC=26.2, SMOKE_OUT=plik-raportu)
// Sprawdza kolejno to, co launcher robi przy kliknięciu GRAJ, i na końcu próbuje uruchomić grę bez okna:
// jeśli brakuje biblioteki albo klasy startowej, zobaczymy to w raporcie.

const os = require('node:os');
const fs = require('node:fs');
const path = require('node:path');
const { spawn, spawnSync } = require('node:child_process');

const mojang = require('../src/core/mojang');
const fabric = require('../src/core/fabric');
const modrinth = require('../src/core/modrinth');
const nebula = require('../src/core/nebula');
const { KNOWN_MODS } = require('../src/core/mods');

const MC = process.env.SMOKE_MC || '26.2';
const OUT = process.env.SMOKE_OUT || 'smoke-report.txt';
const root = fs.mkdtempSync(path.join(os.tmpdir(), 'nebula-smoke-'));

const report = [];
let failed = 0;
const ctx = {};

async function step(name, fn, { fatal = true } = {}) {
  const t = Date.now();
  try {
    const info = await fn();
    report.push(`OK    ${name}${info ? ': ' + info : ''} (${((Date.now() - t) / 1000).toFixed(1)}s)`);
  } catch (e) {
    if (fatal) failed++;
    report.push(`${fatal ? 'FAIL' : 'WARN'}  ${name}: ${e.message}`);
  }
}

async function runGame(cmd, seconds) {
  return new Promise((resolve) => {
    const child = spawn(cmd.command, cmd.args, { cwd: cmd.cwd });
    let out = '';
    const grab = (d) => { out += d.toString(); };
    child.stdout.on('data', grab);
    child.stderr.on('data', grab);
    const timer = setTimeout(() => child.kill('SIGKILL'), seconds * 1000);
    child.on('exit', (code) => { clearTimeout(timer); resolve({ code, out }); });
    child.on('error', (e) => { clearTimeout(timer); resolve({ code: -1, out: String(e) }); });
  });
}

async function main() {
  await step('lista wersji Mojang zawiera ' + MC, async () => {
    const list = await mojang.listVersions();
    const v = list.find((x) => x.id === MC);
    if (!v) throw new Error('brak wersji ' + MC + ' w manifeście');
    return `${list.length} wersji, ${MC} jest typu ${v.type}`;
  });

  await step('version.json ' + MC, async () => {
    ctx.vanilla = await mojang.getVersionJson(root, MC);
    const v = ctx.vanilla;
    if (!v.arguments) throw new Error('brak pola arguments (stary format)');
    if (!v.javaVersion) throw new Error('brak javaVersion');
    return `Java ${v.javaVersion.majorVersion} (${v.javaVersion.component}), bibliotek ${v.libraries.length}, mainClass ${v.mainClass}`;
  });

  await step('Fabric Loader dla ' + MC, async () => {
    const loader = await fabric.latestLoader(MC);
    if (!loader) throw new Error('Fabric nie wspiera ' + MC);
    const prof = await fabric.profile(root, MC, loader);
    ctx.merged = mojang.mergeProfile(ctx.vanilla, prof);
    ctx.loader = loader;
    if (!/knot/i.test(ctx.merged.mainClass)) throw new Error('nietypowa mainClass: ' + ctx.merged.mainClass);
    return `loader ${loader}, mainClass ${ctx.merged.mainClass}`;
  });

  await step('lista bibliotek', async () => {
    const libs = mojang.libraryEntries(ctx.merged);
    ctx.libs = libs;
    const bad = libs.filter((l) => !l.url || !l.path);
    if (bad.length) throw new Error('biblioteki bez url/path: ' + bad.map((b) => b.name).join(', '));
    const natives = libs.filter((l) => /natives/.test(l.name)).length;
    const noHash = libs.filter((l) => !l.sha1).length;
    const legacy = (ctx.merged.libraries || []).filter((l) => l.natives).length;
    return `${libs.length} bibliotek (natywnych na classpath: ${natives}, bez sha1: ${noHash}, starego formatu natives: ${legacy})`;
  });

  await step('mody w Modrinth dla ' + MC, async () => {
    const slugs = ['fabric-api', ...KNOWN_MODS.map((m) => m.slug)];
    const r = await modrinth.resolve(slugs, MC);
    const have = new Set(r.files.map((f) => f.slug));
    const ok = slugs.filter((s) => have.has(s));
    const missing = slugs.filter((s) => !have.has(s));
    if (!have.has('fabric-api')) throw new Error('Fabric API niedostępne dla ' + MC);
    return `dostępne: ${ok.join(', ')}; brak: ${missing.join(', ') || 'nic'}; zależności dodatkowe: ${r.files.length - ok.length}`;
  });

  await step('wydanie Nebula Client na GitHubie', async () => {
    const a = await nebula.latestAsset(MC);
    if (!a) throw new Error('brak wydania z plikiem nebula-client-*.jar (jeszcze nie opublikowane?)');
    return `${a.name} (${a.size} B, tag ${a.tag})`;
  }, { fatal: false });

  await step('pobranie bibliotek i klienta (bez assetów)', async () => {
    ctx.game = await mojang.installGame(root, ctx.merged, () => {}, { assets: false });
    const missing = ctx.game.classpath.filter((p) => !fs.existsSync(p));
    if (missing.length) throw new Error('brakuje plików: ' + missing.slice(0, 3).join(', '));
    return `${ctx.game.classpath.length} plików na classpath`;
  });

  await step('Java od Mojang', async () => {
    const j = await mojang.ensureJava(root, ctx.merged);
    ctx.java = j.javaPath;
    const r = spawnSync(j.javaPath, ['-version'], { encoding: 'utf8' });
    const first = (r.stderr || r.stdout || '').split('\n')[0];
    if (r.status !== 0) throw new Error('java -version zwróciło ' + r.status + ': ' + first);
    if (!first.includes(String(j.major))) throw new Error(`oczekiwano Javy ${j.major}, jest: ${first}`);
    return first;
  });

  await step('polecenie startowe', async () => {
    const gameDir = path.join(root, 'instances', 'smoke');
    fs.mkdirSync(gameDir, { recursive: true });
    ctx.cmd = mojang.buildCommand({
      ver: ctx.merged, javaPath: ctx.java, classpath: ctx.game.classpath, gameDir,
      assetsDir: ctx.game.assetsDir, root, ramMb: 1024, jvmArgs: '',
      auth: { name: 'SmokeTest', uuid: '00000000-0000-0000-0000-000000000000', accessToken: '0' },
    });
    fs.mkdirSync(ctx.cmd.nativesDir, { recursive: true });
    const left = ctx.cmd.args.filter((a) => /\$\{\w+\}/.test(a));
    if (left.length) throw new Error('nieuzupełnione zmienne: ' + left.join(' | '));
    return `${ctx.cmd.args.length} argumentów, main ${ctx.merged.mainClass}`;
  });

  await step('start JVM z grą (bez okna, 40 s)', async () => {
    const { code, out } = await runGame(ctx.cmd, 40);
    const lines = out.split('\n').map((l) => l.trim()).filter(Boolean);
    const fatalRe = /ClassNotFoundException|NoClassDefFoundError|Could not find or load main class|UnsupportedClassVersionError|Error: LinkageError/;
    const bad = lines.find((l) => fatalRe.test(l));
    ctx.gameOut = lines;
    if (bad) throw new Error('klasy/biblioteki: ' + bad);
    const loaded = lines.find((l) => /Loading Minecraft|Fabric Loader|Loading \d+ mod/i.test(l));
    if (!loaded) throw new Error(`Fabric się nie uruchomił (kod ${code}); początek logu: ` + lines.slice(0, 5).join(' | '));
    return `Fabric wystartował (${loaded.slice(0, 120)}); proces zakończył się kodem ${code}`;
  });

  report.push('');
  if (ctx.gameOut) {
    report.push('--- ostatnie linie logu gry ---');
    report.push(...ctx.gameOut.slice(-25));
  }
  report.push('');
  report.push(failed ? `WYNIK: ${failed} krok(ów) nie przeszło` : 'WYNIK: wszystko przeszło');
  fs.writeFileSync(OUT, report.join('\n'));
  console.log(report.join('\n'));
  fs.rmSync(root, { recursive: true, force: true });
  process.exit(failed ? 1 : 0);
}

main().catch((e) => {
  report.push('BŁĄD KRYTYCZNY: ' + (e && e.stack || e));
  fs.writeFileSync(OUT, report.join('\n'));
  console.log(report.join('\n'));
  process.exit(1);
});
