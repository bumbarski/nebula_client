'use strict';
const os = require('os');

function osName(platform = process.platform) {
	return { win32: 'windows', darwin: 'osx', linux: 'linux' }[platform] || platform;
}

/** Zwraca opis srodowiska uzywany do oceny regul z plikow wersji Mojanga. */
function currentEnv(overrides = {}) {
	return {
		name: osName(),
		arch: process.arch, // x64 | arm64 | ia32
		version: os.release(),
		features: {},
		...overrides,
	};
}

function archMatches(ruleArch, envArch) {
	if (!ruleArch) return true;
	if (ruleArch === 'x86') return envArch === 'ia32';
	if (ruleArch === 'x86_64' || ruleArch === 'amd64') return envArch === 'x64';
	return ruleArch === envArch; // arm64
}

function ruleApplies(rule, env) {
	if (rule.os) {
		if (rule.os.name && rule.os.name !== env.name) return false;
		if (!archMatches(rule.os.arch, env.arch)) return false;
		if (rule.os.version) {
			try {
				if (!new RegExp(rule.os.version).test(env.version)) return false;
			} catch {
				return false;
			}
		}
	}
	if (rule.features) {
		for (const [key, wanted] of Object.entries(rule.features)) {
			if (Boolean(env.features[key]) !== Boolean(wanted)) return false;
		}
	}
	return true;
}

/** Brak regul = dozwolone. Inaczej wygrywa ostatnia pasujaca regula. */
function allowed(rules, env) {
	if (!rules || rules.length === 0) return true;
	let ok = false;
	for (const rule of rules) {
		if (ruleApplies(rule, env)) ok = rule.action === 'allow';
	}
	return ok;
}

module.exports = { osName, currentEnv, allowed, ruleApplies };
