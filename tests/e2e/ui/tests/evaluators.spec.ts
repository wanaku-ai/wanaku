import { test, expect } from '@playwright/test';
import { EvaluatorsPage } from '../pages/evaluators.page';
import { ApiHelper } from '../helpers/api-helpers';
import { evaluatorData, opaEvaluatorData } from '../helpers/test-data';

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
    await expect(evaluators.modal().locator('#on-error')).toHaveCount(0);

    const isDisabled = await evaluators.isConnectionSelectDisabled();
    expect(isDisabled).toBeFalsy();

    const options = await evaluators.connectionOptionValues();
    expect(options).toContain('local-llama');
  });

  test('deletes an evaluator without a legacy error policy', async ({ request }) => {
    const api = new ApiHelper(request, routerUrl);
    const original = await api.getEvaluators();
    const evaluator = evaluatorData();
    try {
      await api.setEvaluators([...original, evaluator]);
      await evaluators.goto();
      await expect(evaluators.evaluatorRow(evaluator.name)).toBeVisible();
      await evaluators.deleteEvaluator(evaluator.name);
      await expect(evaluators.evaluatorRow(evaluator.name)).toHaveCount(0);
      expect((await api.getEvaluators()).some((entry: { name: string }) => entry.name === evaluator.name)).toBeFalsy();
    } finally {
      await api.setEvaluators(original);
    }
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

  test('OPA engine requires a connection and a valid decision path', async () => {
    await evaluators.goto();
    await evaluators.clickAddEvaluator();
    await evaluators.selectEngine('opa');

    const modal = evaluators.modal();
    const submit = modal.locator('.cds--modal-footer .cds--btn--primary');
    await modal.locator('#evaluator-name').fill('opa-modal-test');
    await evaluators.opaConnectionInput().fill('local-policy');
    await modal.locator('#processor-path').fill('actions/dist/opa_allow_action.wasm');
    await evaluators.opaDecisionPathInput().fill('/v1/data/wanaku');

    await expect(submit).toBeDisabled();
    await expect(modal).toContainText('Use path segments of letters, digits, and underscores');

    await evaluators.opaDecisionPathInput().fill('wanaku/tool_call/allow');
    await expect(submit).toBeEnabled();
  });

  test('lists an OPA evaluator with its decision path', async ({ request }) => {
    const api = new ApiHelper(request, routerUrl);
    const original = await api.getEvaluators();
    const evaluator = opaEvaluatorData();
    try {
      await api.setEvaluators([...original, evaluator]);
      await evaluators.goto();
      const row = evaluators.evaluatorRow(evaluator.name);
      await expect(row).toContainText('Open Policy Agent');
      await expect(row).toContainText('Decision: wanaku/tool_call/allow');
      await expect(row).toContainText('local-policy');
    } finally {
      await api.setEvaluators(original);
    }
  });

});
