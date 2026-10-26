'use strict';
// Most między interfejsem a procesem głównym: tylko wymienione kanały.

const { contextBridge, ipcRenderer } = require('electron');

const CALL = new Set([
  'state:get', 'settings:save', 'instance:save', 'instance:delete', 'versions:list', 'instance:openFolder',
  'open:external', 'auth:login', 'auth:cancel', 'account:select', 'account:remove', 'game:play', 'game:stop',
]);
const ON = new Set(['progress', 'log', 'game:status', 'auth:done']);

contextBridge.exposeInMainWorld('api', {
  call: (channel, ...args) => {
    if (!CALL.has(channel)) return Promise.resolve({ ok: false, error: 'Nieznany kanał: ' + channel });
    return ipcRenderer.invoke(channel, ...args);
  },
  on: (channel, fn) => {
    if (!ON.has(channel)) return () => {};
    const handler = (_event, payload) => fn(payload);
    ipcRenderer.on(channel, handler);
    return () => ipcRenderer.removeListener(channel, handler);
  },
});
