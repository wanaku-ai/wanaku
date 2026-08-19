import { test, expect } from '@playwright/test';
import { NamespacesPage } from '../pages/namespaces.page';
import { ApiHelper } from '../helpers/api-helpers';
import { namespaceData } from '../helpers/test-data';

const routerUrl = process.env.WANAKU_ROUTER_URL ?? 'http://localhost:8080';

test.describe('Namespaces', () => {
  let namespaces: NamespacesPage;
  let api: ApiHelper;
  const createdNamespaces: string[] = [];

  test.beforeEach(async ({ page, request }) => {
    namespaces = new NamespacesPage(page, `${routerUrl}/admin/`);
    api = new ApiHelper(request, routerUrl);
  });

  test.afterEach(async () => {
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
});
