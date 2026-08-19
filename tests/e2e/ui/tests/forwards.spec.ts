import { test, expect } from '@playwright/test';
import { ForwardsPage } from '../pages/forwards.page';
import { ApiHelper } from '../helpers/api-helpers';
import { forwardData } from '../helpers/test-data';

const routerUrl = process.env.WANAKU_ROUTER_URL ?? 'http://localhost:8080';

test.describe('Forwards', () => {
  let forwards: ForwardsPage;
  let api: ApiHelper;
  const createdForwards: string[] = [];

  test.beforeEach(async ({ page, request }) => {
    forwards = new ForwardsPage(page, `${routerUrl}/admin/`);
    api = new ApiHelper(request, routerUrl);
  });

  test.afterEach(async () => {
    for (const name of createdForwards) {
      await api.deleteForward(name).catch(() => {});
    }
    createdForwards.length = 0;
  });

  test('displays page title', async () => {
    await forwards.goto();
    const title = await forwards.getPageTitle();
    expect(title).toBe('Forwards');
  });

  test('add a forward via modal', async () => {
    const data = forwardData();
    createdForwards.push(data.name);

    await forwards.goto();
    await forwards.clickAddForward();

    const heading = await forwards.getModalHeading();
    expect(heading).toBe('Add a Forward');

    await forwards.fillForwardForm(data);
    await forwards.submitModal();

    await forwards.waitForForwardInTable(data.name);
  });

  test('delete a forward', async () => {
    const data = forwardData();
    await api.addForward(data);

    await forwards.goto();
    await forwards.waitForForwardInTable(data.name);
    await forwards.clickDeleteForward(data.name);

    await forwards.waitForForwardRemoved(data.name);
  });

  test('detail modal shows forward info', async () => {
    const data = forwardData();
    createdForwards.push(data.name);
    await api.addForward(data);

    await forwards.goto();
    await forwards.waitForForwardInTable(data.name);
    await forwards.clickDetailForward(data.name);

    const heading = await forwards.getDetailModalHeading();
    expect(heading).toBe(data.name);

    const hasAddress = await forwards.detailModalHasText(data.address);
    expect(hasAddress).toBeTruthy();

    await forwards.closeDetailModal();
  });

  test('detail modal shows server info section', async () => {
    const data = forwardData();
    createdForwards.push(data.name);
    await api.addForward(data);

    await forwards.goto();
    await forwards.waitForForwardInTable(data.name);
    await forwards.clickDetailForward(data.name);

    const hasServerInfoHeading = await forwards.detailModalHasText('Server Info');
    expect(hasServerInfoHeading).toBeTruthy();

    await forwards.closeDetailModal();
  });

  test('server column appears in table', async () => {
    await forwards.goto();
    const headerText = await forwards.page.locator('th').allInnerTexts();
    expect(headerText.some(h => h.includes('Server'))).toBeTruthy();
  });

  test('status column appears in table', async () => {
    await forwards.goto();
    const headerText = await forwards.page.locator('th').allInnerTexts();
    expect(headerText.some(h => h.includes('Status'))).toBeTruthy();
  });

  test('forward shows unavailable status when unreachable', async () => {
    const data = forwardData();
    createdForwards.push(data.name);
    await api.addForward(data);

    await forwards.goto();
    await forwards.waitForForwardInTable(data.name);

    const status = await forwards.getRowStatusText(data.name);
    expect(status).toBe('Unavailable');
  });

  test('detail modal shows status', async () => {
    const data = forwardData();
    createdForwards.push(data.name);
    await api.addForward(data);

    await forwards.goto();
    await forwards.waitForForwardInTable(data.name);
    await forwards.clickDetailForward(data.name);

    const hasStatus = await forwards.detailModalHasText('Status:');
    expect(hasStatus).toBeTruthy();

    const hasUnavailable = await forwards.detailModalHasText('Unavailable');
    expect(hasUnavailable).toBeTruthy();

    await forwards.closeDetailModal();
  });
});
