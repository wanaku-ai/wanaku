import { test, expect } from '@playwright/test';

// Chat belongs to the optional Debugger plugin. The host must not reserve its route.
test('does not expose LLM Chat in the core UI without the Debugger plugin', async ({ page }) => {
  await page.route('**/api/v1/plugins', (route) =>
    route.fulfill({ json: { data: [], error: null } }),
  );
  await page.goto('./#/');
  const navigation = page.getByRole('navigation', { name: 'Wanaku' });
  await expect(navigation).toBeVisible();
  await expect(navigation.locator('a[href*="llmchat"]')).toHaveCount(0);
  await page.goto('./#/llmchat');
  await expect(page.getByRole('heading', { name: 'LLM Chat for testing' })).toHaveCount(0);
  await expect(page.getByText('Page not found.', { exact: true })).toBeVisible();
});
