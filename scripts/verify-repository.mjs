import { execFileSync } from 'node:child_process';
import { readFileSync, existsSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
process.chdir(root);
const files = execFileSync(
  'git',
  ['ls-files', '--cached', '--others', '--exclude-standard', '-z'],
  { encoding: 'utf8' },
)
  .split('\0')
  .filter(Boolean);
const errors = [],
  warnings = [];
for (const file of files) {
  if (!existsSync(file) || file.endsWith('.docx')) continue;
  const text = readFileSync(file, 'utf8');
  if (file.endsWith('.md')) {
    for (const match of text.matchAll(/\[[^\]]*\]\(([^\s)]+)\)/g)) {
      const target = match[1].split('#')[0];
      if (!target || /^(?:[a-z]+:|\/)/i.test(target)) continue;
      if (!existsSync(resolve(dirname(file), decodeURIComponent(target))))
        errors.push('Broken documentation link in ' + file + ': ' + target);
    }
  }
  if (/(^|\/)\.env($|\.)/.test(file) && !file.endsWith('.example'))
    errors.push('Tracked/unignored environment secret file: ' + file);
  if (
    /(?:-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|gh[pousr]_[A-Za-z0-9]{30,}|sk-proj-[A-Za-z0-9_-]{25,}|AIza[0-9A-Za-z_-]{30,})/.test(
      text,
    )
  )
    errors.push('Potential credential material: ' + file);
  if (
    /\/src\/main\//.test(file) &&
    /\b(TODO|FIXME|NotImplementedException|UnsupportedOperationException)\b/.test(text)
  )
    errors.push('Incomplete production implementation marker: ' + file);
  if (
    /\/src\/main\//.test(file) &&
    /\b(mock|fake|placeholder)\b/i.test(text) &&
    !/(TestProvider|TestMailbox)/.test(file)
  )
    warnings.push('Review test/placeholder term: ' + file);
  if (
    /platform-(domain|application)\/src\/main/.test(file) &&
    /import (com\.google|com\.openai|com\.anthropic|io\.myjobai\.(ai|mail|persistence|storage|connectors))\./.test(
      text,
    )
  )
    errors.push('Infrastructure dependency leaked into core: ' + file);
  if (
    /pom\.xml$|package\.json$|docker-compose.*\.yml$/.test(file) &&
    /(<artifactId>(?:.*redis.*|.*kafka.*|.*opensearch.*)|image:\s*(?:redis|.*kafka|.*opensearch)|"(?:ioredis|kafkajs|@aws-sdk\/client-sqs)"\s*:)/i.test(
      text,
    )
  )
    errors.push('Prohibited V1 infrastructure dependency: ' + file);
}
for (const path of [
  'docs/MASTER_IMPLEMENTATION_SPEC.md',
  'docs/IMPLEMENTATION_STATUS.md',
  'docs/ACCEPTANCE_CRITERIA.md',
  'docs/reference/ai_job_search_platform_architecture_cost_optimized.docx',
])
  if (!existsSync(path)) errors.push('Missing source of truth: ' + path);
const manifest = JSON.parse(readFileSync('extension/public/manifest.json', 'utf8'));
if (
  manifest.manifest_version !== 3 ||
  manifest.background ||
  manifest.content_scripts ||
  manifest.host_permissions
)
  errors.push(
    'Extension must remain MV3 and user-triggered, without mandatory broad host access or background crawling.',
  );
const production =
  readFileSync('docker-compose.yml', 'utf8') + readFileSync('docker-compose.prod.yml', 'utf8');
if (/image:\s*postgres|5432:5432/.test(production))
  errors.push('Production must not contain local PostgreSQL.');
console.log(`Repository checks: ${files.length} files inspected; ${errors.length} errors.`);
for (const warning of warnings) console.log('REVIEW: ' + warning);
for (const error of errors) console.error('ERROR: ' + error);
process.exitCode = errors.length ? 1 : 0;
