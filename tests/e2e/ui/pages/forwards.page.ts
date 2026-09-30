import { type Page, expect } from '@playwright/test';
import { BasePage } from './base.page';
import { Carbon } from '../helpers/carbon';

export class ForwardsPage extends BasePage {
  constructor(page: Page, baseUrl: string) {
    super(page, baseUrl);
  }

  async goto() {
    await this.navigateTo('/forwards');
  }

  async clickAddForward() {
    await this.page.locator(Carbon.buttonWithText('Add Forward')).click();
    await this.modal().waitFor({ state: 'visible' });
  }

  async fillForwardForm(forward: { name: string; address: string }) {
    await this.page.locator(Carbon.textInput('forward-name')).fill(forward.name);
    await this.page.locator(Carbon.textInput('forward-address')).fill(forward.address);
  }

  async getDiscoveryBindingOptions(): Promise<string[]> {
    return this.page.locator('#forward-discovery-binding option').allInnerTexts();
  }

  async getInvocationBindingOptions(): Promise<string[]> {
    return this.page.locator('#forward-invocation-binding option').allInnerTexts();
  }

  async selectDiscoveryBinding(id: string) {
    await this.page.locator('#forward-discovery-binding').selectOption(id);
  }

  async modalHasText(text: string): Promise<boolean> {
    const content = await this.modal().innerText();
    return content.includes(text);
  }

  async clickDetailForward(name: string) {
    await this.rowWithText(name).getByRole('button', { name: 'Details' }).click();
    await this.detailModal().waitFor({ state: 'visible' });
  }

  async clickEditForward(name: string) {
    await this.rowWithText(name).getByRole('button', { name: 'Edit' }).click();
    await this.modal().waitFor({ state: 'visible' });
  }

  async clickDeleteForward(name: string) {
    await this.rowWithText(name).getByRole('button', { name: 'Delete' }).click();
  }

  async waitForForwardInTable(name: string) {
    await expect(this.rowWithText(name)).toBeVisible({ timeout: 10_000 });
  }

  async waitForForwardRemoved(name: string) {
    await expect(this.rowWithText(name)).toBeHidden({ timeout: 5_000 });
  }

  detailModal() {
    return this.page.locator('.cds--modal.is-visible');
  }

  async getDetailModalHeading(): Promise<string> {
    return this.detailModal().locator(Carbon.modalHeading).innerText();
  }

  async getDetailField(label: string): Promise<string> {
    return this.detailModal()
      .locator(`strong:has-text("${label}")`)
      .locator('..')
      .innerText();
  }

  async detailModalHasText(text: string): Promise<boolean> {
    const content = await this.detailModal().innerText();
    return content.includes(text);
  }

  async closeDetailModal() {
    await this.detailModal().locator('button[aria-label="Close"]').click();
    await this.detailModal().waitFor({ state: 'hidden', timeout: 5_000 });
  }

  async getColumnHeaders(): Promise<string[]> {
    await this.page.locator(Carbon.dataTable).waitFor({ state: 'visible', timeout: 5_000 });
    return this.page.locator(`${Carbon.dataTable} th`).allInnerTexts();
  }

  async getRowStatusText(name: string): Promise<string> {
    const row = this.rowWithText(name);
    const tag = row.locator('.cds--tag');
    await tag.waitFor({ state: 'visible', timeout: 5_000 });
    return tag.innerText();
  }
}
