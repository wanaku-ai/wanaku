import { test, expect } from '@playwright/test';
import { ResourcesPage } from '../pages/resources.page';
import { ApiHelper } from '../helpers/api-helpers';
import { resourceData } from '../helpers/test-data';

const routerUrl = process.env.WANAKU_ROUTER_URL ?? 'http://localhost:8080';

test.describe('Resources', () => {
  let resources: ResourcesPage;
  let api: ApiHelper;
  const createdResources: string[] = [];

  test.beforeEach(async ({ page, request }) => {
    resources = new ResourcesPage(page, `${routerUrl}/admin/`);
    api = new ApiHelper(request, routerUrl);
  });

  test.afterEach(async () => {
    for (const name of createdResources) {
      await api.deleteResource(name).catch(() => {});
    }
    createdResources.length = 0;
  });

  test('displays page title', async () => {
    await resources.goto();
    const title = await resources.getPageTitle();
    expect(title).toBe('Resources');
  });

  test('add a resource via modal', async () => {
    const data = resourceData();
    createdResources.push(data.name);

    await resources.goto();
    await resources.clickAddResource();

    const heading = await resources.getModalHeading();
    expect(heading).toBe('Add a Resource');

    await resources.fillResourceForm(data);
    await resources.submitModal();

    await resources.waitForResourceInTable(data.name);
  });

  test('edit a resource via modal', async () => {
    const data = resourceData();
    createdResources.push(data.name);
    await api.addResource(data);

    await resources.goto();
    await resources.waitForResourceInTable(data.name);
    await resources.clickEditResource(data.name);

    const heading = await resources.getModalHeading();
    expect(heading).toBe('Edit resource');

    const descInput = resources['page'].locator('#resource-description');
    await descInput.fill('Updated by e2e test');
    await resources.submitModal();

    await resources.waitForResourceInTable(data.name);
    expect(await resources.rowWithText(data.name).innerText()).toContain('Updated by e2e test');
  });

  test('delete a resource', async () => {
    const data = resourceData();
    await api.addResource(data);

    await resources.goto();
    await resources.waitForResourceInTable(data.name);
    await resources.clickDeleteResource(data.name);

    await resources.waitForResourceRemoved(data.name);
  });
});
