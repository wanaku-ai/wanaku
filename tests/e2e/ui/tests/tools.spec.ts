import { test, expect } from '@playwright/test';
import { ToolsPage } from '../pages/tools.page';

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
});
