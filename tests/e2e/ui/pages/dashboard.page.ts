import { type Page } from '@playwright/test';
import { BasePage } from './base.page';

export class DashboardPage extends BasePage {
  constructor(page: Page, baseUrl: string) {
    super(page, baseUrl);
  }

  async goto() {
    await this.navigateTo('/');
  }

  statTile(label: string) {
    return this.page.locator('.stat-tile', { hasText: label });
  }

  async getStatValue(label: string): Promise<string> {
    return this.statTile(label).locator('.stat-value').innerText();
  }

  async clickRefresh() {
    await this.page.locator('button:has-text("Refresh")').click();
    await this.waitForDataLoad();
  }

  errorNotification() {
    return this.page.locator('.cds--toast-notification--error');
  }
}
