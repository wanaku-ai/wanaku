import { test, expect } from '@playwright/test';
import { PromptsPage } from '../pages/prompts.page';

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
});
