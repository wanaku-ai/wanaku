import { test, expect } from '@playwright/test';
import { PromptsPage } from '../pages/prompts.page';
import { ApiHelper } from '../helpers/api-helpers';
import { promptData } from '../helpers/test-data';

const routerUrl = process.env.WANAKU_ROUTER_URL ?? 'http://localhost:8080';

test.describe('Prompts', () => {
  let prompts: PromptsPage;
  let api: ApiHelper;
  const createdPrompts: string[] = [];

  test.beforeEach(async ({ page, request }) => {
    prompts = new PromptsPage(page, `${routerUrl}/admin/`);
    api = new ApiHelper(request, routerUrl);
  });

  test.afterEach(async () => {
    for (const name of createdPrompts) {
      await api.deletePrompt(name).catch(() => {});
    }
    createdPrompts.length = 0;
  });

  test('displays page title', async () => {
    await prompts.goto();
    const title = await prompts.getPageTitle();
    expect(title).toBe('Prompts');
  });

  test('add a prompt via modal', async () => {
    const data = promptData();
    createdPrompts.push(data.name);

    await prompts.goto();
    await prompts.clickAddPrompt();

    const heading = await prompts.getModalHeading();
    expect(heading).toBe('Add a Prompt');

    await prompts.fillPromptForm(data);
    await prompts.submitModal();

    await prompts.waitForPromptInTable(data.name);
  });

  test('delete a prompt', async () => {
    const data = promptData();
    await api.addPrompt(data);

    await prompts.goto();
    await prompts.waitForPromptInTable(data.name);
    await prompts.clickDeletePrompt(data.name);

    await prompts.waitForPromptRemoved(data.name);
  });
});
