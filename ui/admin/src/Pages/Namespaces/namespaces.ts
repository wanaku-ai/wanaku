import type { NamespaceEntry } from '../../models';

export function sortedNamespaces(namespaces: readonly NamespaceEntry[]): NamespaceEntry[] {
  const result = [...namespaces];
  result.sort((a, b) => {
    if (a.name === 'default') return -1;
    if (b.name === 'default') return 1;
    if (a.name === 'public') return -1;
    if (b.name === 'public') return 1;
    return (a.name ?? '').localeCompare(b.name ?? '');
  });
  return result;
}

/**
 * Returns the MCP address of a namespace.
 *
 * Uses the public MCP base URL from the server (`WANAKU_MCP_PUBLIC_URL`) when it is set.
 * Otherwise, it uses the host of the admin UI with port 8081, which fits a local deployment.
 */
export function mcpAddress(
  name: string | undefined,
  mcpPublicUrl: string | null | undefined,
  location: Pick<Location, 'protocol' | 'hostname'>,
): string {
  const base = mcpPublicUrl
    ? mcpPublicUrl.replace(/\/+$/, '')
    : `${location.protocol}//${location.hostname}:8081`;
  return `${base}/${name || 'default'}/mcp`;
}
