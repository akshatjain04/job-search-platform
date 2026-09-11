export function platformOrigin(raw: string) {
  const url = new URL(raw);
  if (
    url.username ||
    url.password ||
    url.pathname !== '/' ||
    url.search ||
    url.hash ||
    !(
      url.protocol === 'https:' ||
      (url.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(url.hostname))
    )
  )
    throw new Error('Use your HTTPS dashboard origin, or HTTP localhost for isolated testing.');
  return url.origin;
}
export async function call<T>(origin: string, path: string, body?: unknown): Promise<T> {
  const saved = await chrome.storage.session.get(['token', 'expiresAt', 'origin']);
  if (saved.origin !== origin || !saved.token || saved.expiresAt < Date.now())
    throw new Error('Sign in to this platform again; extension sessions expire after one hour.');
  const response = await fetch(origin + '/api/v1' + path, {
    method: body === undefined ? 'GET' : 'POST',
    headers: {
      Authorization: 'Bearer ' + saved.token,
      'Content-Type': 'application/json',
      'Idempotency-Key': crypto.randomUUID(),
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    credentials: 'omit',
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.message || 'Platform request failed (' + response.status + ')');
  }
  return response.json();
}
function base64(bytes: Uint8Array) {
  return btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}
export async function signIn(origin: string) {
  const allowed = await chrome.permissions.request({ origins: [origin + '/*'] });
  if (!allowed) throw new Error('Permission to your dashboard origin is required.');
  const verifier = base64(crypto.getRandomValues(new Uint8Array(32))),
    state = base64(crypto.getRandomValues(new Uint8Array(32)));
  const challenge = base64(
    new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))),
  );
  const redirectUri = chrome.identity.getRedirectURL();
  const url =
    origin +
    '/api/v1/auth/extension/authorize?' +
    new URLSearchParams({ redirectUri, challenge, state });
  const result = await chrome.identity.launchWebAuthFlow({ url, interactive: true });
  if (!result) throw new Error('Sign-in was cancelled.');
  const callback = new URL(result);
  if (
    callback.origin + callback.pathname !== redirectUri ||
    callback.searchParams.get('state') !== state
  )
    throw new Error('Invalid authorization callback.');
  const response = await fetch(origin + '/api/v1/auth/extension/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code: callback.searchParams.get('code'), verifier, redirectUri }),
    credentials: 'omit',
  });
  if (!response.ok)
    throw new Error(
      'Extension authorization failed. Sign in to the dashboard first and register this extension ID.',
    );
  const token = await response.json();
  await chrome.storage.session.set({
    token: token.accessToken,
    expiresAt: Date.now() + token.expiresIn * 1000,
    origin,
  });
  await chrome.storage.local.set({ origin });
}
