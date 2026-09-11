import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { api, post } from '../api';
import { Badge, Empty, ErrorNotice, Field, useLoad, date } from '../shared';
import type { Job } from '../types';
export default function Jobs() {
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [showImport, setShowImport] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const filterKey = JSON.stringify(filters);
  useEffect(() => setPage(0), [filterKey]);
  const params = new URLSearchParams({
    role: query,
    page: String(page),
    ...Object.fromEntries(
      Object.entries(filters)
        .filter(([, v]) => v)
        .map(([k, v]) => [k, k === 'since' ? new Date(v).toISOString() : v]),
    ),
  });
  const {
    data,
    error: loadError,
    reload,
  } = useLoad(() => api<{ items: Job[]; total: number }>('/jobs?' + params), [params.toString()]);
  return (
    <>
      <header className="page-header">
        <div>
          <p className="eyebrow">MAKE YOUR NEXT MOVE</p>
          <h1>Opportunities</h1>
          <p>Find the right fit. Lead with your strongest experience.</p>
        </div>
        <button onClick={() => setShowImport(!showImport)}>＋ Capture an opportunity</button>
      </header>
      <ErrorNotice error={error || loadError} />
      {showImport && (
        <form
          className="panel form-grid"
          onSubmit={async (e) => {
            e.preventDefault();
            const f = new FormData(e.currentTarget);
            setBusy(true);
            try {
              await post('/page-captures', {
                url: f.get('url'),
                title: f.get('title'),
                company: f.get('company'),
                text: f.get('text'),
                metadata: {},
              });
              setShowImport(false);
              reload();
            } catch (err) {
              setError((err as Error).message);
            } finally {
              setBusy(false);
            }
          }}
        >
          <Field label="Source URL">
            <input name="url" type="url" required placeholder="https://careers.company.com/…" />
          </Field>
          <Field label="Job or post title">
            <input name="title" required maxLength={300} />
          </Field>
          <Field label="Company">
            <input name="company" required />
          </Field>
          <Field label="Job description or hiring post">
            <textarea name="text" required rows={6} />
          </Field>
          <button disabled={busy}>{busy ? 'Capturing…' : 'Analyze and save'}</button>
        </form>
      )}
      <section className="panel filters">
        <Field label="Search roles or keywords">
          <input
            placeholder="e.g. Java engineer"
            value={query}
            onChange={(e) => {
              setPage(0);
              setQuery(e.target.value);
            }}
          />
        </Field>
        {['company', 'location', 'salaryMin', 'experienceMax', 'minScore'].map((key) => (
          <Field
            key={key}
            label={
              {
                company: 'Company',
                location: 'Location',
                salaryMin: 'Minimum salary',
                experienceMax: 'Experience up to',
                minScore: 'Minimum match',
              }[key]!
            }
          >
            <input
              type={['salaryMin', 'experienceMax', 'minScore'].includes(key) ? 'number' : 'text'}
              min="0"
              value={filters[key] ?? ''}
              onChange={(e) => setFilters({ ...filters, [key]: e.target.value })}
            />
          </Field>
        ))}
        <Field label="Posted since">
          <input
            type="date"
            value={filters.since ?? ''}
            onChange={(e) => setFilters({ ...filters, since: e.target.value })}
          />
        </Field>
        <Field label="Workplace">
          <select
            value={filters.remote ?? ''}
            onChange={(e) => setFilters({ ...filters, remote: e.target.value })}
          >
            <option value="">Any workplace</option>
            <option value="true">Remote</option>
            <option value="false">On site / hybrid</option>
          </select>
        </Field>
        <Field label="Source">
          <select
            value={filters.source ?? ''}
            onChange={(e) => setFilters({ ...filters, source: e.target.value })}
          >
            <option value="">All sources</option>
            {[
              'greenhouse',
              'lever',
              'ashby',
              'company-career',
              'browser-capture',
              'manual-import',
            ].map((s) => (
              <option key={s}>{s}</option>
            ))}
          </select>
        </Field>
        <Field label="Opportunity">
          <select
            value={filters.referral ?? ''}
            onChange={(e) => setFilters({ ...filters, referral: e.target.value })}
          >
            <option value="">All opportunities</option>
            <option value="true">Referral posts</option>
          </select>
        </Field>
        <Field label="Recruiter">
          <select
            value={filters.hasRecruiter ?? ''}
            onChange={(e) => setFilters({ ...filters, hasRecruiter: e.target.value })}
          >
            <option value="">Any</option>
            <option value="true">Verified contact available</option>
          </select>
        </Field>
      </section>
      <div className="section-title">
        <h2>Your shortlist starts here</h2>
        <span>{data?.total ?? 0} opportunities · ranked by fit</span>
      </div>
      <div className="job-grid">
        {data?.items.map((job) => (
          <Link className="job-card" key={job.id} to={'/jobs/' + job.id}>
            <div className="row">
              <span className="company-icon">{job.company.slice(0, 2).toUpperCase()}</span>
              <Badge tone={job.kind === 'REFERRAL_POST' ? 'green' : ''}>
                {job.kind.replaceAll('_', ' ').toLowerCase()}
              </Badge>
            </div>
            <p className="muted">{job.company}</p>
            <h2>{job.title}</h2>
            <p>
              {job.location || 'Location not provided'} {job.remote ? '· Remote' : ''}
            </p>
            <div className="tags">
              {job.skills.slice(0, 4).map((s) => (
                <Badge key={s}>{s}</Badge>
              ))}
            </div>
            <footer>
              <span>{date(job.postedAt)}</span>
              <span className="arrow">View opportunity ↗</span>
            </footer>
          </Link>
        ))}
      </div>
      {data && data.total > 25 && (
        <nav className="actions" aria-label="Opportunity pages">
          <button className="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
            Previous page
          </button>
          <span>
            Page {page + 1} of {Math.ceil(data.total / 25)}
          </span>
          <button
            className="secondary"
            disabled={(page + 1) * 25 >= data.total}
            onClick={() => setPage(page + 1)}
          >
            Next page
          </button>
        </nav>
      )}
      {data?.items.length === 0 && (
        <Empty>
          Add a job or connect a public ATS board to begin. Hiring and referral posts are welcome
          too.
        </Empty>
      )}
    </>
  );
}
