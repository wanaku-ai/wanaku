import { type Page, type Locator } from '@playwright/test';
import { Carbon } from '../helpers/carbon';

export class BasePage {
  constructor(
    protected readonly page: Page,
    protected readonly baseUrl: string,
  ) {}

  async navigateTo(hashPath: string) {
    await this.page.goto(`${this.baseUrl}#${hashPath}`);
    await this.waitForDataLoad();
  }

  async waitForDataLoad() {
    await this.page.locator('text=Loading...').waitFor({ state: 'hidden', timeout: 15_000 });
  }

  async getPageTitle(): Promise<string> {
    return this.page.locator('h1.title').innerText();
  }

  async getPageDescription(): Promise<string> {
    return this.page.locator('p.description').innerText();
  }

  modal(): Locator {
    return this.page.locator(Carbon.modal);
  }

  async getModalHeading(): Promise<string> {
    return this.modal().locator(Carbon.modalHeading).innerText();
  }

  async submitModal() {
    await this.modal().locator(Carbon.modalFooterPrimary).click();
    await this.modal().waitFor({ state: 'hidden', timeout: 10_000 });
  }

  async cancelModal() {
    await this.modal().locator(Carbon.modalFooterSecondary).click();
    await this.modal().waitFor({ state: 'hidden', timeout: 5_000 });
  }

  tableRows(): Locator {
    return this.page.locator(Carbon.tableRow);
  }

  rowWithText(text: string): Locator {
    return this.page.locator(Carbon.tableRow, { hasText: text });
  }
}
