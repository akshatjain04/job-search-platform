import { useState } from 'react';
import { api, post, ApiError } from '../api';
import { useSession } from '../auth';
import { Field, ErrorNotice, useLoad, split, Badge } from '../shared';
import type { Profile as ProfileData, Fact } from '../types';
export default function Profile() {
  const user = useSession();
  const initial: ProfileData = {
    userId: user.id,
    identity: { name: '', email: user.email, phone: '', location: '', links: [] },
    workHistory: [],
    projects: [],
    education: [],
    skills: [],
    certifications: [],
    achievements: [],
    preferences: {
      roles: [],
      excludedRoles: [],
      companies: [],
      excludedCompanies: [],
      locations: [],
      remoteOnly: false,
      minimumSalary: null,
      currency: 'USD',
      workAuthorization: '',
      noticePeriod: '',
      tone: 'professional',
      resumeTemplate: 'classic',
      outreachTemplates: {},
      alertThreshold: 80,
    },
  };
  const {
    data: profile,
    setData: setProfile,
    error,
  } = useLoad(() =>
    api<ProfileData>('/profile').catch((e) => {
      if (e instanceof ApiError && e.status === 404) return initial;
      throw e;
    }),
  );
  const facts = useLoad(() => api<Fact[]>('/profile/facts'));
  const [message, setMessage] = useState('');
  const [failure, setFailure] = useState('');
  const [factSkills, setFactSkills] = useState('');
  if (!profile) return <ErrorNotice error={error} />;
  const set = (value: Partial<ProfileData>) => setProfile({ ...profile, ...value });
  return (
    <>
      <header className="page-header">
        <div>
          <p className="eyebrow">THE SOURCE OF YOUR STORY</p>
          <h1>Your profile</h1>
          <p>Keep a structured profile. Verify the facts AI may use.</p>
        </div>
      </header>
      <ErrorNotice error={failure || error} />
      {message && (
        <p role="status" className="notice">
          {message}
        </p>
      )}
      <form
        className="panel form-grid"
        onSubmit={async (e) => {
          e.preventDefault();
          try {
            await api('/profile', { method: 'PUT', body: JSON.stringify(profile) });
            setMessage('Profile saved.');
          } catch (err) {
            setFailure((err as Error).message);
          }
        }}
      >
        {(['name', 'email', 'phone', 'location'] as const).map((key) => (
          <Field key={key} label={key[0].toUpperCase() + key.slice(1)}>
            <input
              required={key === 'name' || key === 'email'}
              type={key === 'email' ? 'email' : 'text'}
              value={profile.identity[key]}
              onChange={(e) => set({ identity: { ...profile.identity, [key]: e.target.value } })}
            />
          </Field>
        ))}
        <Field label="Portfolio / profile URLs (comma separated)">
          <input
            value={profile.identity.links.join(', ')}
            onChange={(e) =>
              set({ identity: { ...profile.identity, links: split(e.target.value) } })
            }
          />
        </Field>
        {(['skills', 'certifications', 'achievements'] as const).map((key) => (
          <Field key={key} label={key + ' (comma separated)'}>
            <textarea
              value={profile[key].join(', ')}
              onChange={(e) => set({ [key]: split(e.target.value) })}
            />
          </Field>
        ))}
        <details className="wide">
          <summary>Work experience, projects and education</summary>
          <p className="muted">
            Add structured records. Generated claims additionally require verified facts below.
          </p>
          {(['workHistory', 'projects', 'education'] as const).map((key) => (
            <div className="collection" key={key}>
              <h3>{key}</h3>
              {profile[key].map((record, index) => (
                <div className="form-grid" key={index}>
                  {Object.entries(record).map(([field, value]) => (
                    <Field key={field} label={field}>
                      <input
                        value={Array.isArray(value) ? value.join(', ') : (value ?? '')}
                        onChange={(e) => {
                          const records = [...profile[key]];
                          records[index] = {
                            ...record,
                            [field]: field === 'skills' ? split(e.target.value) : e.target.value,
                          };
                          set({ [key]: records });
                        }}
                      />
                    </Field>
                  ))}
                  <button
                    type="button"
                    className="secondary"
                    onClick={() => set({ [key]: profile[key].filter((_, i) => i !== index) })}
                  >
                    Remove
                  </button>
                </div>
              ))}
              <button
                type="button"
                className="secondary"
                onClick={() => {
                  const record =
                    key === 'workHistory'
                      ? { company: '', role: '', from: '', to: null, description: '' }
                      : key === 'projects'
                        ? { name: '', description: '', url: '', skills: [] }
                        : { institution: '', qualification: '', dates: '' };
                  set({ [key]: [...profile[key], record] });
                }}
              >
                Add{' '}
                {key === 'workHistory'
                  ? 'experience'
                  : key === 'projects'
                    ? 'project'
                    : 'education'}
              </button>
            </div>
          ))}
        </details>
        <h2 className="wide">What matters in your next role</h2>
        {(['roles', 'excludedRoles', 'companies', 'excludedCompanies', 'locations'] as const).map(
          (key) => (
            <Field key={key} label={key + ' (comma separated)'}>
              <input
                value={profile.preferences[key].join(', ')}
                onChange={(e) =>
                  set({ preferences: { ...profile.preferences, [key]: split(e.target.value) } })
                }
              />
            </Field>
          ),
        )}
        <Field label="Minimum annual salary">
          <input
            type="number"
            min="0"
            value={profile.preferences.minimumSalary ?? ''}
            onChange={(e) =>
              set({
                preferences: {
                  ...profile.preferences,
                  minimumSalary: e.target.value ? +e.target.value : null,
                },
              })
            }
          />
        </Field>
        {(['currency', 'workAuthorization', 'noticePeriod'] as const).map((key) => (
          <Field label={key} key={key}>
            <input
              value={profile.preferences[key]}
              onChange={(e) =>
                set({ preferences: { ...profile.preferences, [key]: e.target.value } })
              }
            />
          </Field>
        ))}
        <Field label="Workplace">
          <select
            value={String(profile.preferences.remoteOnly)}
            onChange={(e) =>
              set({
                preferences: { ...profile.preferences, remoteOnly: e.target.value === 'true' },
              })
            }
          >
            <option value="false">Open to any workplace</option>
            <option value="true">Remote only</option>
          </select>
        </Field>
        <Field label="Outreach tone">
          <select
            value={profile.preferences.tone}
            onChange={(e) => set({ preferences: { ...profile.preferences, tone: e.target.value } })}
          >
            <option>professional</option>
            <option>friendly</option>
          </select>
        </Field>
        <Field label="Resume template">
          <select
            value={profile.preferences.resumeTemplate}
            onChange={(e) =>
              set({ preferences: { ...profile.preferences, resumeTemplate: e.target.value } })
            }
          >
            <option>classic</option>
            <option>compact</option>
          </select>
        </Field>
        <Field label="Match alert threshold">
          <input
            type="number"
            min="0"
            max="100"
            value={profile.preferences.alertThreshold}
            onChange={(e) =>
              set({ preferences: { ...profile.preferences, alertThreshold: +e.target.value } })
            }
          />
        </Field>
        <details className="wide">
          <summary>Personal outreach templates</summary>
          <p>Available fields: {'{{recipient}}, {{role}}, {{company}}, {{facts}}, {{name}}'}</p>
          {['EMAIL', 'LINKEDIN', 'WHATSAPP'].map((channel) => (
            <Field label={channel} key={channel}>
              <textarea
                rows={5}
                value={profile.preferences.outreachTemplates[channel] ?? ''}
                onChange={(e) => {
                  const templates = { ...profile.preferences.outreachTemplates };
                  if (e.target.value) templates[channel] = e.target.value;
                  else delete templates[channel];
                  set({ preferences: { ...profile.preferences, outreachTemplates: templates } });
                }}
              />
            </Field>
          ))}
        </details>
        <div className="wide">
          <button>Save profile</button>
        </div>
      </form>
      <h2>Verified experience facts</h2>
      <p className="muted">
        A skill listed in your profile alone is not proof. Add source-backed facts for matching and
        resume generation.
      </p>
      <form
        className="panel form-grid"
        onSubmit={async (e) => {
          e.preventDefault();
          const form = e.currentTarget;
          const f = new FormData(form);
          try {
            await post('/profile/facts', {
              id: crypto.randomUUID(),
              userId: user.id,
              company: f.get('company'),
              context: f.get('context'),
              statement: f.get('statement'),
              skills: split(factSkills),
              metrics: {},
              from: f.get('from') || null,
              to: f.get('to') || null,
              provenance: f.get('provenance'),
              verified: f.get('verified') === 'on',
            });
            facts.reload();
            form.reset();
            setFactSkills('');
          } catch (err) {
            setFailure((err as Error).message);
          }
        }}
      >
        <Field label="Company">
          <input name="company" />
        </Field>
        <Field label="Role or context">
          <input name="context" required />
        </Field>
        <Field label="Fact or achievement">
          <textarea name="statement" required />
        </Field>
        <Field label="Supported skills">
          <input value={factSkills} onChange={(e) => setFactSkills(e.target.value)} />
        </Field>
        <Field label="Source / provenance">
          <input name="provenance" required placeholder="Resume page 1; project record…" />
        </Field>
        <Field label="Verified start date">
          <input name="from" type="date" />
        </Field>
        <Field label="Verified end date">
          <input name="to" type="date" />
        </Field>
        <label className="check">
          <input name="verified" type="checkbox" required />I reviewed this fact and confirm it is
          accurate.
        </label>
        <button>Add verified fact</button>
      </form>
      <div className="stack">
        {facts.data?.map((f) => (
          <article className="panel" key={f.id}>
            <div className="row">
              <h3>
                {f.company} · {f.context}
              </h3>
              <Badge tone={f.verified ? 'green' : ''}>
                {f.verified ? 'Verified' : 'Needs review'}
              </Badge>
            </div>
            <p>{f.statement}</p>
            <small>{f.provenance}</small>
          </article>
        ))}
      </div>
    </>
  );
}
