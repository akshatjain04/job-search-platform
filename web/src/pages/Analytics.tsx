import { api } from '../api';
import { Empty, ErrorNotice, useLoad } from '../shared';
type Performance = {
  label: string;
  applications: number;
  responses: number;
  interviews: number;
  offers: number;
};
type Summary = {
  lifecycle: Record<string, number>;
  sources: Performance[];
  resumes: Performance[];
  outreach: Record<string, number>;
  aiRequests: number;
  cacheHits: number;
  inputTokens: number;
  outputTokens: number;
  reportedCost: number | null;
};
function PerformanceTable({ rows }: { rows: Performance[] }) {
  return rows.length ? (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Source / version</th>
            <th>Applications</th>
            <th>Replies</th>
            <th>Interviews</th>
            <th>Offers</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.label}>
              <td>{r.label}</td>
              <td>{r.applications}</td>
              <td>{r.responses}</td>
              <td>{r.interviews}</td>
              <td>{r.offers}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  ) : (
    <Empty>Results appear as you track applications and record outcomes.</Empty>
  );
}
export default function Analytics() {
  const data = useLoad(() => api<Summary>('/analytics'));
  return (
    <>
      <header className="page-header">
        <div>
          <p className="eyebrow">LEARN FROM YOUR PROGRESS</p>
          <h1>Insights</h1>
          <p>Recorded outcomes, not predictions. Your updates keep these numbers useful.</p>
        </div>
        <button className="secondary" onClick={data.reload}>
          Refresh
        </button>
      </header>
      <ErrorNotice error={data.error} />
      {data.data && (
        <>
          <div className="stats">
            {['APPLIED', 'REPLIED', 'INTERVIEW', 'OFFER'].map((state) => (
              <section className="panel" key={state}>
                <p className="eyebrow">EVER REACHED {state}</p>
                <strong className="stat">{data.data!.lifecycle[state] ?? 0}</strong>
              </section>
            ))}
          </div>
          <section className="panel">
            <h2>Source performance</h2>
            <p className="muted">
              A canonical job can have multiple sources, so source totals may overlap. These are
              associations, not causal attribution.
            </p>
            <PerformanceTable rows={data.data.sources} />
            <h2>Resume performance</h2>
            <p className="muted">
              Versions attached to sent outreach, associated with the tracked job's recorded
              outcomes.
            </p>
            <PerformanceTable rows={data.data.resumes} />
          </section>
          <section className="panel">
            <h2>Outreach activity</h2>
            {Object.entries(data.data.outreach).map(([key, count]) => (
              <p key={key}>
                {key.replaceAll(':', ' · ')}: {count}
              </p>
            ))}
            <h2>AI usage</h2>
            <p>
              {data.data.aiRequests} provider requests · {data.data.cacheHits} cache hits
            </p>
            <p>
              {data.data.inputTokens} input tokens · {data.data.outputTokens} output tokens
            </p>
            <p>
              Reported estimate:{' '}
              {data.data.reportedCost === null
                ? 'Unavailable until pricing/usage is reported'
                : '$' + data.data.reportedCost.toFixed(4)}
            </p>
            <small>
              Estimates may omit failed attempts or provider-specific charges. Reconcile with
              provider billing.
            </small>
          </section>
        </>
      )}
    </>
  );
}
