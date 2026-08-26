import { test, expect } from '@playwright/test';
import { EvaluatorsPage } from '../pages/evaluators.page';

const routerUrl = process.env.WANAKU_ROUTER_URL ?? 'http://localhost:8080';

test.describe('Evaluators', () => {
  let evaluators: EvaluatorsPage;

  test.beforeEach(async ({ page }) => {
    evaluators = new EvaluatorsPage(page, `${routerUrl}/admin/`);
  });

  test('displays page title', async () => {
    await evaluators.goto();
    const title = await evaluators.getPageTitle();
    expect(title).toBe('Evaluators');
  });

  test('LLM connection select is enabled and populated from configured connections', async () => {
    // The e2e server loads the repository wanaku.yaml, which configures
    // llm_connections. The select must therefore be enabled and list them.
    await evaluators.goto();
    await evaluators.clickAddEvaluator();

    const isDisabled = await evaluators.isConnectionSelectDisabled();
    expect(isDisabled).toBeFalsy();

    const options = await evaluators.connectionOptionValues();
    expect(options).toContain('local-llama');
  });
});
