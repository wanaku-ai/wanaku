import { test, expect } from '@playwright/test';
import { ToolsPage } from '../pages/tools.page';
import { toolData } from '../helpers/test-data';
import { mockRegistryEntries } from '../helpers/registry-mock';

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
});
