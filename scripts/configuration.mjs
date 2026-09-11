import { Buffer } from 'node:buffer';

/** Pure, secret-redacting configuration validation shared by preflight and deterministic tests. */
export function validateConfiguration(values, { demo = false } = {}) {
  const provider = values.LLM_PROVIDER || 'gemini';
  if (!['gemini', 'openai', 'claude'].includes(provider))
    throw new Error('LLM_PROVIDER must be gemini, openai or claude.');
  if (values.LLM_FALLBACK_PROVIDERS)
    throw new Error('Automatic provider fallback is disabled in V1; clear LLM_FALLBACK_PROVIDERS.');
  if (demo) return provider;
  if (values.APP_MODE !== 'production')
    throw new Error(
      'Production startup requires APP_MODE=production; use --demo explicitly for isolated tests.',
    );
  const missing = [],
    need = (key) => {
      if (!values[key] || /PROJECT|example\.com|CHANGE_ME/.test(values[key])) missing.push(key);
    };
  for (const key of [
    'APP_PUBLIC_URL',
    'SUPABASE_DB_URL',
    'SUPABASE_DB_USER',
    'SUPABASE_DB_PASSWORD',
    'SUPABASE_URL',
    'SUPABASE_ANON_KEY',
    'SUPABASE_SERVICE_ROLE_KEY',
    'STORAGE_BUCKET',
    'ENCRYPTION_MASTER_KEY',
  ])
    need(key);
  const prefix = provider === 'claude' ? 'CLAUDE' : provider.toUpperCase();
  need(provider === 'claude' ? 'ANTHROPIC_API_KEY' : prefix + '_API_KEY');
  for (const tier of ['CHEAP', 'MEDIUM', 'HIGH']) need(prefix + '_MODEL_' + tier);
  if (missing.length)
    throw new Error(
      'Missing configuration:\n- ' +
        missing.join('\n- ') +
        '\nSee docs/configuration.md and docs/ai-provider-setup.md.',
    );
  const origin = new URL(values.APP_PUBLIC_URL);
  if (
    origin.protocol !== 'https:' ||
    origin.username ||
    origin.password ||
    origin.pathname !== '/' ||
    origin.search ||
    origin.hash
  )
    throw new Error('APP_PUBLIC_URL requires a bare HTTPS origin in production.');
  if (
    !values.SUPABASE_DB_URL.includes('sslmode=verify-full') ||
    /\/\/(localhost|127\.0\.0\.1|postgres|db)[:/]/i.test(values.SUPABASE_DB_URL)
  )
    throw new Error('Use remote Supabase PostgreSQL with sslmode=verify-full.');
  const cipher = Buffer.from(values.ENCRYPTION_MASTER_KEY, 'base64');
  if (cipher.length !== 32 || cipher.every((n) => n === 0))
    throw new Error('ENCRYPTION_MASTER_KEY must encode 32 random bytes; do not use the test key.');
  for (const name of ['GMAIL', 'MICROSOFT'])
    if (Boolean(values[name + '_CLIENT_ID']) !== Boolean(values[name + '_CLIENT_SECRET']))
      throw new Error(`Configure both ${name}_CLIENT_ID and ${name}_CLIENT_SECRET, or neither.`);
  return provider;
}
