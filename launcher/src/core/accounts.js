'use strict';
// Magazyn kont: wiele kont, wybór aktywnego, tokeny odświeżania zapisane w postaci zaszyfrowanej.

const U = require('./util');
const auth = require('./auth');

/**
 * Szyfrowanie tokenów. W Electronie używamy safeStorage (klucz systemowy, np. DPAPI w Windows).
 * Gdy niedostępne, tokeny są tylko zakodowane (nie szyfrowane), co launcher zgłasza w interfejsie.
 */
function makeCrypt(safeStorage) {
  const secure = Boolean(safeStorage && safeStorage.isEncryptionAvailable && safeStorage.isEncryptionAvailable());
  return {
    secure,
    enc(text) {
      if (secure) return 'enc:' + safeStorage.encryptString(text).toString('base64');
      return 'plain:' + Buffer.from(text, 'utf8').toString('base64');
    },
    dec(text) {
      if (text.startsWith('enc:')) {
        if (!secure) throw new Error('Nie można odszyfrować zapisanych kont na tym komputerze.');
        return safeStorage.decryptString(Buffer.from(text.slice(4), 'base64'));
      }
      return Buffer.from(text.replace(/^plain:/, ''), 'base64').toString('utf8');
    },
  };
}

class AccountStore {
  constructor(file, crypt) {
    this.file = file;
    this.crypt = crypt || makeCrypt(null);
  }

  async _load() {
    const d = await U.readJson(this.file, null);
    return d && Array.isArray(d.accounts) ? d : { accounts: [], selected: '' };
  }

  /** Lista kont bez tokenów (bezpieczna do wysłania do interfejsu). */
  async list() {
    const d = await this._load();
    return { selected: d.selected, accounts: d.accounts.map((a) => ({ uuid: a.uuid, name: a.name })) };
  }

  async addFromLogin({ uuid, name, refreshToken }) {
    const d = await this._load();
    const entry = { uuid, name, refresh: this.crypt.enc(refreshToken) };
    const i = d.accounts.findIndex((a) => a.uuid === uuid);
    if (i >= 0) d.accounts[i] = entry;
    else d.accounts.push(entry);
    d.selected = uuid;
    await U.writeJson(this.file, d);
  }

  async select(uuid) {
    const d = await this._load();
    if (d.accounts.some((a) => a.uuid === uuid)) {
      d.selected = uuid;
      await U.writeJson(this.file, d);
    }
  }

  async remove(uuid) {
    const d = await this._load();
    d.accounts = d.accounts.filter((a) => a.uuid !== uuid);
    if (d.selected === uuid) d.selected = d.accounts.length ? d.accounts[0].uuid : '';
    await U.writeJson(this.file, d);
  }

  /** Świeże dane logowania do startu gry dla wskazanego (lub wybranego) konta. */
  async launchAuth(uuid, clientId) {
    const d = await this._load();
    const id = uuid || d.selected;
    const acc = d.accounts.find((a) => a.uuid === id);
    if (!acc) throw new auth.AuthError('Najpierw dodaj konto Microsoft (zakładka Konta).');

    const ms = await auth.refresh(clientId, this.crypt.dec(acc.refresh));
    const mc = await auth.minecraftLogin(ms.accessToken);

    acc.refresh = this.crypt.enc(ms.refreshToken);
    acc.name = mc.name;
    await U.writeJson(this.file, d);
    return { uuid: mc.uuid, name: mc.name, accessToken: mc.accessToken };
  }
}

module.exports = { AccountStore, makeCrypt };
