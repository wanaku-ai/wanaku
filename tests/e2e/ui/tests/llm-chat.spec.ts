import { test, expect } from '@playwright/test';
import { LlmChatPage } from '../pages/llm-chat.page';
import { ApiHelper } from '../helpers/api-helpers';
import { namespaceData, toolData } from '../helpers/test-data';
import { mockRegistryEntries } from '../helpers/registry-mock';

const routerUrl = process.env.WANAKU_ROUTER_URL ?? 'http://localhost:8080';
const API_KEY = 'test-secret-key';

test.describe('LLM Chat configuration persistence', () => {
  let llmChat: LlmChatPage;

  test.beforeEach(async ({ page }) => {
    llmChat = new LlmChatPage(page, `${routerUrl}/admin/`);
    await llmChat.goto();
  });

  test('displays page title', async () => {
    const title = await llmChat.getPageTitle();
    expect(title).toContain('LLM Chat');
  });

  test('API key opt-in is disabled until settings storage is enabled', async () => {
    await expect(llmChat.storeApiKeyToggle()).toBeDisabled();

    await llmChat.setStoreSettings(true);
    await expect(llmChat.storeApiKeyToggle()).toBeEnabled();
  });

  test('does not persist the API key unless the user opts in', async () => {
    await llmChat.setStoreSettings(true);
    await llmChat.setApiKey(API_KEY);
    expect(await llmChat.getApiKey()).toBe(API_KEY);

    // The key must never reach local storage while the opt-in is off, even though it is in memory.
    expect(await llmChat.storedConfigRaw() ?? '').not.toContain(API_KEY);

    await llmChat.reload();

    expect(await llmChat.getApiKey()).toBe('');
  });

  test('persists the API key across reloads when the user opts in', async () => {
    await llmChat.setStoreSettings(true);
    await expect(llmChat.storeApiKeyToggle()).toBeEnabled();
    await llmChat.setStoreApiKey(true);
    await expect(llmChat.securityWarning()).toBeVisible();
    await llmChat.setApiKey(API_KEY);

    // With the opt-in on, the key is written to local storage.
    expect(await llmChat.storedConfigRaw() ?? '').toContain(API_KEY);

    await llmChat.reload();

    expect(await llmChat.getApiKey()).toBe(API_KEY);
    await expect(llmChat.storeApiKeyToggle()).toHaveAttribute('aria-checked', 'true');
    await expect(llmChat.securityWarning()).toBeVisible();
  });

  test('does not rewrite the API key after storage is turned off in another tab', async ({ page }) => {
    // First tab: enable storage, opt in, and store the key.
    await llmChat.setStoreSettings(true);
    await llmChat.setStoreApiKey(true);
    await llmChat.setApiKey(API_KEY);
    expect(await llmChat.storedConfigRaw() ?? '').toContain(API_KEY);

    // Second tab shares the same browser context and local storage. Turning storage off clears it.
    const secondTab = new LlmChatPage(await page.context().newPage(), `${routerUrl}/admin/`);
    await secondTab.goto();
    await secondTab.setStoreSettings(false);
    expect(await secondTab.storedConfigRaw()).toBeNull();

    // The first tab syncs from the storage event: both toggles turn off and the warning disappears.
    await expect(llmChat.storeSettingsToggle()).toHaveAttribute('aria-checked', 'false');
    await expect(llmChat.storeApiKeyToggle()).toHaveAttribute('aria-checked', 'false');
    await expect(llmChat.securityWarning()).toBeHidden();

    // A change in the first tab must not write anything, since storage is now off. Assert on null
    // rather than "does not contain the old key" so a regression that persists any value is caught.
    await llmChat.setApiKey('another-value');
    expect(await llmChat.storedConfigRaw()).toBeNull();
  });
});

// Regression test for #1932: the tool selector must list the tools of the selected namespace.
test.describe('LLM Chat tool selection', () => {
  let llmChat: LlmChatPage;
  let api: ApiHelper;
  let namespace: string;

  test.beforeEach(async ({ page, request }) => {
    llmChat = new LlmChatPage(page, `${routerUrl}/admin/`);
    api = new ApiHelper(request, routerUrl);
    namespace = namespaceData().name;
    await api.addNamespace({ name: namespace });
  });

  test.afterEach(async () => {
    await api.deleteNamespace(namespace).catch(() => {});
  });

  test('lists the tools of the selected namespace', async ({ page }) => {
    const forwardTool = (ns: string) => ({
      ...toolData({ type: 'mcp-forward' }),
      forwardId: 'e2e-forward',
      namespace: ns,
      enabled: true,
    });
    const defaultTool = forwardTool('default');
    const namespacedTools = [forwardTool(namespace), forwardTool(namespace)];
    // Tools come from forward discovery, which needs a live upstream MCP server, so mock the list.
    await mockRegistryEntries(page, 'tools', [defaultTool, ...namespacedTools]);

    await llmChat.goto();
    await expect(llmChat.toolCheckbox(defaultTool.name)).toBeVisible();
    for (const tool of namespacedTools) {
      await expect(llmChat.toolCheckbox(tool.name)).toBeHidden();
    }

    await llmChat.selectNamespace(namespace);

    for (const tool of namespacedTools) {
      await expect(llmChat.toolCheckbox(tool.name)).toBeVisible();
    }
    await expect(llmChat.toolCheckbox(defaultTool.name)).toBeHidden();
    await expect(llmChat.noToolsMessage()).toBeHidden();
  });
});
