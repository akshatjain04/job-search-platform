import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, post, download } from '../api';
import { Badge, Empty, ErrorNotice, Field, useLoad, date } from '../shared';
import type { Job, Match, Source, Contact, BaseResume, ResumeVersion, Task } from '../types';
export default function JobDetail() {
  const { id } = useParams();
  const job = useLoad(() => api<Job>('/jobs/' + id), [id]);
  const sources = useLoad(() => api<Source[]>(`/jobs/${id}/sources`), [id]);
  const match = useLoad(() => api<Match>(`/jobs/${id}/match`).catch(() => null), [id]);
  const contacts = useLoad(() => api<Contact[]>(`/jobs/${id}/recruiters`), [id]);
  const bases = useLoad(() => api<BaseResume[]>('/resumes'));
  const versions = useLoad(() => api<ResumeVersion[]>('/resumes/versions?jobId=' + id), [id]);
  const [base, setBase] = useState('');
  const [recipient, setRecipient] = useState('');
  const [resume, setResume] = useState('');
  const [channel, setChannel] = useState('EMAIL');
  const [instructions, setInstructions] = useState('');
  const [length, setLength] = useState('normal');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  async function action(work: () => Promise<unknown>, success: string) {
    setBusy(true);
    setError('');
    try {
      await work();
      setNotice(success);
      match.reload();
      contacts.reload();
      versions.reload();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  }
  if (!job.data) return <ErrorNotice error={job.error} />;
  const j = job.data;
  return (
    <>
      <Link className="back" to="/">
        ← Opportunities
      </Link>
      <header className="page-header">
        <div>
          <p className="eyebrow">{j.company}</p>
          <h1>{j.title}</h1>
          <p>
            {j.location || 'Location not provided'} · {j.remote ? 'Remote' : 'On site / hybrid'} ·{' '}
            {date(j.postedAt)}
          </p>
        </div>
        <a className="button secondary" target="_blank" rel="noreferrer" href={j.canonicalUrl}>
          Original opportunity ↗
        </a>
      </header>
      <ErrorNotice error={error || job.error} />
      {notice && (
        <p role="status" className="notice">
          {notice} <Link to="/activity">View activity →</Link>
        </p>
      )}
      <div className="detail-grid">
        <div className="stack">
          <section className="panel">
            <div className="row">
              <h2>About the opportunity</h2>
              <Badge>{j.kind.replaceAll('_', ' ')}</Badge>
            </div>
            <p className="prewrap">{j.description}</p>
            <div className="tags">
              {j.skills.map((s) => (
                <Badge key={s}>{s}</Badge>
              ))}
            </div>
            <h3>Source provenance</h3>
            {sources.data?.map((s) => (
              <p key={s.id}>
                <a target="_blank" rel="noreferrer" href={s.url}>
                  {s.connector} ↗
                </a>{' '}
                <small>Observed {date(s.observedAt)}</small>
              </p>
            ))}
            <button
              disabled={busy}
              onClick={() =>
                action(() => post('/applications', { jobId: id }), 'Added to application tracker.')
              }
            >
              Save to tracker
            </button>
          </section>
          <section className="panel">
            <div className="row">
              <h2>Recruiter intelligence</h2>
              <button
                className="secondary"
                disabled={busy}
                onClick={() =>
                  action(
                    () => post<Task>(`/jobs/${id}/recruiters/research`),
                    'Public research queued.',
                  )
                }
              >
                Research contacts
              </button>
            </div>
            <p className="muted">
              Actual public sources, with provenance. Inferred addresses cannot be emailed.
            </p>
            {contacts.data?.length === 0 && (
              <Empty>No verified contact yet. Research or add a source-backed contact below.</Empty>
            )}
            {contacts.data?.map((c) => (
              <article className="contact" key={c.id}>
                <h3>{c.name}</h3>
                <p>{c.value}</p>
                <Badge
                  tone={
                    ['PUBLIC', 'USER_VERIFIED', 'PROVIDER_VERIFIED'].includes(c.verificationStatus)
                      ? 'green'
                      : ''
                  }
                >
                  {c.verificationStatus}
                </Badge>
                <p>
                  <a href={c.sourceUrl} target="_blank" rel="noreferrer">
                    Source ↗
                  </a>{' '}
                  · Confidence {Math.round(c.confidence * 100)}%
                </p>
                <small>{c.verificationMethod}</small>
              </article>
            ))}
            <details>
              <summary>Add a contact you found</summary>
              <form
                className="form-grid"
                onSubmit={(e) => {
                  e.preventDefault();
                  const f = new FormData(e.currentTarget);
                  action(
                    () =>
                      post(`/jobs/${id}/recruiters`, {
                        name: f.get('name'),
                        value: f.get('value'),
                        type: f.get('type'),
                        sourceUrl: f.get('source'),
                        explicitlyVerified: f.get('verified') === 'on',
                      }),
                    'Contact saved.',
                  );
                }}
              >
                <Field label="Name / published label">
                  <input name="name" required />
                </Field>
                <Field label="Contact value">
                  <input name="value" required />
                </Field>
                <Field label="Type">
                  <select name="type">
                    <option>EMAIL</option>
                    <option>LINKEDIN</option>
                    <option>PHONE</option>
                  </select>
                </Field>
                <Field label="Source URL">
                  <input type="url" name="source" required />
                </Field>
                <label className="check wide">
                  <input name="verified" type="checkbox" />I verified this exact contact value
                  against the source.
                </label>
                <button disabled={busy}>Save contact</button>
              </form>
            </details>
          </section>
        </div>
        <aside className="stack">
          <section className="panel fit">
            <div className="row">
              <h2>Your fit</h2>
              <button
                className="secondary"
                disabled={busy}
                onClick={() => action(() => post(`/jobs/${id}/analyze`), 'Match updated.')}
              >
                Calculate
              </button>
            </div>
            {match.data ? (
              <>
                <div className="score">
                  {Math.round(match.data.score)}
                  <span>/100</span>
                </div>
                <Badge tone={match.data.eligible ? 'green' : ''}>
                  {match.data.eligible ? 'Eligible' : 'Preference mismatch'}
                </Badge>
                <h3>Matching skills</h3>
                <div className="tags">
                  {match.data.matchedSkills.map((s) => (
                    <Badge tone="green" key={s}>
                      {s}
                    </Badge>
                  ))}
                </div>
                <h3>Gaps to consider</h3>
                <p>{match.data.missingSkills.join(', ') || 'No structured skill gaps'}</p>
                {match.data.explanation.map((s, i) => (
                  <p className="muted" key={i}>
                    {s}
                  </p>
                ))}
                <details>
                  <summary>Score dimensions</summary>
                  {Object.entries(match.data.dimensions).map(([key, value]) => (
                    <div className="metric" key={key}>
                      <span>{key}</span>
                      <meter min="0" max="100" value={value} />
                      <span>{Math.round(value)}</span>
                    </div>
                  ))}
                </details>
              </>
            ) : (
              <p>Calculate a match after creating a profile and verifying facts.</p>
            )}
          </section>
          <section className="panel">
            <h2>Tailor your resume</h2>
            <Field label="Base resume">
              <select value={base} onChange={(e) => setBase(e.target.value)}>
                <option value="">Choose a base resume</option>
                {bases.data?.map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.filename}
                  </option>
                ))}
              </select>
            </Field>
            <button
              disabled={!base || busy}
              onClick={() =>
                action(
                  () => post('/resumes/tailor', { resumeId: base, jobId: id }),
                  'Tailoring queued. Refresh versions after the task completes.',
                )
              }
            >
              Tailor to this opportunity
            </button>
            <button className="text-button" onClick={versions.reload}>
              Refresh versions
            </button>
            {versions.data?.map((v) => (
              <article key={v.id} className="resume-mini">
                <strong>{date(v.createdAt)}</strong>
                <p>
                  Compatibility {v.scoringHistory.at(-1)?.compatibility} · Parsing{' '}
                  {v.scoringHistory.at(-1)?.parsing}%
                </p>
                <Badge tone={v.scoringHistory.at(-1)?.recommended ? 'green' : ''}>
                  {v.scoringHistory.at(-1)?.recommended ? 'Recommended' : 'Review required'}
                </Badge>
                <div className="actions">
                  <button
                    className="secondary"
                    onClick={() => download(v.id, 'pdf').catch((e) => setError(e.message))}
                  >
                    PDF
                  </button>
                  <button
                    className="secondary"
                    onClick={() => download(v.id, 'docx').catch((e) => setError(e.message))}
                  >
                    DOCX
                  </button>
                </div>
              </article>
            ))}
          </section>
          <section className="panel">
            <h2>Prepare outreach</h2>
            <Field label="Channel">
              <select value={channel} onChange={(e) => setChannel(e.target.value)}>
                <option>EMAIL</option>
                <option>LINKEDIN</option>
                <option>WHATSAPP</option>
              </select>
            </Field>
            <Field label="Recipient">
              <select value={recipient} onChange={(e) => setRecipient(e.target.value)}>
                <option value="">Select contact</option>
                {contacts.data?.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name} · {c.value}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Exact resume attachment">
              <select value={resume} onChange={(e) => setResume(e.target.value)}>
                <option value="">Choose version</option>
                {versions.data?.map((v) => (
                  <option key={v.id} value={v.id}>
                    {date(v.createdAt)} · {v.id.slice(0, 8)}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Length">
              <select value={length} onChange={(e) => setLength(e.target.value)}>
                <option>compact</option>
                <option>normal</option>
                <option>detailed</option>
              </select>
            </Field>
            <Field label="Instructions">
              <textarea
                value={instructions}
                onChange={(e) => setInstructions(e.target.value)}
                maxLength={1500}
              />
            </Field>
            <button
              disabled={busy}
              onClick={() =>
                action(
                  () =>
                    post('/outreach/generate', {
                      jobId: id,
                      channel,
                      recipientId: recipient || null,
                      resumeVersionId: resume || null,
                      instructions,
                      length,
                    }),
                  'Draft generation queued. Open Outreach to review.',
                )
              }
            >
              Generate draft
            </button>
            <p className="muted">Generation does not send a message.</p>
          </section>
        </aside>
      </div>
    </>
  );
}
