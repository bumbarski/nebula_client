'use strict';
const fs = require('fs');
const fsp = require('fs/promises');
const path = require('path');
const crypto = require('crypto');
const { Readable } = require('stream');
const { pipeline } = require('stream/promises');

const UA = 'bumbarski/nebula_client launcher/0.1.0';

async function sleep(ms) {
	return new Promise((r) => setTimeout(r, ms));
}

/** Powtarza operacje kilka razy (siec bywa kapryśna). */
async function retry(fn, tries = 3) {
	let last;
	for (let i = 0; i < tries; i++) {
		try {
			return await fn(i);
		} catch (e) {
			last = e;
			await sleep(400 * (i + 1));
		}
	}
	throw last;
}

async function getJson(url) {
	return retry(async () => {
		const res = await fetch(url, { headers: { 'User-Agent': UA, Accept: 'application/json' } });
		if (!res.ok) throw new Error(`HTTP ${res.status} dla ${url}`);
		return res.json();
	});
}

async function sha1File(file) {
	const hash = crypto.createHash('sha1');
	await pipeline(fs.createReadStream(file), hash);
	return hash.digest('hex');
}

async function exists(file) {
	try {
		await fsp.access(file);
		return true;
	} catch {
		return false;
	}
}

/**
 * Czy plik jest na miejscu i poprawny.
 * checkHash=false sprawdza tylko rozmiar (szybkie, dla tysiecy plikow assetow).
 */
async function fileOk(file, { sha1, size, checkHash = true } = {}) {
	let st;
	try {
		st = await fsp.stat(file);
	} catch {
		return false;
	}
	if (!st.isFile()) return false;
	if (size != null && st.size !== size) return false;
	if (sha1 && checkHash) return (await sha1File(file)) === sha1;
	return true;
}

/** Pobiera url do dest (przez plik tymczasowy), weryfikuje sha1 jesli podany. */
async function download(url, dest, { sha1, size } = {}) {
	await fsp.mkdir(path.dirname(dest), { recursive: true });
	await retry(async () => {
		const tmp = `${dest}.part`;
		const res = await fetch(url, { headers: { 'User-Agent': UA } });
		if (!res.ok || !res.body) throw new Error(`HTTP ${res.status} dla ${url}`);
		await pipeline(Readable.fromWeb(res.body), fs.createWriteStream(tmp));
		if (sha1 && (await sha1File(tmp)) !== sha1) {
			await fsp.rm(tmp, { force: true });
			throw new Error(`Zla suma kontrolna: ${url}`);
		}
		if (size != null && (await fsp.stat(tmp)).size !== size) {
			await fsp.rm(tmp, { force: true });
			throw new Error(`Zly rozmiar pliku: ${url}`);
		}
		await fsp.rename(tmp, dest);
	});
}

/** Uruchamia worker dla kazdego elementu z ograniczeniem rownoleglosci. */
async function pool(items, limit, worker) {
	let next = 0;
	let done = 0;
	const total = items.length;
	async function run() {
		while (true) {
			const i = next++;
			if (i >= total) return;
			await worker(items[i], i);
			done++;
		}
	}
	const runners = [];
	for (let i = 0; i < Math.min(limit, total); i++) runners.push(run());
	await Promise.all(runners);
	return done;
}

async function writeJsonAtomic(file, data) {
	await fsp.mkdir(path.dirname(file), { recursive: true });
	const tmp = `${file}.tmp`;
	await fsp.writeFile(tmp, JSON.stringify(data, null, 2), 'utf8');
	await fsp.rename(tmp, file);
}

async function readJson(file, fallback = null) {
	try {
		return JSON.parse(await fsp.readFile(file, 'utf8'));
	} catch {
		return fallback;
	}
}

module.exports = { UA, getJson, download, fileOk, exists, sha1File, pool, retry, writeJsonAtomic, readJson };
