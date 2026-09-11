let csrf = '';
export function setCsrf(value: string) {
  csrf = value;
}
export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
  ) {
    super(message);
  }
}
export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body && !(init.body instanceof FormData))
    headers.set('Content-Type', 'application/json');
  if (init.method && !['GET', 'HEAD'].includes(init.method)) {
    headers.set('X-CSRF-Token', csrf);
    if (!headers.has('Idempotency-Key')) headers.set('Idempotency-Key', crypto.randomUUID());
  }
  const response = await fetch('/api/v1' + path, { ...init, headers, credentials: 'include' });
  if (!response.ok) {
    let error: { code?: string; message?: string } = {};
    try {
      error = await response.json();
    } catch {}
    throw new ApiError(
      response.status,
      error.code ?? 'HTTP_ERROR',
      error.message ?? `Request failed (${response.status})`,
    );
  }
  if (response.status === 204 || response.headers.get('content-length') === '0')
    return undefined as T;
  const text = await response.text();
  return text ? (JSON.parse(text) as T) : (undefined as T);
}
export function post<T>(path: string, body: unknown = {}) {
  return api<T>(path, { method: 'POST', body: JSON.stringify(body) });
}
export async function download(id: string, format: 'pdf' | 'docx') {
  const response = await fetch(`/api/v1/resumes/versions/${id}/${format}`, {
    credentials: 'include',
  });
  if (!response.ok) throw new Error('Download failed');
  const url = URL.createObjectURL(await response.blob());
  const a = document.createElement('a');
  a.href = url;
  a.download = `resume-${id}.${format}`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
