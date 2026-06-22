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

  capabilityTile(title: string) {
    return this.page.locator('.capability-tile', { hasText: title });
  }

  async getCapabilityValue(tileTitle: string, label: string): Promise<string> {
    return this.capabilityTile(tileTitle)
      .locator('.capability-stat', { hasText: label })
      .locator('.capability-value')
      .innerText();
  }

  async clickRefresh() {
    await this.page.locator('button:has-text("Refresh")').click();
    await this.waitForDataLoad();
  }

  errorNotification() {
    return this.page.locator('.cds--toast-notification--error');
  }
}
