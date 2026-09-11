import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, download } from '../api';
import { Badge, Empty, ErrorNotice, Field, useLoad, date } from '../shared';
import type { BaseResume, ResumeVersion } from '../types';
export default function Resumes() {
  const bases = useLoad(() => api<BaseResume[]>('/resumes'));
  const versions = useLoad(() => api<ResumeVersion[]>('/resumes/versions'));
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [compare, setCompare] = useState<string[]>([]);
  return (
    <>
      <header className="page-header">
        <div>
          <p className="eyebrow">YOUR EXPERIENCE, CLEARLY TOLD</p>
          <h1>Resume library</h1>
          <p>Immutable versions. Source-backed content. No invented achievements.</p>
        </div>
      </header>
      <ErrorNotice error={error || bases.error || versions.error} />
      <form
        className="panel upload"
        onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true);
          try {
            await api('/resumes', { method: 'POST', body: new FormData(e.currentTarget) });
            bases.reload();
          } catch (err) {
            setError((err as Error).message);
          } finally {
            setBusy(false);
          }
        }}
      >
        <Field label="Upload your base resume · PDF or DOCX · up to 10 MB">
          <input name="file" type="file" required accept=".pdf,.docx" />
        </Field>
        <button disabled={busy}>{busy ? 'Extracting…' : 'Upload and extract'}</button>
      </form>
      <h2>Base resumes</h2>
      {bases.data?.length === 0 && (
        <Empty>
          Upload your resume to begin. You will review extracted text before verifying facts.
        </Empty>
      )}
      <div className="stack">
        {bases.data?.map((r) => (
          <article className="panel" key={r.id}>
            <div className="row">
              <h3>{r.filename}</h3>
              <small>{date(r.createdAt)}</small>
            </div>
            <details>
              <summary>Review extracted text</summary>
              <pre className="document-text">{r.extractedText}</pre>
              <p>
                Review the source carefully, then add supported achievements and skills to{' '}
                <Link to="/profile">verified experience facts</Link>. Extraction alone does not
                verify a claim.
              </p>
            </details>
            {r.parsed && (
              <details>
                <summary>Review structured extraction</summary>
                <p>
                  Sections and source lines are extracted for your review. Confirm accuracy before
                  adding anything to your profile.
                </p>
                {r.parsed.sections.map((section, index) => (
                  <section key={index}>
                    <h4>{section.kind.replaceAll('_', ' ')}</h4>
                    {section.lines.map((line) => (
                      <p key={line.lineNumber}>
                        {line.text}
                        <br />
                        <small>
                          Source: {r.filename}, extracted line {line.lineNumber}
                        </small>
                      </p>
                    ))}
                  </section>
                ))}
              </details>
            )}
          </article>
        ))}
      </div>
      <div className="section-title">
        <h2>Tailored versions</h2>
        <button className="secondary" onClick={versions.reload}>
          Refresh
        </button>
      </div>
      <div className="job-grid">
        {versions.data?.map((v) => (
          <article className="panel" key={v.id}>
            <p className="eyebrow">VERSION {v.id.slice(0, 8)}</p>
            <h3>{date(v.createdAt)}</h3>
            <Link to={'/jobs/' + v.jobId}>Related opportunity ↗</Link>
            {v.scoringHistory.map((s, index) => (
              <details key={index}>
                <summary>
                  {index === 0 ? 'Initial evaluation' : 'Repair evaluation'} · {s.compatibility}/100
                </summary>
                <p>Parsing: {s.parsing}%</p>
                {s.explanations.map((text, i) => (
                  <p key={i}>{text}</p>
                ))}
                {Object.entries(s.dimensions).map(([name, value]) => (
                  <p key={name}>
                    {name}: {Math.round(value * 10) / 10}
                  </p>
                ))}
              </details>
            ))}
            <Badge tone={v.scoringHistory.at(-1)?.recommended ? 'green' : ''}>
              {v.scoringHistory.at(-1)?.recommended
                ? 'Recommendation gate passed'
                : 'Human review required'}
            </Badge>
            <div className="actions">
              <button onClick={() => download(v.id, 'pdf').catch((e) => setError(e.message))}>
                PDF
              </button>
              <button
                className="secondary"
                onClick={() => download(v.id, 'docx').catch((e) => setError(e.message))}
              >
                DOCX
              </button>
            </div>
            <label className="check">
              <input
                type="checkbox"
                checked={compare.includes(v.id)}
                onChange={(e) =>
                  setCompare(
                    e.target.checked
                      ? [...compare, v.id].slice(-2)
                      : compare.filter((id) => id !== v.id),
                  )
                }
              />
              Compare structured version
            </label>
          </article>
        ))}
      </div>
      {compare.length === 2 && (
        <section className="panel">
          <h2>Version comparison</h2>
          <div className="compare">
            {compare.map((id) => {
              const v = versions.data?.find((x) => x.id === id);
              return (
                <div key={id}>
                  <h3>{id.slice(0, 8)}</h3>
                  {v?.content.sections.map((section, i) => (
                    <div key={i}>
                      <h4>{section.heading}</h4>
                      {section.bullets.map((b) => (
                        <p key={b.factId}>{b.text}</p>
                      ))}
                    </div>
                  ))}
                </div>
              );
            })}
          </div>
        </section>
      )}
    </>
  );
}
