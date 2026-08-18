import { test, expect } from '@playwright/test';
import { ResourcesPage } from '../pages/resources.page';

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
});
