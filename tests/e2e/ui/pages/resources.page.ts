import { type Page, expect } from '@playwright/test';
import { BasePage } from './base.page';

export class ResourcesPage extends BasePage {
  constructor(page: Page, baseUrl: string) {
    super(page, baseUrl);
  }

  async goto() {
    await this.navigateTo('/resources');
  }

  async clickDeleteResource(name: string) {
    await this.rowWithText(name).getByRole('button', { name: 'Delete' }).click();
  }

  async waitForResourceInTable(name: string) {
    await expect(this.rowWithText(name)).toBeVisible({ timeout: 5_000 });
  }

  async waitForResourceRemoved(name: string) {
    await expect(this.rowWithText(name)).toBeHidden({ timeout: 5_000 });
  }
}
