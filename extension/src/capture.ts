export interface Capture {
  url: string;
  title: string;
  text: string;
  company: string;
  metadata: Record<string, unknown>;
}
export function normalizeCapture(value: Capture): Capture {
  const url = new URL(value.url);
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password)
    throw new Error('Open a public job, hiring, or referral page first.');
  return {
    url: url.href.slice(0, 2048),
    title: value.title.trim().slice(0, 200),
    text: value.text
      .replace(/\u0000/g, '')
      .trim()
      .slice(0, 80000),
    company: value.company.trim().slice(0, 200),
    metadata: value.metadata,
  };
}
// This function is serialized by Chrome; it must not reference external variables.
export function extractCurrentPage(): Capture {
  const metadata: Record<string, unknown> = {};
  for (const item of Array.from(
    document.querySelectorAll('script[type="application/ld+json"]'),
  ).slice(0, 5)) {
    if ((item.textContent?.length ?? 0) > 30000) continue;
    try {
      metadata.jsonLd = JSON.parse(item.textContent ?? '{}');
      break;
    } catch {
      /* Malformed publisher metadata is optional, never executed. */
    }
  }
  return {
    url: location.href,
    title: document.title.slice(0, 200),
    text: document.body.innerText.slice(0, 80000),
    company: '',
    metadata,
  };
}
