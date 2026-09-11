import { api } from '../api';
import { Badge, ErrorNotice, useLoad } from '../shared';
import type { Task } from '../types';
export default function Activity() {
  const list = useLoad(() => api<Task[]>('/tasks'));
  const audit = useLoad(() =>
    api<{ id: string; action: string; at: string; metadata: Record<string, string> }[]>(
      '/audit-events',
    ),
  );
  return (
    <>
      <header className="page-header">
        <div>
          <p className="eyebrow">WHAT'S HAPPENING</p>
          <h1>Activity</h1>
          <p>Durable background work, with visible attempts and outcomes.</p>
        </div>
        <button
          onClick={() => {
            list.reload();
            audit.reload();
          }}
        >
          Refresh activity
        </button>
      </header>
      <ErrorNotice error={list.error || audit.error} />
      <details className="panel">
        <summary>Audit history and match alerts</summary>
        {audit.data?.map((event) => (
          <article key={event.id}>
            <h3>{event.action.replaceAll('_', ' ')}</h3>
            <small>{new Date(event.at).toLocaleString()}</small>
            <pre>{JSON.stringify(event.metadata, null, 2)}</pre>
          </article>
        ))}
      </details>
      <div className="stack">
        {list.data?.map((task) => (
          <article className="panel" key={task.id}>
            <div className="row">
              <h3>{task.eventType.replaceAll('_', ' ')}</h3>
              <Badge tone={task.status === 'COMPLETED' ? 'green' : ''}>{task.status}</Badge>
            </div>
            <p>
              Attempt {task.attemptCount} of {task.maxAttempts}
            </p>
            <small>{task.id}</small>
            {task.lastError && <p className="notice error">{task.lastError}</p>}
          </article>
        ))}
      </div>
    </>
  );
}
