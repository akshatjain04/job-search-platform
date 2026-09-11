import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validateConfiguration } from './configuration.mjs';

const base = {
  APP_MODE: 'production',
  APP_PUBLIC_URL: 'https://jobs.mycompany.test',
  SUPABASE_DB_URL: 'jdbc:postgresql://db.myproject.supabase.co:5432/postgres?sslmode=verify-full',
  SUPABASE_DB_USER: 'postgres',
  SUPABASE_DB_PASSWORD: 'test-only',
  SUPABASE_URL: 'https://myproject.supabase.co',
  SUPABASE_ANON_KEY: 'test-only',
  SUPABASE_SERVICE_ROLE_KEY: 'test-only',
  STORAGE_BUCKET: 'resumes',
  ENCRYPTION_MASTER_KEY: Buffer.alloc(32, 7).toString('base64'),
};
function selected(provider) {
  const prefix = provider === 'claude' ? 'CLAUDE' : provider.toUpperCase();
  return {
    ...base,
    LLM_PROVIDER: provider,
    [provider === 'claude' ? 'ANTHROPIC_API_KEY' : prefix + '_API_KEY']: 'test-only',
    [prefix + '_MODEL_CHEAP']: 'fixture-cheap',
    [prefix + '_MODEL_MEDIUM']: 'fixture-medium',
    [prefix + '_MODEL_HIGH']: 'fixture-high',
  };
}
test('each selected provider requires only its own credentials and model tiers', () => {
  for (const provider of ['gemini', 'openai', 'claude'])
    assert.equal(validateConfiguration(selected(provider)), provider);
  const defaults = selected('gemini');
  delete defaults.LLM_PROVIDER;
  assert.equal(validateConfiguration(defaults), 'gemini');
});
test('missing and unknown provider diagnostics are actionable and redact values', () => {
  assert.throws(() => validateConfiguration({ ...base, LLM_PROVIDER: 'unknown' }), /LLM_PROVIDER/);
  for (const provider of ['gemini', 'openai', 'claude']) {
    const config = selected(provider),
      key = provider === 'claude' ? 'ANTHROPIC_API_KEY' : provider.toUpperCase() + '_API_KEY';
    delete config[key];
    assert.throws(
      () => validateConfiguration(config),
      (error) => error.message.includes(key) && !error.message.includes('test-only'),
    );
  }
});
test('unsafe deployment modes, local persistence and silent fallback are rejected', () => {
  for (const invalid of [
    { APP_MODE: 'test' },
    { SUPABASE_DB_URL: 'jdbc:postgresql://localhost/db?sslmode=verify-full' },
    { APP_PUBLIC_URL: 'http://jobs.mycompany.test' },
    { LLM_FALLBACK_PROVIDERS: 'openai' },
    { ENCRYPTION_MASTER_KEY: Buffer.alloc(32).toString('base64') },
  ])
    assert.throws(() => validateConfiguration({ ...selected('gemini'), ...invalid }));
  assert.equal(validateConfiguration({}, { demo: true }), 'gemini');
});
