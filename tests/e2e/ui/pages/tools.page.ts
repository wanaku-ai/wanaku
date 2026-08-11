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

  async clickAddTool() {
    await this.page.locator(Carbon.buttonWithText('Add Tool')).click();
    await this.modal().waitFor({ state: 'visible' });
  }

  async fillToolForm(tool: { name: string; description: string; uri: string }) {
    await this.page.locator(Carbon.textInput('tool-name')).fill(tool.name);
    await this.page.locator(Carbon.textInput('tool-description')).fill(tool.description);
    await this.page.locator(Carbon.textInput('tool-uri')).fill(tool.uri);
  }

  async clickDeleteTool(name: string) {
    await this.rowWithText(name).getByRole('button', { name: 'Delete' }).click();
  }

  async waitForToolInTable(name: string) {
    await expect(this.rowWithText(name)).toBeVisible({ timeout: 5_000 });
  }

  async waitForToolRemoved(name: string) {
    await expect(this.rowWithText(name)).toBeHidden({ timeout: 5_000 });
  }
}
