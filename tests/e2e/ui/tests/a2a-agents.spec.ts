import { test, expect } from '@playwright/test';
import { A2aAgentsPage } from '../pages/a2a-agents.page';
import { ApiHelper } from '../helpers/api-helpers';
import { agentData } from '../helpers/test-data';

const routerUrl = process.env.WANAKU_ROUTER_URL ?? 'http://localhost:8080';

test.describe('A2A agents', () => {
  let agents: A2aAgentsPage;
  let api: ApiHelper;
  const created: { namespace: string; name: string }[] = [];

  test.beforeEach(async ({ page, request }) => {
    agents = new A2aAgentsPage(page, `${routerUrl}/admin/`);
    api = new ApiHelper(request, routerUrl);
  });

  test.afterEach(async () => {
    for (const agent of created.splice(0)) {
      await api.deleteAgent(agent.namespace, agent.name);
    }
  });

  test('displays page title and add modal', async () => {
    await agents.goto();
    expect(await agents.getPageTitle()).toBe('A2A agents');
    await agents.clickAddAgent();
    expect(await agents.getModalHeading()).toBe('Add A2A agent');
    await agents.cancelModal();
  });

  test('adds an agent and displays its proxy URL', async () => {
    const data = agentData();
    created.push(data);
    await agents.goto();
    await agents.clickAddAgent();
    await agents.fillAgentForm(data);
    await agents.submitModal();
    await agents.waitForAgent(data.name);
    await agents.openDetails(data.name);
    await expect(agents.modal()).toContainText(`/${data.namespace}/a2a/${data.name}`);
    await agents.closeDetails();
    const response = await api.getAgent(data.namespace, data.name);
    expect(response.ok()).toBeTruthy();
    expect((await response.json()).data.address).toBe(data.address);
  });

  test('edits an agent address', async ({ page }) => {
    const data = agentData();
    created.push(data);
    await api.addAgent(data);
    await agents.goto();
    await agents.waitForAgent(data.name);
    await agents.clickEditAgent(data.name);
    const address = 'http://localhost:19998/';
    await page.locator('#agent-address').fill(address);
    await agents.submitModal();
    await expect(agents.rowWithText(data.name)).toContainText(address);
    expect((await (await api.getAgent(data.namespace, data.name)).json()).data.address).toBe(address);
  });

  test('deletes an agent', async () => {
    const data = agentData();
    created.push(data);
    await api.addAgent(data);
    await agents.goto();
    await agents.waitForAgent(data.name);
    await agents.clickDeleteAgent(data.name);
    await agents.waitForAgentRemoved(data.name);
    expect((await api.getAgent(data.namespace, data.name)).status()).toBe(404);
  });

  test('rejects an invalid upstream address without creating an agent', async ({ request }) => {
    const data = agentData({ address: 'file:///tmp/agent' });
    created.push(data);
    const response = await request.post(`${routerUrl}/api/v1/agents`, { data });
    expect(response.status()).toBe(400);
    expect((await api.getAgent(data.namespace, data.name)).status()).toBe(404);
  });
});
