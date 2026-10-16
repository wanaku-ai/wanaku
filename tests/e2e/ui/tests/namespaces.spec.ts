import { test, expect } from '@playwright/test';
import { NamespacesPage } from '../pages/namespaces.page';
import { ApiHelper } from '../helpers/api-helpers';
import { agentData, namespaceData } from '../helpers/test-data';

const routerUrl = process.env.WANAKU_ROUTER_URL ?? 'http://localhost:8080';

test.describe('Namespaces', () => {
  let namespaces: NamespacesPage;
  let api: ApiHelper;
  const createdNamespaces: string[] = [];
  const createdAgents: { namespace: string; name: string }[] = [];

  test.beforeEach(async ({ page, request }) => {
    namespaces = new NamespacesPage(page, `${routerUrl}/admin/`);
    api = new ApiHelper(request, routerUrl);
  });

  test.afterEach(async () => {
    for (const agent of createdAgents) {
      await api.deleteAgent(agent.namespace, agent.name).catch(() => {});
    }
    createdAgents.length = 0;
    for (const name of createdNamespaces) {
      await api.deleteNamespace(name).catch(() => {});
    }
    createdNamespaces.length = 0;
  });

  test('displays page title', async () => {
    await namespaces.goto();
    const title = await namespaces.getPageTitle();
    expect(title).toBe('Namespaces');
  });

  test('table has expected columns', async () => {
    await namespaces.goto();
    const headers = await namespaces.getColumnHeaders();
    expect(headers.some(h => h.includes('Name'))).toBeTruthy();
    expect(headers.some(h => h.includes('Status'))).toBeTruthy();
    expect(headers.some(h => h.includes('Address'))).toBeTruthy();
    expect(headers.some(h => h.includes('Actions'))).toBeTruthy();
  });

  test('default namespace is present', async () => {
    await namespaces.goto();
    await namespaces.waitForNamespaceInTable('default');
  });

  test('add a namespace via modal', async () => {
    const data = namespaceData();
    createdNamespaces.push(data.name);

    await namespaces.goto();
    await namespaces.clickAddNamespace();

    const heading = await namespaces.getModalHeading();
    expect(heading).toBe('Create Namespace');

    await namespaces.fillNamespaceForm(data.name);
    await namespaces.submitModal();

    await namespaces.waitForNamespaceInTable(data.name);
  });

  test('modal rejects invalid namespace name', async () => {
    await namespaces.goto();
    await namespaces.clickAddNamespace();

    await namespaces.fillNamespaceForm('Bad Name');
    expect(await namespaces.isSubmitDisabled()).toBeTruthy();

    await namespaces.fillNamespaceForm('-leading');
    expect(await namespaces.isSubmitDisabled()).toBeTruthy();

    await namespaces.cancelModal();
  });

  test('delete a namespace', async () => {
    const data = namespaceData();
    await api.addNamespace(data);

    await namespaces.goto();
    await namespaces.waitForNamespaceInTable(data.name);
    await namespaces.clickDeleteNamespace(data.name);

    await namespaces.waitForNamespaceRemoved(data.name);
  });

  test('address column shows MCP endpoint', async () => {
    const data = namespaceData();
    createdNamespaces.push(data.name);
    await api.addNamespace(data);

    await namespaces.goto();
    await namespaces.waitForNamespaceInTable(data.name);

    const row = namespaces.rowWithText(data.name);
    const rowText = await row.innerText();
    expect(rowText).toContain(`/${data.name}/mcp`);
  });

  test('edit saves a namespace with an A2A agent through PUT', async ({ page }) => {
    const data = namespaceData();
    const agent = agentData({ namespace: data.name });
    createdNamespaces.push(data.name);
    createdAgents.push(agent);
    await api.addNamespace(data);
    await api.addAgent(agent);

    const namespaceRequests: string[] = [];
    page.on('request', (request) => {
      if (request.url().includes('/api/v1/namespaces/')) {
        namespaceRequests.push(request.method());
      }
    });

    await namespaces.goto();
    await namespaces.waitForNamespaceInTable(data.name);
    await namespaces.clickEditNamespace(data.name);
    expect(await namespaces.getModalHeading()).toBe('Edit Namespace');

    const update = page.waitForResponse(
      (response) =>
        response.request().method() === 'PUT' &&
        response.url().endsWith(`/api/v1/namespaces/${data.name}`),
    );
    await namespaces.submitModal();
    expect((await update).status()).toBe(200);

    await namespaces.waitForNamespaceInTable(data.name);
    await expect(namespaces.errorNotification()).toBeHidden();
    expect(namespaceRequests).toContain('PUT');
    expect(namespaceRequests).not.toContain('DELETE');
    expect(namespaceRequests).not.toContain('POST');
    expect((await api.getNamespace(data.name)).status()).toBe(200);
    expect((await api.getAgent(agent.namespace, agent.name)).status()).toBe(200);
  });

  test('failed PUT keeps the namespace and reports the error', async ({ page }) => {
    const data = namespaceData();
    createdNamespaces.push(data.name);
    await api.addNamespace(data);

    await page.route(`**/api/v1/namespaces/${data.name}`, (route) =>
      route.request().method() === 'PUT'
        ? route.fulfill({ status: 500, json: { data: null, error: 'simulated update failure' } })
        : route.continue(),
    );

    await namespaces.goto();
    await namespaces.waitForNamespaceInTable(data.name);
    await namespaces.clickEditNamespace(data.name);
    await namespaces.submitModal();

    await expect(namespaces.errorNotification()).toContainText('simulated update failure');
    await namespaces.waitForNamespaceInTable(data.name);
    expect((await api.getNamespace(data.name)).status()).toBe(200);
  });
});
