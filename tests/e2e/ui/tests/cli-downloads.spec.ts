import { test, expect } from '@playwright/test';
import { CliDownloadsPage } from '../pages/cli-downloads.page';

const routerUrl = process.env.WANAKU_ROUTER_URL ?? 'http://localhost:8080';

test.describe('CLI Downloads', () => {
  let cliDownloads: CliDownloadsPage;

  test.beforeEach(async ({ page }) => {
    cliDownloads = new CliDownloadsPage(page, `${routerUrl}/admin/`);
    await cliDownloads.goto();
  });

  test('displays page title and description', async () => {
    const title = await cliDownloads.getPageTitle();
    expect(title).toBe('CLI Downloads');

    const description = await cliDownloads.getPageDescription();
    expect(description).toContain('Wanaku CLI');
  });

  test('distinguishes native and Java packages', async () => {
    await expect(cliDownloads.sectionHeading('Native Binaries')).toBeVisible();
    await expect(cliDownloads.sectionHeading('Java Packages')).toBeVisible();
  });

  test('provides a working download link for each package', async () => {
    const tile = cliDownloads.packageTile('Wanaku CLI').first();
    await expect(tile).toBeVisible();

    const link = cliDownloads.downloadLink(tile);
    await expect(link).toHaveAttribute('href', /.+/);
    await expect(link).toHaveAttribute('target', '_blank');
  });
});
