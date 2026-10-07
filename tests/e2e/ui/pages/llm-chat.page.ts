import { type Page, type Locator } from '@playwright/test';
import { BasePage } from './base.page';

export class LlmChatPage extends BasePage {
  constructor(page: Page, baseUrl: string) {
    super(page, baseUrl);
  }

  async goto() {
    await this.navigateTo('/llmchat');
  }

  apiKeyInput(): Locator {
    return this.page.locator('#api-key');
  }

  async setApiKey(value: string) {
    await this.apiKeyInput().fill(value);
  }

  async getApiKey(): Promise<string> {
    return this.apiKeyInput().inputValue();
  }

  storeSettingsToggle(): Locator {
    return this.page.locator('#enabledLocalStorage');
  }

  storeApiKeyToggle(): Locator {
    return this.page.locator('#enabledApiKeyStorage');
  }

  securityWarning(): Locator {
    // Scope to the API key banner by its title so an unrelated warning notification cannot match.
    return this.page.locator('.cds--inline-notification--warning:has-text("Security warning")');
  }

  // Reads the raw persisted config payload from local storage, or null when nothing is stored.
  async storedConfigRaw(): Promise<string | null> {
    return this.page.evaluate(() => localStorage.getItem('llmConfig'));
  }

  // Carbon renders the toggle as a visually hidden <button role="switch"> paired with a clickable
  // <label for="{id}">. State is read from the button; the label is clicked to toggle it.
  private async setToggle(id: string, on: boolean) {
    const button = this.page.locator(`#${id}`);
    const checked = (await button.getAttribute('aria-checked')) === 'true';
    if (checked !== on) {
      await this.page.locator(`label[for="${id}"]`).click();
    }
  }

  async setStoreSettings(on: boolean) {
    await this.setToggle('enabledLocalStorage', on);
  }

  async setStoreApiKey(on: boolean) {
    await this.setToggle('enabledApiKeyStorage', on);
  }

  namespaceSelect(): Locator {
    return this.page.locator('select#namespace');
  }

  async selectNamespace(name: string) {
    await this.namespaceSelect().selectOption(name);
  }

  // Each tool is a Carbon checkbox whose input id is the tool name.
  toolCheckbox(name: string): Locator {
    return this.page.locator(`label[for="${name}"]`);
  }

  noToolsMessage(): Locator {
    return this.page.getByText('No tools available', { exact: true });
  }

  async reload() {
    await this.page.reload();
    await this.apiKeyInput().waitFor({ state: 'visible', timeout: 15_000 });
  }
}
