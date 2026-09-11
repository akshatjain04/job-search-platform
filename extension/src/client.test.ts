import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { webcrypto } from 'node:crypto';
import { call, signIn } from './client';

const origin = 'https://jobs.mycompany.test';
const saved = { origin, token: 'test-access-token', expiresAt: Date.now() + 3600000 };
const get = vi.fn(),
  set = vi.fn(),
  launch = vi.fn(),
  fetchMock = vi.fn();
beforeEach(() => {
  get.mockResolvedValue({ ...saved });
  set.mockResolvedValue(undefined);
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
  vi.stubGlobal('crypto', webcrypto);
  vi.stubGlobal('chrome', {
    storage: { session: { get, set }, local: { set } },
    permissions: { request: vi.fn().mockResolvedValue(true) },
    identity: {
      getRedirectURL: () => 'https://' + 'a'.repeat(32) + '.chromiumapp.org/',
      launchWebAuthFlow: launch,
    },
  });
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});
it('uses only the bound short-lived access token and omits cookies', async () => {
  fetchMock.mockResolvedValue(
    new Response('{"id":"job"}', { status: 200, headers: { 'Content-Type': 'application/json' } }),
  );
  expect(await call(origin, '/page-captures', { title: 'Engineer' })).toEqual({ id: 'job' });
  expect(fetchMock).toHaveBeenCalledWith(
    origin + '/api/v1/page-captures',
    expect.objectContaining({
      credentials: 'omit',
      method: 'POST',
      headers: expect.objectContaining({ Authorization: 'Bearer test-access-token' }),
    }),
  );
});
it('rejects expired or differently bound sessions before network access', async () => {
  for (const value of [
    { ...saved, expiresAt: 1 },
    { ...saved, origin: 'https://another.test' },
  ]) {
    get.mockResolvedValue(value);
    await expect(call(origin, '/jobs')).rejects.toThrow('Sign in');
  }
  expect(fetchMock).not.toHaveBeenCalled();
});
it('surfaces controlled backend failures', async () => {
  fetchMock.mockResolvedValue(new Response('{"message":"Unsupported page"}', { status: 400 }));
  await expect(call(origin, '/page-captures', {})).rejects.toThrow('Unsupported page');
});
it('PKCE callback requires the matching state before token exchange', async () => {
  launch.mockResolvedValue('https://' + 'a'.repeat(32) + '.chromiumapp.org/?state=wrong&code=code');
  await expect(signIn(origin)).rejects.toThrow('Invalid authorization callback');
  expect(fetchMock).not.toHaveBeenCalled();
});
