import { type Page, type Locator } from '@playwright/test';
import { BasePage } from './base.page';

export class CliDownloadsPage extends BasePage {
  constructor(page: Page, baseUrl: string) {
    super(page, baseUrl);
  }

  async goto() {
    await this.navigateTo('/cli-downloads');
  }

  sectionHeading(label: string): Locator {
    return this.page.locator('.cli-package-section-heading', { hasText: label });
  }

  packageTile(name: string): Locator {
    return this.page.locator('.cli-package-tile', { hasText: name });
  }

  downloadLink(tile: Locator): Locator {
    return tile.locator('a:has-text("Download")');
  }
}
