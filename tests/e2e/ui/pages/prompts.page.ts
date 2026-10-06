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

  async fillPromptForm(prompt: { name: string; description: string }) {
    await this.page.locator(Carbon.textInput('prompt-name')).fill(prompt.name);
    await this.page.locator(Carbon.textInput('prompt-description')).fill(prompt.description);
  }

  async waitForPromptInTable(name: string) {
    await expect(this.rowWithText(name)).toBeVisible({ timeout: 5_000 });
  }

  enabledSwitch(name: string) {
    return this.rowWithText(name).getByRole('switch');
  }

  async clickEnabledToggle(name: string) {
    await this.rowWithText(name).locator(Carbon.toggleAppearance).click();
  }
}
