import { type Page, type Locator } from '@playwright/test';
import { BasePage } from './base.page';

export class DownloadsPage extends BasePage {
  constructor(page: Page, baseUrl: string) {
    super(page, baseUrl);
  }

  async goto() {
    await this.navigateTo('/downloads');
  }

  downloadButtons(): Locator {
    return this.page.getByRole('link', { name: 'Download' });
  }

  viewAllPackagesLink(): Locator {
    return this.page.getByRole('link', { name: /View all packages/ });
  }
}
