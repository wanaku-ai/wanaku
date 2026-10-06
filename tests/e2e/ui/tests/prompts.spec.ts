import { test, expect } from '@playwright/test';
import { PromptsPage } from '../pages/prompts.page';
import { promptData } from '../helpers/test-data';
import { mockRegistryEntries } from '../helpers/registry-mock';

const routerUrl = process.env.WANAKU_ROUTER_URL ?? 'http://localhost:8080';

test.describe('Prompts', () => {
  let prompts: PromptsPage;

  test.beforeEach(async ({ page }) => {
    prompts = new PromptsPage(page, `${routerUrl}/admin/`);
  });

  test('displays page title', async () => {
    await prompts.goto();
    const title = await prompts.getPageTitle();
    expect(title).toBe('Prompts');
  });

  test('prompt can be disabled and enabled again', async ({ page }) => {
    const entry = { ...promptData(), forwardId: 'e2e-forward', namespace: 'default', enabled: true };
    const calls = await mockRegistryEntries(page, 'prompts', [entry]);

    await prompts.goto();

    const toggle = prompts.enabledSwitch(entry.name);
    await expect(toggle).toHaveAttribute('aria-checked', 'true');

    await prompts.clickEnabledToggle(entry.name);
    await expect(toggle).toHaveAttribute('aria-checked', 'false');
    await expect(prompts.rowWithText(entry.name)).toContainText('Disabled');

    await prompts.clickEnabledToggle(entry.name);
    await expect(toggle).toHaveAttribute('aria-checked', 'true');
    expect(calls).toEqual([`disable:${entry.name}`, `enable:${entry.name}`]);
  });
});
