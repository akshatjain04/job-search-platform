import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { AuthBoundary } from './auth';
afterEach(() => vi.unstubAllGlobals());
it('offers production OAuth without a password or token form', async () => {
  vi.stubGlobal(
    'fetch',
    vi
      .fn()
      .mockImplementation((url: string) =>
        Promise.resolve(
          url.endsWith('/config')
            ? new Response('{"testMode":false}')
            : new Response('{}', { status: 401 }),
        ),
      ),
  );
  render(
    <AuthBoundary>
      <p>Private data</p>
    </AuthBoundary>,
  );
  await waitFor(() =>
    expect(screen.getByRole('link', { name: 'Sign in securely' })).toHaveAttribute(
      'href',
      '/api/v1/auth/login',
    ),
  );
  expect(screen.queryByText('Private data')).not.toBeInTheDocument();
});
