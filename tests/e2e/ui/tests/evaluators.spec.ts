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

  test('LLM connection select is disabled with no connections configured', async () => {
    await evaluators.goto();
    await evaluators.clickAddEvaluator();

    const isDisabled = await evaluators.isConnectionSelectDisabled();
    expect(isDisabled).toBeTruthy();

    const hasHelperText = await evaluators.modalHasText('llm_connections');
    expect(hasHelperText).toBeTruthy();
  });
});
