import { test, expect } from '@playwright/test';
import { ToolsPage } from '../pages/tools.page';
import { toolData } from '../helpers/test-data';
import { mockRegistryEntries } from '../helpers/registry-mock';
import { Carbon } from '../helpers/carbon';

const routerUrl = process.env.WANAKU_ROUTER_URL ?? 'http://localhost:8080';

test.describe('Tools', () => {
  let tools: ToolsPage;

  test.beforeEach(async ({ page }) => {
    tools = new ToolsPage(page, `${routerUrl}/admin/`);
  });

  test('displays page title', async () => {
    await tools.goto();
    const title = await tools.getPageTitle();
    expect(title).toBe('Tools');
  });

  test('tool can be disabled and enabled again', async ({ page }) => {
    const entry = { ...toolData({ type: 'mcp-forward' }), forwardId: 'e2e-forward', namespace: 'default', enabled: true };
    const calls = await mockRegistryEntries(page, 'tools', [entry]);

    await tools.goto();

    const toggle = tools.enabledSwitch(entry.name);
    await expect(toggle).toHaveAttribute('aria-checked', 'true');

    await tools.clickEnabledToggle(entry.name);
    await expect(toggle).toHaveAttribute('aria-checked', 'false');
    await expect(tools.rowWithText(entry.name)).toContainText('Disabled');

    await tools.clickEnabledToggle(entry.name);
    await expect(toggle).toHaveAttribute('aria-checked', 'true');
    expect(calls).toEqual([`disable:${entry.name}`, `enable:${entry.name}`]);
  });

  test('same tool name in two namespaces is toggled per namespace', async ({ page }) => {
    const base = toolData({ type: 'mcp-forward' });
    const entries = [
      { ...base, forwardId: 'e2e-forward-a', namespace: 'default', enabled: true },
      { ...base, forwardId: 'e2e-forward-b', namespace: 'e2e-finance', enabled: true },
    ];
    const calls = await mockRegistryEntries(page, 'tools', entries);

    await tools.goto();

    await expect(tools.rowWithText(base.name)).toHaveCount(2);
    const financeRow = tools.rowInNamespace(base.name, 'e2e-finance');
    await financeRow.locator(Carbon.toggleAppearance).click();
    await expect(financeRow.getByRole('switch')).toHaveAttribute('aria-checked', 'false');
    await expect(
      tools.rowInNamespace(base.name, 'default').getByRole('switch'),
    ).toHaveAttribute('aria-checked', 'true');
    expect(calls).toEqual([`disable:e2e-finance/${base.name}`]);
  });

  test('paginates and filters tools', async ({ page }) => {
    const entries = Array.from({ length: 30 }, (_, index) => ({
      ...toolData({ name: `e2e-paged-tool-${String(index).padStart(2, '0')}`, type: 'mcp-forward' }),
      forwardId: 'e2e-forward',
      namespace: 'default',
      enabled: true,
    }));
    await mockRegistryEntries(page, 'tools', entries);

    await tools.goto();

    await expect(tools.tableRows()).toHaveCount(25);
    await expect(tools.pagination()).toContainText('1–25 of 30 items');

    await tools.goToNextPage();
    await expect(tools.tableRows()).toHaveCount(5);
    await expect(tools.rowWithText('e2e-paged-tool-29')).toBeVisible();

    await tools.searchTable('tool-07');
    await expect(tools.tableRows()).toHaveCount(1);
    await expect(tools.rowWithText('e2e-paged-tool-07')).toBeVisible();

    await tools.searchTable('no-such-tool');
    await expect(tools.tableRows()).toHaveCount(1);
    await expect(tools.tableRows()).toContainText('No matching tools');
  });
});
