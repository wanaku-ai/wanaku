import { type Page, expect } from '@playwright/test';
import { BasePage } from './base.page';
import { Carbon } from '../helpers/carbon';

export class NamespacesPage extends BasePage {
  constructor(page: Page, baseUrl: string) {
    super(page, baseUrl);
  }

  async goto() {
    await this.navigateTo('/namespaces');
  }

  async clickAddNamespace() {
    await this.page.locator(Carbon.buttonWithText('Create Namespace')).click();
    await this.modal().waitFor({ state: 'visible' });
  }

  async fillNamespaceForm(name: string) {
    await this.page.locator(Carbon.textInput('namespace-name')).fill(name);
  }

  async isSubmitDisabled(): Promise<boolean> {
    const btn = this.modal().locator(Carbon.modalFooterPrimary);
    return btn.isDisabled();
  }

  async clickDeleteNamespace(name: string) {
    await this.rowWithText(name).getByRole('button', { name: 'Delete' }).click();
  }

  async waitForNamespaceInTable(name: string) {
    await expect(this.rowWithText(name)).toBeVisible({ timeout: 5_000 });
  }

  async waitForNamespaceRemoved(name: string) {
    await expect(this.rowWithText(name)).toBeHidden({ timeout: 5_000 });
  }

  async getColumnHeaders(): Promise<string[]> {
    await this.page.locator(Carbon.dataTable).waitFor({ state: 'visible', timeout: 5_000 });
    return this.page.locator(`${Carbon.dataTable} th`).allInnerTexts();
  }
}
