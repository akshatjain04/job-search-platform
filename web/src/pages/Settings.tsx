import { useState } from 'react';
import { api, post } from '../api';
import { Badge, ErrorNotice, Field, useLoad } from '../shared';
import type { Connector } from '../types';
export default function Settings() {
  const configs = useLoad(() => api<Connector[]>('/connectors'));
  const [error, setError] = useState('');
  const [token, setToken] = useState('');
  const [notice, setNotice] = useState('');
  return (
    <>
      <header className="page-header">
        <div>
          <p className="eyebrow">CONNECTED, ON YOUR TERMS</p>
          <h1>Connections</h1>
          <p>Public job feeds, your mailbox and authenticated AI clients.</p>
        </div>
      </header>
      <ErrorNotice error={error || configs.error} />
      {notice && <p className="notice">{notice}</p>}
      <section className="panel">
        <h2>Job discovery</h2>
        <form
          className="form-grid"
          onSubmit={async (e) => {
            e.preventDefault();
            const f = new FormData(e.currentTarget);
            try {
              await post('/connectors', {
                connector: f.get('connector'),
                board: f.get('board'),
                role: f.get('role'),
                intervalMinutes: Number(f.get('interval')),
                enabled: true,
              });
              configs.reload();
            } catch (err) {
              setError((err as Error).message);
            }
          }}
        >
          <Field label="Source">
            <select name="connector">
              <option>greenhouse</option>
              <option>lever</option>
              <option>ashby</option>
              <option>company-career</option>
            </select>
          </Field>
          <Field label="Public board identifier (or structured career URL)">
            <input name="board" required placeholder="acme" />
          </Field>
          <Field label="Role filter">
            <input name="role" placeholder="Engineer" />
          </Field>
          <Field label="Run every (minutes)">
            <input name="interval" type="number" min="15" max="10080" defaultValue="360" required />
          </Field>
          <button>Connect source</button>
        </form>
        {configs.data?.map((c) => (
          <div className="connection row" key={c.id}>
            <div>
              <strong>{c.board}</strong>
              <p>
                {c.connector} · Every {c.intervalMinutes} min
              </p>
            </div>
            <Badge tone={c.enabled ? 'green' : ''}>{c.enabled ? 'Enabled' : 'Paused'}</Badge>
            <button
              className="secondary"
              onClick={() =>
                post('/connectors/' + c.id + '/run')
                  .then(() => setNotice('Discovery queued.'))
                  .catch((e) => setError(e.message))
              }
            >
              Run now
            </button>
          </div>
        ))}
      </section>
      <section className="panel">
        <h2>Mailbox</h2>
        <p>
          Mail is sent only after you approve an exact immutable message and attachment. OAuth
          credentials stay on the backend.
        </p>
        <div className="actions">
          <a className="button secondary" href="/api/v1/mailboxes/gmail/connect">
            Connect Gmail
          </a>
          <a className="button secondary" href="/api/v1/mailboxes/outlook/connect">
            Connect Outlook
          </a>
        </div>
      </section>
      <section className="panel">
        <h2>MCP access</h2>
        <p>
          Create an access credential for your AI client. It expires in one hour and is revoked when
          you log out. The MCP server has no arbitrary email-sending tool.
        </p>
        <button
          onClick={() =>
            post<{ accessToken: string }>('/auth/access-token')
              .then((r) => setToken(r.accessToken))
              .catch((e) => setError(e.message))
          }
        >
          Create one-hour access token
        </button>
        {token && (
          <Field label="Access token (copy into your local MCP client; never commit)">
            <input value={token} readOnly type="password" onFocus={(e) => e.target.select()} />
          </Field>
        )}
        <p>
          Endpoint: <code>{location.origin}/mcp</code>
        </p>
      </section>
    </>
  );
}
