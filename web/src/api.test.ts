import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, setCsrf } from './api';
afterEach(() => vi.unstubAllGlobals());
describe('authenticated API contract', () => {
  it('sends cookies and CSRF on mutations without exposing refresh tokens', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response('{"ok":true}'));
    vi.stubGlobal('fetch', fetch);
    setCsrf('csrf-test');
    await api('/profile', { method: 'PUT', body: '{}' });
    const init = fetch.mock.calls[0][1];
    expect(init.credentials).toBe('include');
    expect(init.headers.get('X-CSRF-Token')).toBe('csrf-test');
    expect(init.headers.get('Authorization')).toBeNull();
  });
  it('preserves controlled API errors', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          new Response('{"code":"CONFLICT","message":"Preview changed"}', { status: 409 }),
        ),
    );
    await expect(api('/outreach/1')).rejects.toMatchObject({
      status: 409,
      code: 'CONFLICT',
      message: 'Preview changed',
    });
  });
});
