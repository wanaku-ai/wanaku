import { type Page, expect } from '@playwright/test';
import { BasePage } from './base.page';
import { Carbon } from '../helpers/carbon';

export class PromptsPage extends BasePage {
  constructor(page: Page, baseUrl: string) {
    super(page, baseUrl);
  }

  async goto() {
    await this.navigateTo('/prompts');
  }

  async clickAddPrompt() {
    await this.page.locator(Carbon.buttonWithText('Add Prompt')).click();
    await this.modal().waitFor({ state: 'visible' });
  }

  async fillPromptForm(prompt: { name: string; description: string }) {
    await this.page.locator(Carbon.textInput('prompt-name')).fill(prompt.name);
    await this.page.locator(Carbon.textInput('prompt-description')).fill(prompt.description);
  }

  async clickDeletePrompt(name: string) {
    await this.rowWithText(name).locator(Carbon.iconButton('Delete')).click();
  }

  async hasPromptNamed(name: string): Promise<boolean> {
    return this.rowWithText(name).count().then(c => c > 0);
  }

  async isEmptyState(): Promise<boolean> {
    return this.page.locator('text=Start by adding prompts').isVisible();
  }

  async waitForPromptInTable(name: string) {
    await expect(this.rowWithText(name)).toBeVisible({ timeout: 5_000 });
  }

  async waitForPromptRemoved(name: string) {
    await expect(this.rowWithText(name)).toBeHidden({ timeout: 5_000 });
  }
}
