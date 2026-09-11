import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { api, post, setCsrf } from './api';
import type { Session } from './types';
import { ErrorNotice, Field } from './shared';
const Auth = createContext<Session | null>(null);
export function useSession() {
  const user = useContext(Auth);
  if (!user) throw new Error('Sign in required');
  return user;
}
export function AuthBoundary({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session>();
  const [ready, setReady] = useState(false);
  const [test, setTest] = useState(false);
  const [email, setEmail] = useState('demo@example.com');
  const [error, setError] = useState('');
  useEffect(() => {
    api<Session>('/auth/session')
      .then((s) => {
        setCsrf(s.csrf);
        setSession(s);
      })
      .catch(() => {})
      .finally(() => setReady(true));
    api<{ testMode: boolean }>('/auth/config')
      .then((c) => setTest(c.testMode))
      .catch((e) => setError(e.message));
  }, []);
  if (!ready) return <div className="loading">Opening your workspace…</div>;
  if (!session)
    return (
      <main className="login">
        <div className="brandmark">M</div>
        <p className="eyebrow">YOUR NEXT CHAPTER</p>
        <h1>
          A thoughtful way
          <br />
          to find your next role.
        </h1>
        <p>
          Discover opportunities, build on your verified experience, and keep every application in
          view.
        </p>
        <ErrorNotice error={error} />
        {test ? (
          <form
            onSubmit={async (e) => {
              e.preventDefault();
              try {
                const s = await post<Session>('/auth/test-login', { email });
                setCsrf(s.csrf);
                setSession(s);
              } catch (err) {
                setError((err as Error).message);
              }
            }}
          >
            <BadgeTest />
            <Field label="Test account email">
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </Field>
            <button>Enter test workspace</button>
          </form>
        ) : (
          <a className="button" href="/api/v1/auth/login">
            Sign in securely
          </a>
        )}
        <small>Your facts stay yours. Every email needs your approval.</small>
      </main>
    );
  return <Auth.Provider value={session}>{children}</Auth.Provider>;
}
function BadgeTest() {
  return (
    <p className="notice">Local acceptance mode · AI is deterministic and email is never sent.</p>
  );
}
