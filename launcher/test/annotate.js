'use strict';
// Wypisuje plik jako adnotację GitHub Actions (czytelną przez API nawet gdy logi są niedostępne).
// Użycie: node test/annotate.js <plik> <tytuł> [error|notice|warning] [maks. znaków od końca]
const fs = require('node:fs');
const [file, title, level = 'error', max = '7000'] = process.argv.slice(2);
let s;
try { s = fs.readFileSync(file, 'utf8'); } catch { s = '(brak pliku ' + file + ')'; }
if (s.length > Number(max)) s = '…' + s.slice(-Number(max));
s = s.replace(/%/g, '%25').replace(/\r/g, '%0D').replace(/\n/g, '%0A');
console.log(`::${level} title=${title}::${s}`);
