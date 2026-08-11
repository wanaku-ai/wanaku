import { test, expect } from '@playwright/test';
import { ToolsPage } from '../pages/tools.page';
import { ApiHelper } from '../helpers/api-helpers';
import { toolData } from '../helpers/test-data';

const routerUrl = process.env.WANAKU_ROUTER_URL ?? 'http://localhost:8080';

test.describe('Tools', () => {
  let tools: ToolsPage;
  let api: ApiHelper;
  const createdTools: string[] = [];

  test.beforeEach(async ({ page, request }) => {
    tools = new ToolsPage(page, `${routerUrl}/admin/`);
    api = new ApiHelper(request, routerUrl);
  });

  test.afterEach(async () => {
    for (const name of createdTools) {
      await api.deleteTool(name).catch(() => {});
    }
    createdTools.length = 0;
  });

  test('displays page title', async () => {
    await tools.goto();
    const title = await tools.getPageTitle();
    expect(title).toBe('Tools');
  });

  test('add a tool via modal', async () => {
    const data = toolData();
    createdTools.push(data.name);

    await tools.goto();
    await tools.clickAddTool();

    const heading = await tools.getModalHeading();
    expect(heading).toBe('Add a Tool');

    await tools.fillToolForm(data);
    await tools.submitModal();

    await tools.waitForToolInTable(data.name);
  });

  test('delete a tool', async () => {
    const data = toolData();
    await api.addTool(data);

    await tools.goto();
    await tools.waitForToolInTable(data.name);
    await tools.clickDeleteTool(data.name);

    await tools.waitForToolRemoved(data.name);
  });
});
