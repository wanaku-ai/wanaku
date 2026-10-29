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

  test('paginates and filters resources', async ({ page }) => {
    const entries = Array.from({ length: 30 }, (_, index) => ({
      ...resourceData({ name: `e2e-paged-resource-${String(index).padStart(2, '0')}`, type: 'mcp-forward' }),
      forwardId: 'e2e-forward',
      namespace: 'default',
      enabled: true,
    }));
    await mockRegistryEntries(page, 'resources', entries);

    await resources.goto();

    await expect(resources.tableRows()).toHaveCount(25);
    await expect(resources.pagination()).toContainText('1–25 of 30 items');

    await resources.goToNextPage();
    await expect(resources.tableRows()).toHaveCount(5);
    await expect(resources.rowWithText('e2e-paged-resource-29')).toBeVisible();

    await resources.searchTable('resource-07');
    await expect(resources.tableRows()).toHaveCount(1);
    await expect(resources.rowWithText('e2e-paged-resource-07')).toBeVisible();

    await resources.searchTable('no-such-resource');
    await expect(resources.tableRows()).toHaveCount(1);
    await expect(resources.tableRows()).toContainText('No matching resources');
  });
});
