#!/usr/bin/env node
import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync, realpathSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { validateConfiguration } from './configuration.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
process.chdir(root);
const args = process.argv.slice(2),
  command = args[0] ?? 'bootstrap',
  demo = args.includes('--demo');
const expected = 'https://github.com/akshatjain04/job-search-platform';
const option = (key, fallback) =>
  args.find((v) => v.startsWith(key + '='))?.slice(key.length + 1) ?? fallback;
const envFile = resolve(root, option('--env', demo ? '.env.test.example' : '.env'));
const environment = { ...process.env };
function exec(
  program,
  parameters = [],
  { capture = false, cwd = root, allowFailure = false } = {},
) {
  const actual =
    process.platform === 'win32' && ['npm', 'mvn'].includes(program) ? program + '.cmd' : program;
  const result = spawnSync(actual, parameters, {
    cwd,
    env: environment,
    encoding: 'utf8',
    stdio: capture ? 'pipe' : 'inherit',
    shell: process.platform === 'win32' && actual.endsWith('.cmd'),
  });
  if (result.error || result.status !== 0) {
    if (allowFailure) return null;
    throw new Error(
      `${program} failed${result.error ? ': ' + result.error.message : ' (exit ' + result.status + ')'}`,
    );
  }
  return capture ? (result.stdout + result.stderr).trim() : '';
}
function requireTool(name) {
  if (exec(name, ['--version'], { capture: true, allowFailure: true }) === null)
    throw new Error(`Install ${name}, add it to PATH, then rerun. See docs/local-development.md.`);
}
function readEnv() {
  if (!existsSync(envFile))
    throw new Error(
      `Missing ${demo ? '.env.test.example' : '.env'}. Copy .env.example to .env and configure selected integrations; see docs/configuration.md.`,
    );
  const values = {};
  for (const [index, line] of readFileSync(envFile, 'utf8').split(/\r?\n/).entries()) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const match = trimmed.match(/^([A-Z][A-Z0-9_]*)=(.*)$/);
    if (!match)
      throw new Error(
        `Invalid environment syntax at line ${index + 1}; use KEY=value, not shell commands.`,
      );
    let value = match[2].trim();
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    )
      value = value.slice(1, -1);
    values[match[1]] = value;
  }
  Object.assign(environment, values);
  environment.APP_ENV_FILE = envFile;
  return values;
}
function repository() {
  const actual = exec('git', ['rev-parse', '--show-toplevel'], { capture: true });
  if (realpathSync(actual).toLowerCase() !== realpathSync(root).toLowerCase())
    throw new Error('Run inside the intended repository root.');
  if (exec('git', ['remote', 'get-url', 'origin'], { capture: true }) !== expected)
    throw new Error(`origin must equal ${expected}`);
  if (
    exec('git', ['config', '--local', 'user.email'], { capture: true }) !==
    'akshatjain0410@gmail.com'
  )
    throw new Error(
      'Repository-local user.email must be akshatjain0410@gmail.com. Complete secure Git bootstrap first.',
    );
  if (!exec('git', ['config', '--local', 'user.name'], { capture: true }))
    throw new Error('Configure a repository-local user.name.');
}
function configuration() {
  const values = readEnv();
  validateConfiguration(values, { demo });
  return values;
}
const composeFiles = () => [
  '--env-file',
  envFile,
  '-f',
  'docker-compose.yml',
  '-f',
  demo ? 'docker-compose.test.yml' : 'docker-compose.prod.yml',
];
function compose(...parameters) {
  return exec('docker', ['compose', ...composeFiles(), ...parameters]);
}
function toolsCheck(build) {
  if (!['x64', 'arm64'].includes(process.arch))
    throw new Error('Supported host architectures: x64 and arm64.');
  if (Number(process.versions.node.split('.')[0]) < 22)
    throw new Error('Install Node.js 22 or newer.');
  requireTool('docker');
  if (exec('docker', ['info', '--format', '{{.OSType}}'], { capture: true }) !== 'linux')
    throw new Error('Switch Docker Desktop to Linux containers.');
  const version = exec('docker', ['compose', 'version', '--short'], { capture: true })
    .replace(/^v/, '')
    .split('.')
    .map(Number);
  if (version[0] < 2 || (version[0] === 2 && version[1] < 24))
    throw new Error('Docker Compose 2.24.4+ is required for safe production port overrides.');
  if (build) {
    requireTool('npm');
    const java = exec('java', ['-XshowSettings:properties', '-version'], { capture: true });
    const major = java.match(/java\.version\s*=\s*(\d+)/)?.[1];
    if (!major || Number(major) < 21)
      throw new Error('Install a Java 21+ JDK and put java on PATH.');
    const javaHome = java.match(/java\.home\s*=\s*(.+)/)?.[1]?.trim();
    if (javaHome) environment.JAVA_HOME = javaHome;
    requireTool('mvn');
  }
}
function preflight(build = true) {
  if (!args.includes('--deployment')) repository();
  const configured = configuration();
  if (!demo && !args.includes('--build-host')) {
    const tls = resolve(root, configured.TLS_DIRECTORY || '.local/tls');
    for (const file of ['fullchain.pem', 'privkey.pem'])
      if (!existsSync(resolve(tls, file)))
        throw new Error(
          'Missing TLS file ' + file + ' in TLS_DIRECTORY; see docs/ec2-deployment.md.',
        );
  }
  toolsCheck(build);
  if (!args.includes('--deployment')) compose('config', '--quiet');
  console.log(
    `Preflight passed: ${process.platform}/${process.arch}; ${demo ? 'isolated, non-sending test mode' : 'production / ' + (environment.LLM_PROVIDER || 'gemini')}.`,
  );
}
function build(test = true) {
  if (test) exec('node', ['--test', 'scripts/configuration.test.mjs']);
  const maven = ['-f', 'backend/pom.xml', 'clean', 'verify', '-B', '-ntp'];
  if (!test) maven.push('-DskipTests');
  if (process.platform === 'win32' && existsSync('C:/Windows/Fonts/arial.ttf'))
    maven.push('-Dmyjobai.resume.font=C:/Windows/Fonts/arial.ttf');
  exec('mvn', maven);
  for (const part of ['web', 'extension']) {
    exec('npm', ['ci', '--no-audit', '--no-fund'], { cwd: resolve(root, part) });
    exec('npm', ['run', test ? 'verify' : 'build'], { cwd: resolve(root, part) });
  }
}
async function smoke() {
  const url = demo ? 'http://localhost:8080' : environment.APP_PUBLIC_URL;
  for (const [path, status] of [
    ['/healthz', 200],
    ['/api-health', 200],
    ['/', 200],
    ['/api/v1/jobs', 401],
  ]) {
    let response;
    for (let attempt = 0; attempt < 20; attempt++) {
      try {
        response = await fetch(url + path, {
          signal: AbortSignal.timeout(5000),
          redirect: 'manual',
        });
        if (response.status === status) break;
      } catch {}
      await delay(1000);
    }
    if (response?.status !== status)
      throw new Error(
        `Smoke check ${path}: expected ${status}, got ${response?.status ?? 'unreachable'}`,
      );
  }
  const mcp = await fetch(url + '/mcp', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ jsonrpc: '2.0', id: 1, method: 'tools/list' }),
    signal: AbortSignal.timeout(10000),
  });
  if (mcp.status !== 401) throw new Error('Unauthenticated MCP must return 401.');
  console.log('Smoke PASS: web, API readiness, authentication and MCP protection.');
}
function summary() {
  const url = demo ? 'http://localhost:8080' : environment.APP_PUBLIC_URL;
  console.log(
    `Web: ${url}\nAPI: ${url}/api/v1\nOpenAPI (authenticated): ${url}/v3/api-docs\nHealth: ${url}/api-health\nMCP: ${url}/mcp\nExtension: extension/dist or ${url}/myjobai-extension.zip\n${demo ? 'Test login: enter an email on the dashboard. No real email or billable AI calls.' : 'Next: sign in through Supabase; configure profile, verified facts and a base resume.'}`,
  );
}
try {
  switch (command) {
    case 'preflight':
      preflight(!args.includes('--runtime'));
      break;
    case 'build':
    case 'test':
      preflight(true);
      build(!args.includes('--skip-tests'));
      break;
    case 'bootstrap':
      preflight(true);
      build(!args.includes('--skip-tests'));
      compose('build', 'job-platform-api', 'web');
      compose('up', '-d', '--wait', '--wait-timeout', '300');
      await smoke();
      summary();
      break;
    case 'start':
      preflight(false);
      compose('up', '-d', '--wait', '--wait-timeout', '300');
      await smoke();
      summary();
      break;
    case 'stop':
      repository();
      readEnv();
      compose('stop');
      console.log('Stopped containers. Database and object volumes preserved.');
      break;
    case 'smoke':
      configuration();
      await smoke();
      summary();
      break;
    case 'logs':
      repository();
      readEnv();
      compose('logs', '--tail', '100');
      break;
    default:
      throw new Error(
        'Commands: preflight, build, test, bootstrap, start, stop, smoke, logs. Options: --demo, --skip-tests, --env=PATH.',
      );
  }
} catch (error) {
  console.error('\nBOOTSTRAP / OPERATION BLOCKED\n' + error.message);
  console.error(
    'Check docs/troubleshooting.md. Container diagnostics: docker compose logs --tail 100 (with the same environment and Compose files).',
  );
  process.exitCode = 1;
}
