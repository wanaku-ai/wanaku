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

  test('TypeSafe System One engine displays Noul configuration', async () => {
    await evaluators.goto();
    await evaluators.clickAddEvaluator();
    await evaluators.selectEngine('typesafe-system-one');

    await expect(evaluators.systemOneConnectionInput()).toBeVisible();
    expect(await evaluators.modalHasText('Noul Primitive ID')).toBeTruthy();
    expect(await evaluators.modalHasText('State Mapping')).toBeTruthy();
  });

  test('TypeSafe System One criteria must be supplied as a pair', async () => {
    await evaluators.goto();
    await evaluators.clickAddEvaluator();
    await evaluators.selectEngine('typesafe-system-one');

    const modal = evaluators.modal();
    await modal.locator('#evaluator-name').fill('typesafe-criteria-test');
    await evaluators.systemOneConnectionInput().fill('typesafe');
    await modal.locator('#system-one-noul-instructions').fill('Is this request safe?');
    await modal.locator('#processor-path').fill('actions/dist/safety_review_action.wasm');
    await modal.locator('#system-one-noul-true').fill('The request is safe.');

    await expect(modal.locator('.cds--modal-footer .cds--btn--primary')).toBeDisabled();
    await expect(modal).toContainText('Set both true and false criteria, or leave both empty');
  });

});
