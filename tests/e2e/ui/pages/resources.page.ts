import { type Page, expect } from '@playwright/test';
import { BasePage } from './base.page';
import { Carbon } from '../helpers/carbon';

export class ResourcesPage extends BasePage {
  constructor(page: Page, baseUrl: string) {
    super(page, baseUrl);
  }

  async goto() {
    await this.page.goto(`${this.baseUrl}#/resources`);
    await this.page.locator('button:has-text("Add Resource")').waitFor({ state: 'visible', timeout: 15_000 });
  }

  async clickAddResource() {
    await this.page.locator(Carbon.buttonWithText('Add Resource')).click();
    await this.modal().waitFor({ state: 'visible' });
  }

  async fillResourceForm(resource: { name: string; description: string; location: string }) {
    await this.page.locator(Carbon.textInput('resource-name')).fill(resource.name);
    await this.page.locator(Carbon.textInput('resource-description')).fill(resource.description);
    await this.page.locator(Carbon.textInput('resource-location')).fill(resource.location);
  }

  async clickEditResource(name: string) {
    await this.rowWithText(name).locator(Carbon.iconButton('Edit')).click();
    await this.modal().waitFor({ state: 'visible' });
  }

  async clickDeleteResource(name: string) {
    await this.rowWithText(name).locator(Carbon.iconButton('Delete')).click();
  }

  async hasResourceNamed(name: string): Promise<boolean> {
    return this.rowWithText(name).count().then(c => c > 0);
  }

  async isEmptyState(): Promise<boolean> {
    return this.page.locator('text=Start by adding resources').isVisible();
  }

  async waitForResourceInTable(name: string) {
    await expect(this.rowWithText(name)).toBeVisible({ timeout: 5_000 });
  }

  async waitForResourceRemoved(name: string) {
    await expect(this.rowWithText(name)).toBeHidden({ timeout: 5_000 });
  }
}
