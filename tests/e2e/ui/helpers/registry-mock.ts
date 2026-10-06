import { type Page } from '@playwright/test';

type RegistryKind = 'tools' | 'resources' | 'prompts';
type RegistryEntry = { name: string; enabled?: boolean } & Record<string, unknown>;

/**
 * Serves a mocked registry list for the Admin UI and applies enable/disable
 * calls to it. Forward-sourced entries need a live upstream MCP server, so
 * the specs mock the management API instead. Returns the received calls,
 * for example `disable:my-tool`.
 */
export async function mockRegistryEntries(
  page: Page,
  kind: RegistryKind,
  entries: RegistryEntry[],
): Promise<string[]> {
  const calls: string[] = [];
  const toggle = new RegExp(`/api/v1/${kind}/([^/]+)/(enable|disable)$`);

  await page.route(`**/api/v1/${kind}`, (route) => route.fulfill({ json: { data: entries } }));
  await page.route(toggle, (route) => {
    const [, encodedName, action] = route.request().url().match(toggle) ?? [];
    const name = decodeURIComponent(encodedName);
    const entry = entries.find((item) => item.name === name);
    if (!entry || route.request().method() !== 'PUT') {
      return route.fulfill({ status: 404, json: { data: null, error: `not found: ${name}` } });
    }
    entry.enabled = action === 'enable';
    calls.push(`${action}:${name}`);
    return route.fulfill({ json: { data: entry } });
  });

  return calls;
}
