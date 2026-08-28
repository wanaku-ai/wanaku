import { test, expect } from '@playwright/test';
import { DownloadsPage } from '../pages/downloads.page';

const routerUrl = process.env.WANAKU_ROUTER_URL ?? 'http://localhost:8080';

test.describe('CLI Downloads', () => {
  let downloads: DownloadsPage;

  test.beforeEach(async ({ page }) => {
    downloads = new DownloadsPage(page, `${routerUrl}/admin/`);
  });

  test('displays page title', async () => {
    await downloads.goto();
    const title = await downloads.getPageTitle();
    expect(title).toBe('CLI Downloads');
  });

  test('lists CLI Downloads in the Developer menu', async ({ page }) => {
    await downloads.goto();
    const headerNavigation = page.getByRole('navigation', { name: 'Wanaku' });
    await headerNavigation.getByRole('link', { name: 'Developer', exact: true }).click();

    await expect(headerNavigation.getByRole('link', { name: 'CLI Downloads' })).toBeVisible();
  });

  test('lists CLI packages with download links', async () => {
    await downloads.goto();
    await expect(downloads.downloadButtons().first()).toBeVisible({ timeout: 5_000 });
    expect(await downloads.downloadButtons().count()).toBeGreaterThan(0);
  });

  test('links to the full release page', async () => {
    await downloads.goto();
    await expect(downloads.viewAllPackagesLink()).toBeVisible({ timeout: 5_000 });
  });
});
