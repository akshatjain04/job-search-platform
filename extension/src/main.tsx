import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { call, platformOrigin, signIn } from './client';
import { extractCurrentPage, normalizeCapture, type Capture } from './capture';
import './style.css';
function Popup() {
  const [origin, setOrigin] = useState('http://localhost:8080');
  const [capture, setCapture] = useState<Capture>();
  const [job, setJob] = useState<{ id: string; title: string }>();
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [resume, setResume] = useState('');
  const [bases, setBases] = useState<{ id: string; filename: string }[]>([]);
  useEffect(() => {
    chrome.storage.local.get('origin').then((v) => {
      if (v.origin) setOrigin(v.origin);
    });
  }, []);
  async function work(action: () => Promise<void>) {
    setError('');
    setBusy(true);
    try {
      await action();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function inspect() {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    if (!tab.id || !tab.url?.startsWith('http'))
      throw new Error('Chrome settings and internal pages cannot be captured.');
    const [result] = await chrome.scripting.executeScript({
      target: { tabId: tab.id },
      func: extractCurrentPage,
    });
    setCapture(normalizeCapture(result.result as Capture));
    setJob(undefined);
    setNotice('Review what will be submitted. Nothing has been uploaded yet.');
  }
  const base = () => platformOrigin(origin);
  return (
    <main>
      <p className="eyebrow">MYJOBAI · CONTEXTUAL CAPTURE</p>
      <h1>Your next opportunity.</h1>
      <p>Only the current page, only when you choose.</p>
      <label>
        Dashboard origin
        <input value={origin} onChange={(e) => setOrigin(e.target.value)} />
      </label>
      <div className="actions">
        <button
          disabled={busy}
          onClick={() =>
            work(async () => {
              await signIn(base());
              setNotice('Connected for one hour.');
            })
          }
        >
          Connect securely
        </button>
        <button className="secondary" onClick={() => chrome.tabs.create({ url: base() })}>
          Dashboard
        </button>
      </div>
      <small>
        Sign in to your dashboard first. Register extension ID <code>{chrome.runtime.id}</code> in
        EXTENSION_IDS.
      </small>
      <hr />
      <button disabled={busy} onClick={() => work(inspect)}>
        Analyze current page
      </button>
      {capture && (
        <section>
          <h2>{capture.title}</h2>
          <small>{capture.url}</small>
          <label>
            Company (review required if missing)
            <input
              value={capture.company}
              onChange={(e) => setCapture({ ...capture, company: e.target.value })}
            />
          </label>
          <details>
            <summary>Review captured text ({capture.text.length} characters)</summary>
            <textarea
              aria-label="Captured text"
              rows={8}
              value={capture.text}
              onChange={(e) => setCapture({ ...capture, text: e.target.value })}
            />
          </details>
          <button
            disabled={busy}
            onClick={() =>
              work(async () => {
                const saved = await call<{ id: string; title: string }>(
                  base(),
                  '/page-captures',
                  capture,
                );
                setJob(saved);
                setNotice('Saved as a canonical opportunity. Matching is queued.');
                setBases(await call(base(), '/resumes'));
              })
            }
          >
            Save and match this opportunity
          </button>
        </section>
      )}
      {job && (
        <section>
          <h2>{job.title}</h2>
          <div className="actions">
            <button
              onClick={() =>
                work(async () => {
                  const match = await call<{ score: number }>(
                    base(),
                    '/jobs/' + job.id + '/analyze',
                    {},
                  );
                  setNotice('Match score: ' + Math.round(match.score));
                })
              }
            >
              Match now
            </button>
            <button
              className="secondary"
              onClick={() => chrome.tabs.create({ url: base() + '/jobs/' + job.id })}
            >
              Open job and outreach
            </button>
          </div>
          <label>
            Base resume
            <select value={resume} onChange={(e) => setResume(e.target.value)}>
              <option value="">Select a resume</option>
              {bases.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.filename}
                </option>
              ))}
            </select>
          </label>
          <button
            disabled={!resume || busy}
            onClick={() =>
              work(async () => {
                await call(base(), '/resumes/tailor', { jobId: job.id, resumeId: resume });
                setNotice('Resume tailoring queued. Review the result in your dashboard.');
              })
            }
          >
            Tailor resume
          </button>
        </section>
      )}
      {notice && (
        <p role="status" className="notice">
          {notice}
        </p>
      )}
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
      <button
        className="link"
        onClick={() =>
          chrome.storage.session.clear().then(() => setNotice('Extension session removed.'))
        }
      >
        Disconnect extension
      </button>
    </main>
  );
}
createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <Popup />
  </React.StrictMode>,
);
