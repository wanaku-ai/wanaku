export function isHttpUrl(value: string): boolean {
  const parts = /^https?:\/\/([^/?#]+)(\/[^?#]*)?(?:\?[^#]*)?$/i.exec(value);
  if (!parts || /[\s\\]/.test(value)) return false;

  const [, authority, path = ''] = parts;
  try {
    const url = new URL(value);
    // Inspect the original path because URL normalizes traversal segments.
    return (
      Boolean(url.hostname) &&
      !authority.includes('@') &&
      !authority.endsWith(':') &&
      url.port !== '0' &&
      !path.startsWith('//') &&
      !/(?:^|\/)(?:\.|%2e){2}(?:\/|$)/i.test(path)
    );
  } catch {
    return false;
  }
}
