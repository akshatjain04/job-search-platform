#!/usr/bin/env node
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { randomUUID, createHash } from 'node:crypto';
import { setTimeout as delay } from 'node:timers/promises';

// Explicit test overlay only: never accepts a production URL or credentials.
const origin = 'http://localhost:8080';
assert.equal(
  (await (await fetch(origin + '/api/v1/auth/config')).json()).testMode,
  true,
  'Restart test requires explicit non-sending test mode',
);
const login = await fetch(origin + '/api/v1/auth/test-login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'restart-' + randomUUID() + '@example.com' }),
});
assert.equal(login.status, 200);
const session = await login.json(),
  cookie = login.headers.get('set-cookie').split(';')[0];
async function request(path, method = 'GET', data) {
  const response = await fetch(origin + '/api/v1' + path, {
    method,
    headers: {
      // Do not reuse a socket across the intentional Nginx/container restart.
      Connection: 'close',
      Cookie: cookie,
      'X-CSRF-Token': session.csrf,
      'Idempotency-Key': randomUUID(),
      ...(data instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
    },
    body: data === undefined ? undefined : data instanceof FormData ? data : JSON.stringify(data),
  });
  if (!response.ok) {
    // Only synthetic test data is used here; surface the rejecting layer for CI diagnosis.
    const detail = (await response.text()).slice(0, 500);
    throw new Error(path + ' returned ' + response.status + ': ' + detail);
  }
  return response;
}
await request('/profile', 'PUT', {
  userId: session.id,
  identity: {
    name: 'Restart Verified',
    email: session.email,
    phone: '',
    location: 'Remote',
    links: [],
  },
  skills: ['Java'],
});
await request('/profile/facts', 'POST', {
  id: randomUUID(),
  userId: session.id,
  company: 'Acme',
  context: 'Engineer',
  statement: 'Built Java services',
  skills: ['Java'],
  provenance: 'Synthetic restart test record',
  verified: true,
});
const form = new FormData();
form.set(
  'file',
  new Blob([readFileSync('backend/platform-resume/target/test-artifacts/resume.pdf')], {
    type: 'application/pdf',
  }),
  'resume.pdf',
);
const base = await (await request('/resumes', 'POST', form)).json();
const job = await (
  await request('/page-captures', 'POST', {
    url: 'https://careers.example.com/' + randomUUID(),
    title: 'Java Engineer',
    company: 'Acme',
    text: 'Hiring Java engineer. Remote Java services.',
    metadata: {},
  })
).json();
const task = await (
  await request('/resumes/tailor', 'POST', { resumeId: base.id, jobId: job.id })
).json();
for (let i = 0; i < 60; i++) {
  const state = await (await request('/tasks/' + task.id)).json();
  assert.notEqual(state.status, 'FAILED', state.lastError);
  if (state.status === 'COMPLETED') break;
  assert.ok(i < 59, 'Tailoring timed out');
  await delay(1000);
}
const versions = await (await request('/resumes/versions?jobId=' + job.id)).json();
assert.ok(versions.length);
const path = '/resumes/versions/' + versions[0].id + '/pdf';
const digest = async () =>
  createHash('sha256')
    .update(Buffer.from(await (await request(path)).arrayBuffer()))
    .digest('hex');
const before = await digest();
const compose = [
  'compose',
  '--env-file',
  '.env.test.example',
  '-f',
  'docker-compose.yml',
  '-f',
  'docker-compose.test.yml',
];
for (const args of [['restart'], ['up', '-d', '--wait', '--wait-timeout', '300']]) {
  const result = spawnSync('docker', [...compose, ...args], {
    stdio: 'inherit',
    env: { ...process.env, APP_ENV_FILE: '.env.test.example' },
  });
  assert.equal(result.status, 0, 'Test stack restart failed');
}
assert.equal((await (await request('/profile')).json()).identity.name, 'Restart Verified');
assert.equal(await digest(), before);
assert.equal((await (await request('/jobs/' + job.id)).json()).id, job.id);
// A deploy can replace upstream containers while Nginx stays alive. This must
// retain both routing and the existing session without restarting the proxy.
const replacement = spawnSync(
  'docker',
  [
    ...compose,
    'up',
    '-d',
    '--force-recreate',
    '--no-deps',
    '--wait',
    '--wait-timeout',
    '300',
    'job-platform-api',
    'job-platform-mcp',
  ],
  { stdio: 'inherit', env: { ...process.env, APP_ENV_FILE: '.env.test.example' } },
);
assert.equal(replacement.status, 0, 'Upstream replacement failed');
assert.equal((await (await request('/profile')).json()).identity.name, 'Restart Verified');
assert.equal(await digest(), before);
const mcp = await fetch(origin + '/mcp', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', Connection: 'close' },
  body: JSON.stringify({ jsonrpc: '2.0', id: 1, method: 'tools/list' }),
});
assert.equal(mcp.status, 401, 'MCP routing and authentication must survive replacement');
console.log(
  'Restart PASS: session, profile, canonical job and exact PDF bytes survive restart; API/MCP routing survives upstream replacement.',
);
