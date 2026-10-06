import { type Page, expect } from '@playwright/test';
import { BasePage } from './base.page';
import { Carbon } from '../helpers/carbon';

export class ToolsPage extends BasePage {
  constructor(page: Page, baseUrl: string) {
    super(page, baseUrl);
  }

  async goto() {
    await this.navigateTo('/tools');
  }

  async waitForToolInTable(name: string) {
    await expect(this.rowWithText(name)).toBeVisible({ timeout: 5_000 });
  }

  enabledSwitch(name: string) {
    return this.rowWithText(name).getByRole('switch');
  }

  async clickEnabledToggle(name: string) {
    await this.rowWithText(name).locator(Carbon.toggleAppearance).click();
  }
}
