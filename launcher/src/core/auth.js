'use strict';
// Logowanie kontem Microsoft do Minecrafta Java (kod urządzenia → Xbox Live → XSTS → Minecraft).
// Wymaga Client ID aplikacji Azure zatwierdzonej przez Mojang (patrz README).

const U = require('./util');

const SCOPE = 'XboxLive.signin offline_access';
const MS = 'https://login.microsoftonline.com/consumers/oauth2/v2.0';

class AuthError extends Error {}

function msError(data) {
  const d = data || {};
  return d.error_description || d.error || 'nieznany błąd logowania Microsoft';
}

function requireClientId(clientId) {
  if (!clientId) {
    throw new AuthError(
      'Brak Client ID aplikacji. Wpisz go w Ustawieniach (instrukcja jest w README launchera).'
    );
  }
}

/** Krok 1: prosi Microsoft o kod do wpisania na stronie logowania. */
async function startDeviceFlow(clientId) {
  requireClientId(clientId);
  const { status, data } = await U.postForm(`${MS}/devicecode`, { client_id: clientId, scope: SCOPE });
  if (status !== 200 || !data || !data.device_code) {
    throw new AuthError(`Microsoft odrzucił prośbę o kod: ${msError(data)}`);
  }
  return {
    deviceCode: data.device_code,
    userCode: data.user_code,
    verificationUri: data.verification_uri,
    expiresIn: data.expires_in || 900,
    interval: data.interval || 5,
  };
}

/** Krok 2: czeka, aż użytkownik zatwierdzi kod w przeglądarce. */
async function pollDeviceFlow(clientId, flow, isCancelled = () => false) {
  const deadline = Date.now() + flow.expiresIn * 1000;
  let interval = flow.interval;
  while (Date.now() < deadline) {
    await U.sleep(interval * 1000);
    if (isCancelled()) throw new AuthError('Logowanie anulowane.');
    const { data } = await U.postForm(`${MS}/token`, {
      grant_type: 'urn:ietf:params:oauth:grant-type:device_code',
      client_id: clientId,
      device_code: flow.deviceCode,
    });
    if (data && data.access_token) {
      return { accessToken: data.access_token, refreshToken: data.refresh_token };
    }
    const err = data && data.error;
    if (err === 'authorization_pending') continue;
    if (err === 'slow_down') {
      interval += 5;
      continue;
    }
    if (err === 'authorization_declined') throw new AuthError('Odrzucono logowanie.');
    if (err === 'expired_token') break;
    throw new AuthError(`Logowanie nie powiodło się: ${msError(data)}`);
  }
  throw new AuthError('Kod logowania wygasł. Spróbuj ponownie.');
}

/** Odświeża token Microsoft (bez ponownego wpisywania kodu). */
async function refresh(clientId, refreshToken) {
  requireClientId(clientId);
  const { data } = await U.postForm(`${MS}/token`, {
    grant_type: 'refresh_token',
    client_id: clientId,
    refresh_token: refreshToken,
    scope: SCOPE,
  });
  if (!data || !data.access_token) {
    throw new AuthError('Sesja tego konta wygasła. Dodaj konto ponownie.');
  }
  return { accessToken: data.access_token, refreshToken: data.refresh_token || refreshToken };
}

const XSTS_ERRORS = {
  2148916233: 'To konto Microsoft nie ma profilu Xbox. Załóż go na xbox.com i spróbuj ponownie.',
  2148916235: 'Xbox Live jest niedostępne w kraju tego konta.',
  2148916236: 'To konto wymaga weryfikacji dorosłego (Xbox).',
  2148916237: 'To konto wymaga weryfikacji dorosłego (Xbox).',
  2148916238: 'To konto należy do dziecka i musi być dodane do rodziny Microsoft.',
};

/** Krok 3: Xbox Live → XSTS → Minecraft. Zwraca profil i token do uruchomienia gry. */
async function minecraftLogin(msAccessToken) {
  const xbl = await U.postJson('https://user.auth.xboxlive.com/user/authenticate', {
    Properties: {
      AuthMethod: 'RPS',
      SiteName: 'user.auth.xboxlive.com',
      RpsTicket: 'd=' + msAccessToken,
    },
    RelyingParty: 'http://auth.xboxlive.com',
    TokenType: 'JWT',
  });
  if (xbl.status !== 200 || !xbl.data || !xbl.data.Token) {
    throw new AuthError(`Logowanie do Xbox Live nie powiodło się (HTTP ${xbl.status}).`);
  }
  const uhs = xbl.data.DisplayClaims.xui[0].uhs;

  const xsts = await U.postJson('https://xsts.auth.xboxlive.com/xsts/authorize', {
    Properties: { SandboxId: 'RETAIL', UserTokens: [xbl.data.Token] },
    RelyingParty: 'rp://api.minecraftservices.com/',
    TokenType: 'JWT',
  });
  if (xsts.status !== 200 || !xsts.data || !xsts.data.Token) {
    const code = xsts.data && xsts.data.XErr;
    throw new AuthError(XSTS_ERRORS[code] || `Autoryzacja Xbox (XSTS) nie powiodła się (HTTP ${xsts.status}).`);
  }

  const mc = await U.postJson('https://api.minecraftservices.com/authentication/login_with_xbox', {
    identityToken: `XBL3.0 x=${uhs};${xsts.data.Token}`,
  });
  if (mc.status !== 200 || !mc.data || !mc.data.access_token) {
    if (mc.status === 403) {
      throw new AuthError(
        'Mojang odrzucił logowanie: Client ID tej aplikacji nie jest jeszcze zatwierdzony ("Invalid app registration"). ' +
          'Trzeba złożyć wniosek do Mojang i poczekać na akceptację (patrz README).'
      );
    }
    throw new AuthError(`Logowanie do Minecrafta nie powiodło się (HTTP ${mc.status}).`);
  }

  let profile;
  try {
    profile = await U.getJson('https://api.minecraftservices.com/minecraft/profile', {
      Authorization: 'Bearer ' + mc.data.access_token,
    });
  } catch (e) {
    if (/HTTP 404/.test(e.message)) throw new AuthError('To konto Microsoft nie ma Minecrafta Java.');
    throw e;
  }
  return { uuid: profile.id, name: profile.name, accessToken: mc.data.access_token, expiresIn: mc.data.expires_in };
}

module.exports = { AuthError, startDeviceFlow, pollDeviceFlow, refresh, minecraftLogin };
