import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { Badge, ErrorNotice, Field, useLoad, date } from '../shared';
import type { Application, ApplicationEvent, Job } from '../types';
export default function Tracker() {
  const list = useLoad(() => api<Application[]>('/applications'));
  const jobs = useLoad(() => api<{ items: Job[] }>('/jobs?size=100'));
  const [events, setEvents] = useState<ApplicationEvent[]>([]);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState('');
  const states = [
    'DISCOVERED',
    'SAVED',
    'SHORTLISTED',
    'PREPARING',
    'READY_TO_APPLY',
    'APPLIED',
    'OUTREACH_PREPARED',
    'OUTREACH_SENT',
    'REPLIED',
    'INTERVIEW',
    'OFFER',
    'REJECTED',
    'WITHDRAWN',
  ];
  return (
    <>
      <header className="page-header">
        <div>
          <p className="eyebrow">MOMENTUM, ONE STEP AT A TIME</p>
          <h1>Application tracker</h1>
          <p>Every transition is validated and recorded.</p>
        </div>
      </header>
      <ErrorNotice error={error || list.error} />
      <div className="stats">
        {['APPLIED', 'REPLIED', 'INTERVIEW', 'OFFER'].map((state) => (
          <div className="panel" key={state}>
            <p className="eyebrow">{state}</p>
            <strong className="stat">
              {list.data?.filter((a) => a.state === state).length ?? 0}
            </strong>
          </div>
        ))}
      </div>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Opportunity</th>
              <th>Current state</th>
              <th>Updated</th>
              <th>Next step</th>
              <th>History</th>
            </tr>
          </thead>
          <tbody>
            {list.data?.map((a) => (
              <tr key={a.id}>
                <td>
                  <Link to={'/jobs/' + a.jobId}>
                    {jobs.data?.items.find((j) => j.id === a.jobId)?.title ?? a.jobId.slice(0, 8)}
                  </Link>
                </td>
                <td>
                  <Badge>{a.state}</Badge>
                </td>
                <td>{date(a.updatedAt)}</td>
                <td>
                  <select
                    aria-label="Application state"
                    value={a.state}
                    onChange={async (e) => {
                      try {
                        await api('/applications/' + a.id, {
                          method: 'PATCH',
                          body: JSON.stringify({
                            state: e.target.value,
                            note: 'Updated from dashboard',
                          }),
                        });
                        list.reload();
                      } catch (err) {
                        setError((err as Error).message);
                      }
                    }}
                  >
                    {states.map((s) => (
                      <option key={s}>{s}</option>
                    ))}
                  </select>
                </td>
                <td>
                  <button
                    className="secondary"
                    onClick={() =>
                      api<ApplicationEvent[]>('/applications/' + a.id + '/events')
                        .then((e) => {
                          setSelected(a.id);
                          setEvents(e);
                        })
                        .catch((e) => setError(e.message))
                    }
                  >
                    View
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {selected && (
        <section className="panel">
          <h2>Application history</h2>
          <ol className="timeline">
            {events.map((e) => (
              <li key={e.id}>
                <strong>{e.to}</strong>
                <p>{e.note}</p>
                <small>{new Date(e.at).toLocaleString()}</small>
              </li>
            ))}
          </ol>
        </section>
      )}
    </>
  );
}
