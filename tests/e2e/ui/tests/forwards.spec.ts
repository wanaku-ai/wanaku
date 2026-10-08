import { test, expect } from '@playwright/test';
import { ForwardsPage } from '../pages/forwards.page';
import { ApiHelper } from '../helpers/api-helpers';
import { bindingData, forwardData, httpUrlNormalizationCases, invalidHttpUrls } from '../helpers/test-data';

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

  test('validates HTTP and HTTPS addresses before submitting', async ({ page }) => {
    await forwards.goto();
    await forwards.clickAddForward();
    const data = forwardData();
    await forwards.fillForwardForm(data);
    const address = page.locator('#forward-address');
    const add = forwards.modal().getByRole('button', { name: 'Add', exact: true });
    const submissions: string[] = [];
    page.on('request', (request) => {
      if (request.method() === 'POST' && new URL(request.url()).pathname === '/api/v1/forwards') {
        submissions.push(request.url());
      }
    });
    for (const value of invalidHttpUrls) {
      await address.fill(value);
      await expect(address, value).toHaveAttribute('aria-invalid', 'true');
      await expect(add, value).toBeDisabled();
      await address.press('Enter');
      await expect(forwards.modal()).toBeVisible();
    }
    for (const value of ['http://localhost:9090/mcp', 'https://example.com/mcp', 'http://[::1]:9090/mcp', 'HTTPS://example.com/mcp']) {
      await address.fill(value);
      await expect(add, value).toBeEnabled();
    }
    expect(submissions).toEqual([]);
    await forwards.cancelModal();
  });

  for (const urls of httpUrlNormalizationCases) {
    test(`saves a forward URL with ${urls.label}`, async () => {
      const data = forwardData({ address: urls.address });
      createdForwards.push(data.name);
      await forwards.goto();
      await forwards.clickAddForward();
      await forwards.fillForwardForm(data);
      await forwards.submitModal();
      await forwards.waitForForwardInTable(data.name);
      const saved = (await (await api.getForward(data.name)).json()).data;
      expect(saved.address).toBe(urls.normalizedAddress);
    });
  }

  test('forward modal filters credential bindings by purpose', async ({ page }) => {
    const data = forwardData();
    // A binding is usable only when it is owned by this forward and allows the
    // purpose. Author-side bindings come from config, so the API list is mocked.
    // One binding per purpose gives each selector a positive control.
    const discovery = bindingData({ forwardId: data.name, allowedPurposes: ['discovery'] });
    const invocation = bindingData({ forwardId: data.name, allowedPurposes: ['invocation'] });
    await page.route('**/api/v1/bindings', (route) =>
      route.fulfill({ json: { data: [discovery, invocation] } }));

    await forwards.goto();
    await forwards.clickAddForward();
    await forwards.fillForwardForm(data);

    // Each selector offers only the binding for its own purpose, and excludes
    // the binding for the other purpose.
    await expect.poll(() => forwards.getDiscoveryBindingOptions()).toContain(discovery.id);
    await expect.poll(() => forwards.getInvocationBindingOptions()).toContain(invocation.id);
    expect(await forwards.getDiscoveryBindingOptions()).not.toContain(invocation.id);
    expect(await forwards.getInvocationBindingOptions()).not.toContain(discovery.id);
  });

  test('forward modal explains when no binding matches the forward', async ({ page }) => {
    const data = forwardData();
    // The only binding is owned by a different forward, so it never matches this
    // forward name. The selector must show the empty state instead of a silently
    // disabled control.
    const other = bindingData({ forwardId: 'some-other-forward', allowedPurposes: ['discovery'] });
    await page.route('**/api/v1/bindings', (route) =>
      route.fulfill({ json: { data: [other] } }));

    await forwards.goto();
    await forwards.clickAddForward();
    await forwards.fillForwardForm(data);

    // The empty state (shown only after the bindings load) tells the operator to
    // author a binding in wanaku.yaml with a matching forwardId.
    await expect.poll(() => forwards.modalHasText(`forwardId: ${data.name}`)).toBe(true);
    // The mocked binding is owned by a different forward, so once loaded it is
    // never offered as an option.
    expect(await forwards.getDiscoveryBindingOptions()).not.toContain(other.id);
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
    const headerText = await forwards.getColumnHeaders();
    expect(headerText.some(h => h.includes('Server'))).toBeTruthy();
  });

  test('status column appears in table', async () => {
    await forwards.goto();
    const headerText = await forwards.getColumnHeaders();
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
