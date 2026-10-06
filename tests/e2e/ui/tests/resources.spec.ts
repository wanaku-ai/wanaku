import { test, expect } from '@playwright/test';
import { ResourcesPage } from '../pages/resources.page';
import { resourceData } from '../helpers/test-data';
import { mockRegistryEntries } from '../helpers/registry-mock';

const routerUrl = process.env.WANAKU_ROUTER_URL ?? 'http://localhost:8080';

test.describe('Resources', () => {
  let resources: ResourcesPage;

  test.beforeEach(async ({ page }) => {
    resources = new ResourcesPage(page, `${routerUrl}/admin/`);
  });

  test('displays page title', async () => {
    await resources.goto();
    const title = await resources.getPageTitle();
    expect(title).toBe('Resources');
  });

  test('resource can be disabled and enabled again', async ({ page }) => {
    const entry = { ...resourceData({ type: 'mcp-forward' }), forwardId: 'e2e-forward', namespace: 'default', enabled: true };
    const calls = await mockRegistryEntries(page, 'resources', [entry]);

    await resources.goto();

    const toggle = resources.enabledSwitch(entry.name);
    await expect(toggle).toHaveAttribute('aria-checked', 'true');

    await resources.clickEnabledToggle(entry.name);
    await expect(toggle).toHaveAttribute('aria-checked', 'false');
    await expect(resources.rowWithText(entry.name)).toContainText('Disabled');

    await resources.clickEnabledToggle(entry.name);
    await expect(toggle).toHaveAttribute('aria-checked', 'true');
    expect(calls).toEqual([`disable:${entry.name}`, `enable:${entry.name}`]);
  });
});
