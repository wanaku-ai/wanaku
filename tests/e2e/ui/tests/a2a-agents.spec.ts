import { test, expect } from '@playwright/test';
import { A2aAgentsPage } from '../pages/a2a-agents.page';
import { ApiHelper } from '../helpers/api-helpers';
import { agentData, httpUrlNormalizationCases, invalidHttpUrls } from '../helpers/test-data';

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

  test('adds an agent and keeps suggested discovery tied to its endpoint on edit', async ({ page }) => {
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
    const saved = (await response.json()).data;
    expect(saved.address).toBe(data.address);
    expect(saved.cardAddress).toBeUndefined();
    await agents.clickEditAgent(data.name);
    await expect(page.locator('#agent-card-address')).toHaveValue('');
    const address = 'http://localhost:19998/rpc';
    await page.locator('#agent-address').fill(address);
    await agents.submitModal();
    const edited = (await (await api.getAgent(data.namespace, data.name)).json()).data;
    expect(edited.address).toBe(address);
    expect(edited.cardAddress).toBeUndefined();
  });

  for (const urls of httpUrlNormalizationCases) {
    test(`saves endpoint and explicit card URLs with ${urls.label}`, async () => {
      const data = agentData({ address: urls.address, cardAddress: urls.cardAddress });
      created.push(data);
      await agents.goto();
      await agents.clickAddAgent();
      await agents.fillAgentForm(data);
      await agents.submitModal();
      await agents.waitForAgent(data.name);
      const saved = (await (await api.getAgent(data.namespace, data.name)).json()).data;
      expect(saved.address).toBe(urls.normalizedAddress);
      expect(saved.cardAddress).toBe(urls.normalizedCardAddress);
    });
  }

  test('validates endpoint and optional card URLs before submitting', async ({ page }) => {
    const submissions: string[] = [];
    page.on('request', (request) => {
      if (request.method() === 'POST' && new URL(request.url()).pathname === '/api/v1/agents') {
        submissions.push(request.url());
      }
    });
    await agents.goto();
    await agents.clickAddAgent();
    await page.locator('#agent-name').fill(agentData().name);
    const endpoint = page.locator('#agent-address');
    const card = page.locator('#agent-card-address');
    const add = agents.modal().getByRole('button', { name: 'Add', exact: true });

    for (const value of invalidHttpUrls) {
      await endpoint.fill(value);
      await expect(endpoint, value).toHaveAttribute('aria-invalid', 'true');
      await expect(add, value).toBeDisabled();
      await endpoint.press('Enter');
      await expect(agents.modal()).toBeVisible();
    }
    await endpoint.fill('http://localhost:9090/');
    await expect(add).toBeEnabled();
    for (const value of invalidHttpUrls) {
      await card.fill(value);
      await expect(card, value).toHaveAttribute('aria-invalid', 'true');
      await expect(add, value).toBeDisabled();
      await card.press('Enter');
      await expect(agents.modal()).toBeVisible();
    }
    for (const value of ['http://localhost:9090/', 'https://agent.example/rpc?tenant=blue', 'http://[::1]:9090/', 'HTTPS://agent.example/rpc']) {
      await endpoint.fill(value);
      await card.fill(value);
      await expect(add, value).toBeEnabled();
    }
    await card.fill('');
    await expect(add).toBeEnabled();
    expect(submissions).toEqual([]);
    await agents.cancelModal();
  });

  test('suggests the standard card URL from the endpoint origin', async ({ page }) => {
    await agents.goto();
    await agents.clickAddAgent();
    const endpoint = page.locator('#agent-address');
    const card = page.locator('#agent-card-address');
    for (const [address, expected] of [
      ['http://localhost:9090/', 'http://localhost:9090/.well-known/agent-card.json'],
      ['http://localhost:9090', 'http://localhost:9090/.well-known/agent-card.json'],
      ['https://agent.example:9443/rpc?tenant=blue', 'https://agent.example:9443/.well-known/agent-card.json'],
      ['http://[::1]:9090/rpc', 'http://[::1]:9090/.well-known/agent-card.json'],
      ['HTTPS://agent.example/rpc', 'https://agent.example/.well-known/agent-card.json'],
      ['https://münich.example/送信', 'https://xn--mnich-kva.example/.well-known/agent-card.json'],
    ]) {
      await endpoint.fill(address);
      await expect(card).toHaveValue(expected);
    }
    await endpoint.fill('');
    await expect(card).toHaveValue('');
    await endpoint.fill('not-a-url');
    await expect(card).toHaveValue('');
    await agents.cancelModal();
  });

  test('preserves a custom card URL when the endpoint changes', async ({ page }) => {
    await agents.goto();
    await agents.clickAddAgent();
    await agents.fillAgentForm(agentData({ cardAddress: 'https://cards.example/custom.json' }));
    await page.locator('#agent-address').fill('https://other-agent.example/rpc');
    await expect(page.locator('#agent-card-address')).toHaveValue('https://cards.example/custom.json');
    await agents.cancelModal();
  });

  test('keeps a cleared card empty and saves standard discovery', async ({ page }) => {
    const data = agentData();
    created.push(data);
    await agents.goto();
    await agents.clickAddAgent();
    await agents.fillAgentForm(data);
    await agents.modal().getByRole('button', { name: 'Clear agent card URL', exact: true }).click();
    await expect(page.locator('#agent-card-address')).toHaveValue('');
    const address = 'https://agent.example/rpc';
    await page.locator('#agent-address').fill(address);
    await expect(page.locator('#agent-card-address')).toHaveValue('');
    await agents.submitModal();
    await agents.waitForAgent(data.name);
    const saved = (await (await api.getAgent(data.namespace, data.name)).json()).data;
    expect(saved.address).toBe(address);
    expect(saved.cardAddress).toBeUndefined();
  });

  test('keeps a manually emptied card empty when the endpoint changes', async ({ page }) => {
    await agents.goto();
    await agents.clickAddAgent();
    await agents.fillAgentForm(agentData());
    await page.locator('#agent-card-address').fill('');
    await page.locator('#agent-address').fill('https://agent.example/rpc');
    await expect(page.locator('#agent-card-address')).toHaveValue('');
    await agents.cancelModal();
  });

  test('edits an agent address', async ({ page }) => {
    const data = agentData();
    created.push(data);
    await api.addAgent(data);
    await agents.goto();
    await agents.waitForAgent(data.name);
    await agents.clickEditAgent(data.name);
    await expect(page.locator('#agent-card-address')).toHaveValue('');
    const address = 'http://localhost:19998/';
    await page.locator('#agent-address').fill(address);
    await expect(page.locator('#agent-card-address')).toHaveValue('');
    await agents.submitModal();
    await expect(agents.rowWithText(data.name)).toContainText(address);
    expect((await (await api.getAgent(data.namespace, data.name)).json()).data.address).toBe(address);
  });

  test('preserves an existing custom card URL when editing the endpoint', async ({ page }) => {
    const cardAddress = 'https://cards.example/custom.json';
    const data = agentData({ cardAddress });
    created.push(data);
    await api.addAgent(data);
    await agents.goto();
    await agents.waitForAgent(data.name);
    await agents.clickEditAgent(data.name);
    await page.locator('#agent-address').fill('https://other-agent.example/rpc');
    await expect(page.locator('#agent-card-address')).toHaveValue(cardAddress);
    await agents.submitModal();
    expect((await (await api.getAgent(data.namespace, data.name)).json()).data.cardAddress).toBe(data.cardAddress);
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
