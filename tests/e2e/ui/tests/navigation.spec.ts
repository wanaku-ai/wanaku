import { test, expect } from '@playwright/test';

const routerUrl = process.env.WANAKU_ROUTER_URL ?? 'http://localhost:8080';

test.describe('Navigation', () => {
  test('sorts the Admin menu alphabetically', async ({ page }) => {
    await page.goto(`${routerUrl}/admin/`);
    const headerNavigation = page.getByRole('navigation', { name: 'Wanaku' });
    const adminMenu = headerNavigation.locator('li', {
      has: page.getByRole('link', { name: 'Admin', exact: true }),
    });
    await adminMenu.getByRole('link', { name: 'Admin', exact: true }).click();

    const items = adminMenu.getByRole('menuitem');
    await expect(items.first()).toBeVisible();
    const labels = (await items.allTextContents()).map((label) => label.trim());

    expect(labels).toContain('Audit');
    expect(labels).toEqual([...labels].sort((a, b) => a.localeCompare(b)));
  });
});
