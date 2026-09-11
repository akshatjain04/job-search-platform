import { useState } from 'react';
import { api, post, download } from '../api';
import { Badge, Empty, ErrorNotice, Field, useLoad, date } from '../shared';
import type { Message, Preview } from '../types';
export default function Outreach() {
  const list = useLoad(() => api<Message[]>('/outreach'));
  const [selected, setSelected] = useState('');
  const [preview, setPreview] = useState<Preview>();
  const [subject, setSubject] = useState('');
  const [body, setBody] = useState('');
  const [approved, setApproved] = useState(false);
  const [attachmentReviewed, setAttachmentReviewed] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const [evidence, setEvidence] = useState('');
  const [reconciled, setReconciled] = useState(false);
  async function load(id: string) {
    setApproved(false);
    setAttachmentReviewed(false);
    setReconciled(false);
    setEvidence('');
    try {
      const value = await api<Preview>('/outreach/' + id);
      setSelected(id);
      setPreview(value);
      setSubject(value.version.subject);
      setBody(value.version.body);
    } catch (e) {
      setError((e as Error).message);
    }
  }
  async function work(call: () => Promise<unknown>, message: string) {
    setBusy(true);
    setError('');
    try {
      await call();
      setNotice(message);
      await load(selected);
      list.reload();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  const changed = preview && (subject !== preview.version.subject || body !== preview.version.body);
  return (
    <>
      <header className="page-header">
        <div>
          <p className="eyebrow">MAKE EVERY CONVERSATION COUNT</p>
          <h1>Outreach</h1>
          <p>Review the exact message and attachment. You decide what gets sent.</p>
        </div>
        <button className="secondary" onClick={list.reload}>
          Refresh drafts
        </button>
      </header>
      <ErrorNotice error={error || list.error} />
      {notice && (
        <p role="status" className="notice">
          {notice}
        </p>
      )}
      {list.data?.length === 0 && <Empty>Generate a draft from an opportunity to begin.</Empty>}
      <div className="outreach-grid">
        <div className="stack">
          {list.data?.map((m) => (
            <button
              className={'message-card ' + (m.id === selected ? 'selected' : '')}
              key={m.id}
              onClick={() => load(m.id)}
            >
              <div className="row">
                <strong>{m.channel}</strong>
                <Badge tone={m.state === 'SENT' ? 'green' : ''}>{m.state}</Badge>
              </div>
              <small>
                {date(m.updatedAt)} · {m.id.slice(0, 8)}
              </small>
            </button>
          ))}
        </div>
        {preview && (
          <section className="panel">
            <div className="row">
              <h2>Final message preview</h2>
              <Badge>{preview.message.state}</Badge>
            </div>
            <p>
              <strong>To:</strong> {preview.version.recipientValue || 'No recipient selected'}
            </p>
            {preview.recipient && (
              <p>
                <a href={preview.recipient.sourceUrl} target="_blank" rel="noreferrer">
                  Contact provenance ↗
                </a>{' '}
                · {preview.recipient.verificationStatus}
              </p>
            )}
            <Field label="Subject">
              <input
                value={subject}
                disabled={['SENDING', 'SENT', 'DELIVERY_UNKNOWN'].includes(preview.message.state)}
                onChange={(e) => {
                  setSubject(e.target.value);
                  setApproved(false);
                }}
              />
            </Field>
            <Field label="Exact message body">
              <textarea
                rows={13}
                value={body}
                disabled={['SENDING', 'SENT', 'DELIVERY_UNKNOWN'].includes(preview.message.state)}
                onChange={(e) => {
                  setBody(e.target.value);
                  setApproved(false);
                }}
              />
            </Field>
            <p className="muted">Message version {preview.version.id}</p>
            {changed && <p className="notice">Saving changes invalidates any previous approval.</p>}
            <div className="actions">
              <button
                className="secondary"
                disabled={!changed || busy}
                onClick={() =>
                  work(
                    () =>
                      api('/outreach/' + selected, {
                        method: 'PUT',
                        body: JSON.stringify({
                          recipientId: preview.version.recipientId,
                          subject,
                          body,
                          resumeVersionId: preview.version.resumeVersionId,
                        }),
                      }),
                    'New immutable message version saved; prior approval invalidated.',
                  )
                }
              >
                Save changes
              </button>
              <button
                className="secondary"
                onClick={() =>
                  navigator.clipboard
                    .writeText(body)
                    .then(() => setNotice('Message copied for manual sending.'))
                    .catch(() => setError('Clipboard unavailable; select and copy the message.'))
                }
              >
                Copy message
              </button>
            </div>
            {preview.message.state === 'APPROVED' && preview.approvalId && (
              <button
                disabled={!!changed || busy}
                onClick={() =>
                  work(
                    () => post('/approvals/' + preview.approvalId + '/queue'),
                    'Approved email queued for dispatch.',
                  )
                }
              >
                Queue this approved email
              </button>
            )}
            {preview.resume && (
              <div className="attachment">
                <h3>Exact attachment</h3>
                <p>resume-{preview.resume.id}.pdf</p>
                <p className="hash">Attachment fingerprint: {preview.resume.pdfHash}</p>
                <button
                  className="secondary"
                  onClick={() =>
                    download(preview.resume!.id, 'pdf')
                      .then(() => setAttachmentReviewed(true))
                      .catch((e) => setError(e.message))
                  }
                >
                  Open / download attachment
                </button>
                <details>
                  <summary>Structured content and source facts</summary>
                  {preview.resume.content.sections.map((s, i) => (
                    <div key={i}>
                      <h4>{s.heading}</h4>
                      {s.bullets.map((b) => (
                        <p key={b.factId}>
                          {b.text}
                          <br />
                          <small>Source fact: {b.factId}</small>
                        </p>
                      ))}
                    </div>
                  ))}
                </details>
              </div>
            )}
            {preview.message.channel === 'EMAIL' && (
              <div className="approval-box">
                {preview.message.state === 'DRAFT' && (
                  <button
                    disabled={!!changed || busy}
                    onClick={() =>
                      work(
                        () => post('/outreach/' + selected + '/request-approval'),
                        'Ready for your explicit approval.',
                      )
                    }
                  >
                    Prepare final approval
                  </button>
                )}
                {preview.message.state === 'AWAITING_APPROVAL' && (
                  <>
                    <label className="check">
                      <input
                        type="checkbox"
                        checked={approved}
                        onChange={(e) => setApproved(e.target.checked)}
                      />
                      I approve this exact recipient, subject, body and resume attachment.
                    </label>
                    <label className="check">
                      <input
                        type="checkbox"
                        checked={attachmentReviewed}
                        onChange={(e) => setAttachmentReviewed(e.target.checked)}
                      />
                      I have reviewed the attachment shown above.
                    </label>
                    <div className="actions">
                      <button
                        disabled={!approved || !attachmentReviewed || !!changed || busy}
                        onClick={() =>
                          work(async () => {
                            const a = await post<{ id: string }>(
                              '/outreach/' + selected + '/approve',
                              {
                                expectedVersionId: preview.version.id,
                                expectedFingerprint: preview.fingerprint,
                                explicitlyApproved: true,
                              },
                            );
                            await post('/approvals/' + a.id + '/queue');
                          }, 'Approved and queued. The communication worker will validate the approval again before sending.')
                        }
                      >
                        Approve exact email and queue
                      </button>
                      <button
                        className="secondary"
                        disabled={busy}
                        onClick={() =>
                          work(() => post('/outreach/' + selected + '/reject'), 'Email rejected.')
                        }
                      >
                        Reject
                      </button>
                    </div>
                  </>
                )}
                {preview.message.state === 'DELIVERY_UNKNOWN' && (
                  <>
                    <p className="notice error">
                      Delivery is uncertain. Check the connected mailbox’s Sent folder.
                      Reconciliation records your finding and never resends.
                    </p>
                    <Field label="Mailbox reconciliation evidence">
                      <textarea
                        value={evidence}
                        maxLength={1000}
                        onChange={(e) => setEvidence(e.target.value)}
                        placeholder="Describe the provider receipt or your mailbox check; do not enter credentials."
                      />
                    </Field>
                    <label className="check">
                      <input
                        type="checkbox"
                        checked={reconciled}
                        onChange={(e) => setReconciled(e.target.checked)}
                      />
                      I checked the connected mailbox and confirm this finding.
                    </label>
                    <div className="actions">
                      {[true, false].map((delivered) => (
                        <button
                          className="secondary"
                          key={String(delivered)}
                          disabled={busy || !reconciled || !evidence.trim()}
                          onClick={() =>
                            work(
                              () =>
                                post('/outreach/' + selected + '/reconcile', {
                                  delivered,
                                  evidence,
                                  explicitlyConfirmed: true,
                                }),
                              delivered
                                ? 'Delivery confirmed.'
                                : 'Not delivered; old approval invalidated. Edit to create a new draft before approving again.',
                            )
                          }
                        >
                          {delivered ? 'Confirm delivered' : 'Confirm not delivered'}
                        </button>
                      ))}
                    </div>
                  </>
                )}
              </div>
            )}
            {preview.message.channel !== 'EMAIL' && (
              <p className="notice">
                Copy this message and send it yourself in{' '}
                {preview.message.channel === 'LINKEDIN' ? 'LinkedIn' : 'WhatsApp'}. This platform
                does not automate social messaging.
              </p>
            )}
          </section>
        )}
      </div>
    </>
  );
}
