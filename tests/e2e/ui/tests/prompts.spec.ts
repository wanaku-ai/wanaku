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

  test('paginates and filters prompts', async ({ page }) => {
    const entries = Array.from({ length: 30 }, (_, index) => ({
      ...promptData({ name: `e2e-paged-prompt-${String(index).padStart(2, '0')}` }),
      forwardId: 'e2e-forward',
      namespace: 'default',
      enabled: true,
    }));
    await mockRegistryEntries(page, 'prompts', entries);

    await prompts.goto();

    await expect(prompts.tableRows()).toHaveCount(25);
    await expect(prompts.pagination()).toContainText('1–25 of 30 items');

    await prompts.goToNextPage();
    await expect(prompts.tableRows()).toHaveCount(5);
    await expect(prompts.rowWithText('e2e-paged-prompt-29')).toBeVisible();

    await prompts.searchTable('prompt-07');
    await expect(prompts.tableRows()).toHaveCount(1);
    await expect(prompts.rowWithText('e2e-paged-prompt-07')).toBeVisible();

    await prompts.searchTable('no-such-prompt');
    await expect(prompts.tableRows()).toHaveCount(1);
    await expect(prompts.tableRows()).toContainText('No matching prompts');
  });
});
