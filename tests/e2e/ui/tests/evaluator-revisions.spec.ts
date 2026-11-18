import { test, expect } from '@playwright/test';
import { EvaluatorsPage } from '../pages/evaluators.page';
import { mockEvaluators, type RevisionEntry } from '../helpers/evaluator-mock';
import { evaluatorRevision } from '../helpers/test-data';

const routerUrl = process.env.WANAKU_ROUTER_URL ?? 'http://localhost:8080';

const evaluator = (name: string, path: string) => ({
  name,
  trigger: { method: 'tools/call' },
  engine: { type: 'passthrough' },
  processor: { path },
});

const gateOld = evaluator('gate', 'actions/old.wasm');
const gateNew = evaluator('gate', 'actions/new.wasm');
const audit = evaluator('audit', 'actions/audit.wasm');

const superseded: RevisionEntry = {
  revision: evaluatorRevision({
    id: 1,
    status: 'superseded',
    checksum: 'checksum-one',
    actor: 'alice',
  }),
  evaluators: [gateOld],
};
const active: RevisionEntry = {
  revision: evaluatorRevision({
    id: 2,
    origin: 'api',
    checksum: 'checksum-two',
    activated_at: '2026-08-28T10:00:00Z',
  }),
  evaluators: [gateNew, audit],
};
const rejected: RevisionEntry = {
  revision: evaluatorRevision({
    id: 3,
    status: 'rejected',
    activated_at: null,
    origin: 'api',
    checksum: 'checksum-three',
    failure_reason: 'processor actions/missing.wasm cannot be compiled',
  }),
  evaluators: [evaluator('gate', 'actions/missing.wasm')],
};

test.describe('Evaluator revisions', () => {
  let evaluators: EvaluatorsPage;

  test.beforeEach(async ({ page }) => {
    evaluators = new EvaluatorsPage(page, `${routerUrl}/admin/`);
  });

  test('displays the active revision and the revision history', async ({ page }) => {
    await mockEvaluators(page, { revisions: [superseded, active, rejected], activeId: 2 });
    await evaluators.goto();

    await expect(evaluators.evaluatorRow('gate')).toBeVisible();
    await expect(evaluators.evaluatorRow('audit')).toBeVisible();
    await expect(evaluators.activeRevision()).toContainText('Revision 2');
    await expect(evaluators.activeRevision()).toContainText('checksum-two');
    await expect(evaluators.activeRevision()).toContainText('api');
    await expect(evaluators.activeRevision()).toContainText('active');

    const history = evaluators.revisionHistory();
    await expect(history).toContainText('Revision 3 — rejected');
    await expect(history).toContainText('Revision 2 — active');
    await expect(history).toContainText('Revision 1 — superseded');

    await evaluators.openRevision(1);
    await expect(history).toContainText('checksum-one');
    await expect(history).toContainText('startup');
    await expect(history).toContainText('alice');
  });

  test('displays a rejected revision with its failure reason', async ({ page }) => {
    await mockEvaluators(page, { revisions: [active, rejected], activeId: 2 });
    await evaluators.goto();

    await evaluators.openRevision(3);
    await expect(evaluators.revisionHistory()).toContainText(
      'processor actions/missing.wasm cannot be compiled',
    );
    await expect(evaluators.revisionHistory()).toContainText('checksum-three');
    await expect(
      evaluators.revisionHistory().getByRole('button', { name: 'Activate as new revision' }),
    ).toHaveCount(0);
  });

  test('shows an empty state when no revision is active', async ({ page }) => {
    await mockEvaluators(page, { revisions: [rejected] });
    await evaluators.goto();

    await expect(evaluators.activeRevision()).toContainText('No evaluator revision is currently active');
    await expect(evaluators.revisionHistory()).toContainText('Revision 3 — rejected');
  });

  test('sends the active revision as the expected revision', async ({ page }) => {
    const { updateRequests } = await mockEvaluators(page, {
      revisions: [active],
      activeId: 2,
      updates: [
        {
          json: { data: { revision: active.revision, evaluators: active.evaluators } },
          apply: (state) => {
            state.revisions = [
              { ...active, revision: { ...active.revision, status: 'superseded' } },
              {
                revision: evaluatorRevision({ id: 4, origin: 'api', checksum: 'checksum-four' }),
                evaluators: [evaluator('gate', 'actions/edited.wasm'), audit],
              },
            ];
            state.activeId = 4;
          },
        },
      ],
    });
    await evaluators.goto();

    await evaluators.editEvaluator('gate');
    await evaluators.modal().locator('#processor-path').fill('actions/edited.wasm');
    await evaluators.submitModal();

    expect(updateRequests).toHaveLength(1);
    expect(updateRequests[0]).toMatchObject({ expected_revision: 2 });
    await expect(evaluators.activeRevision()).toContainText('Revision 4');
  });

  test('keeps the edits after a 409 conflict and saves them on the latest revision', async ({
    page,
  }) => {
    const concurrent: RevisionEntry = {
      revision: evaluatorRevision({ id: 4, origin: 'api', checksum: 'checksum-four' }),
      evaluators: [gateNew, audit, evaluator('extra', 'actions/extra.wasm')],
    };
    const saved: RevisionEntry = {
      revision: evaluatorRevision({ id: 5, origin: 'api', checksum: 'checksum-five' }),
      evaluators: [evaluator('gate', 'actions/edited.wasm'), audit, concurrent.evaluators[2]],
    };
    const { updateRequests } = await mockEvaluators(page, {
      revisions: [active],
      activeId: 2,
      updates: [
        {
          status: 409,
          json: { error: 'expected active revision 2, but current is 4' },
          apply: (state) => {
            state.revisions = [
              { ...active, revision: { ...active.revision, status: 'superseded' } },
              concurrent,
            ];
            state.activeId = 4;
          },
        },
        {
          json: { data: saved },
          apply: (state) => {
            state.revisions = [
              ...state.revisions.map((entry) => ({
                ...entry,
                revision: { ...entry.revision, status: 'superseded' },
              })),
              saved,
            ];
            state.activeId = 5;
          },
        },
      ],
    });
    await evaluators.goto();

    await evaluators.editEvaluator('gate');
    await evaluators.modal().locator('#processor-path').fill('actions/edited.wasm');
    await evaluators.clickModalPrimary();

    const modal = evaluators.modal();
    await expect(modal).toContainText('Another change was activated first');
    await expect(modal).toContainText('expected active revision 2, but current is 4');
    await expect(modal.locator('#processor-path')).toHaveValue('actions/edited.wasm');

    await modal.getByRole('button', { name: 'Load latest revision' }).click();
    await expect(evaluators.activeRevision()).toContainText('Revision 4');
    await expect(modal.locator('#processor-path')).toHaveValue('actions/edited.wasm');

    await evaluators.submitModal();
    expect(updateRequests).toHaveLength(2);
    expect(updateRequests[0]).toMatchObject({ expected_revision: 2 });
    expect(updateRequests[1]).toMatchObject({
      expected_revision: 4,
      evaluators: saved.evaluators,
    });
    await expect(evaluators.evaluatorRow('extra')).toBeVisible();
    await expect(evaluators.activeRevision()).toContainText('Revision 5');
  });

  test('keeps the edits and lists the rejected revision after a 422 rejection', async ({
    page,
  }) => {
    await mockEvaluators(page, {
      revisions: [active],
      activeId: 2,
      updates: [
        {
          status: 422,
          json: { error: 'configuration rejected: processor actions/bad.wasm cannot be compiled' },
          apply: (state) => {
            state.revisions = [
              ...state.revisions,
              {
                revision: evaluatorRevision({
                  id: 3,
                  status: 'rejected',
                  activated_at: null,
                  origin: 'api',
                  failure_reason: 'processor actions/bad.wasm cannot be compiled',
                }),
                evaluators: [evaluator('gate', 'actions/bad.wasm'), audit],
              },
            ];
          },
        },
      ],
    });
    await evaluators.goto();

    await evaluators.editEvaluator('gate');
    await evaluators.modal().locator('#processor-path').fill('actions/bad.wasm');
    await evaluators.clickModalPrimary();

    const modal = evaluators.modal();
    await expect(modal).toContainText('The server rejected the configuration');
    await expect(modal.locator('#processor-path')).toHaveValue('actions/bad.wasm');
    await expect(evaluators.revisionHistory()).toContainText('Revision 3 — rejected');
    await expect(evaluators.activeRevision()).toContainText('Revision 2');
  });

  test('compares a historical revision with the active revision', async ({ page }) => {
    await mockEvaluators(page, { revisions: [superseded, active], activeId: 2 });
    await evaluators.goto();

    await evaluators.openRevision(1);
    const comparison = evaluators.revisionHistory().getByTestId('comparison-1');
    await expect(comparison).toContainText('Comparison with active revision 2');
    await expect(comparison).toContainText('audit');
    await expect(comparison).toContainText('is only in the active revision');
    await expect(comparison).toContainText('processor.path');
    await expect(comparison).toContainText('actions/new.wasm');
    await expect(comparison).toContainText('actions/old.wasm');
  });

  test('activates a historical revision as a new revision', async ({ page }) => {
    const restored: RevisionEntry = {
      revision: evaluatorRevision({ id: 3, origin: 'api', checksum: 'checksum-one' }),
      evaluators: superseded.evaluators,
    };
    const { activateRequests } = await mockEvaluators(page, {
      revisions: [superseded, active],
      activeId: 2,
      activations: [
        {
          json: { data: restored },
          apply: (state) => {
            state.revisions = [
              { ...superseded },
              { ...active, revision: { ...active.revision, status: 'superseded' } },
              restored,
            ];
            state.activeId = 3;
          },
        },
      ],
    });
    await evaluators.goto();
    await expect(evaluators.evaluatorRow('audit')).toBeVisible();

    await evaluators.activateRevision(1);

    expect(activateRequests).toEqual([{ expected_revision: 2 }]);
    await expect(page.getByText('Revision 1 is active again as revision 3')).toBeVisible();
    await expect(evaluators.activeRevision()).toContainText('Revision 3');
    await expect(evaluators.evaluatorRow('audit')).toHaveCount(0);
    await expect(evaluators.evaluatorRow('gate')).toContainText('gate');
    await expect(evaluators.revisionHistory()).toContainText('Revision 3 — active');
    await expect(evaluators.revisionHistory()).toContainText('Revision 2 — superseded');
  });

  test('reports a 409 conflict when a rollback is stale', async ({ page }) => {
    await mockEvaluators(page, {
      revisions: [superseded, active],
      activeId: 2,
      activations: [
        { status: 409, json: { error: 'expected active revision 2, but current is 4' } },
      ],
    });
    await evaluators.goto();

    await evaluators.activateRevision(1);

    await expect(evaluators.errorNotification()).toContainText(
      'expected active revision 2, but current is 4',
    );
    await expect(evaluators.activeRevision()).toContainText('Revision 2');
  });
});
