import { test, expect } from '@playwright/test';
import { DashboardPage } from '../pages/dashboard.page';

const routerUrl = process.env.WANAKU_ROUTER_URL ?? 'http://localhost:8080';

test.describe('Dashboard', () => {
  let dashboard: DashboardPage;

  test.beforeEach(async ({ page }) => {
    dashboard = new DashboardPage(page, `${routerUrl}/admin/`);
    await dashboard.goto();
  });

  test('displays page title and description', async () => {
    const title = await dashboard.getPageTitle();
    expect(title).toBe('Dashboard');

    const description = await dashboard.getPageDescription();
    expect(description).toContain('System overview');
  });

  test('displays statistics tiles with numeric values', async ({ page }) => {
    const labels = ['Tools', 'Resources', 'Prompts', 'Forwards', 'Data Stores'];
    for (const label of labels) {
      const tile = dashboard.statTile(label);
      await expect(tile).toBeVisible();

      const value = await dashboard.getStatValue(label);
      expect(Number(value)).toBeGreaterThanOrEqual(0);
    }
  });

  test('displays capability sections', async ({ page }) => {
    await expect(dashboard.capabilityTile('Tool Capabilities')).toBeVisible();
    await expect(dashboard.capabilityTile('Resource Capabilities')).toBeVisible();

    for (const tile of ['Tool Capabilities', 'Resource Capabilities']) {
      for (const label of ['Total', 'Healthy', 'Down']) {
        const value = await dashboard.getCapabilityValue(tile, label);
        expect(Number(value)).toBeGreaterThanOrEqual(0);
      }
    }
  });

  test('refresh button reloads statistics without error', async ({ page }) => {
    await dashboard.clickRefresh();
    await expect(dashboard.errorNotification()).toBeHidden();
  });
});
