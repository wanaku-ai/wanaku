import { type Page, expect } from '@playwright/test';
import { BasePage } from './base.page';
import { Carbon } from '../helpers/carbon';

export class ResourcesPage extends BasePage {
  constructor(page: Page, baseUrl: string) {
    super(page, baseUrl);
  }

  async goto() {
    await this.navigateTo('/resources');
  }

  async waitForResourceInTable(name: string) {
    await expect(this.rowWithText(name)).toBeVisible({ timeout: 5_000 });
  }

  enabledSwitch(name: string) {
    return this.rowWithText(name).getByRole('switch');
  }

  async clickEnabledToggle(name: string) {
    await this.rowWithText(name).locator(Carbon.toggleAppearance).click();
  }
}
