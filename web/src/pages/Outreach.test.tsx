import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import Outreach from './Outreach';
import { api, post } from '../api';
vi.mock('../api', () => ({ api: vi.fn(), post: vi.fn(), download: vi.fn() }));
const preview = {
  message: {
    id: 'message',
    channel: 'EMAIL',
    state: 'AWAITING_APPROVAL',
    currentVersionId: 'version',
    updatedAt: '2026-09-11T00:00:00Z',
  },
  version: {
    id: 'version',
    subject: 'Application',
    body: 'Verified achievement',
    recipientValue: 'careers@example.com',
    recipientId: 'recipient',
    resumeVersionId: 'resume',
  },
  recipient: { sourceUrl: 'https://example.com/contact', verificationStatus: 'PUBLIC' },
  resume: { id: 'resume', pdfHash: 'hash', content: { sections: [] } },
  fingerprint: 'fingerprint',
  approvalId: null,
};
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(api).mockImplementation(async (path) =>
    path === '/outreach' ? [preview.message] : preview,
  );
  vi.mocked(post).mockResolvedValue({ id: 'approval' });
});
it('requires explicit review of both email and attachment and binds exact version', async () => {
  render(<Outreach />);
  fireEvent.click(await screen.findByRole('button', { name: /EMAIL AWAITING_APPROVAL/ }));
  const button = await screen.findByRole('button', { name: 'Approve exact email and queue' });
  expect(button).toBeDisabled();
  fireEvent.click(screen.getByLabelText(/I approve this exact/));
  expect(button).toBeDisabled();
  fireEvent.click(screen.getByLabelText(/I have reviewed the attachment/));
  expect(button).toBeEnabled();
  fireEvent.click(button);
  await waitFor(() =>
    expect(post).toHaveBeenCalledWith('/outreach/message/approve', {
      expectedVersionId: 'version',
      expectedFingerprint: 'fingerprint',
      explicitlyApproved: true,
    }),
  );
  expect(post).toHaveBeenCalledWith('/approvals/approval/queue');
});
it('editing the preview disables approval until changes have been saved and reviewed', async () => {
  render(<Outreach />);
  fireEvent.click(await screen.findByRole('button', { name: /EMAIL AWAITING_APPROVAL/ }));
  fireEvent.click(await screen.findByLabelText(/I approve this exact/));
  fireEvent.click(screen.getByLabelText(/I have reviewed the attachment/));
  fireEvent.change(screen.getByLabelText('Subject'), { target: { value: 'New subject' } });
  expect(screen.getByRole('button', { name: 'Approve exact email and queue' })).toBeDisabled();
  expect(screen.getByText('Saving changes invalidates any previous approval.')).toBeVisible();
  expect(post).not.toHaveBeenCalled();
});
