import { type Page } from '@playwright/test';
import { BasePage } from './base.page';
import { Carbon } from '../helpers/carbon';

export class EvaluatorsPage extends BasePage {
  constructor(page: Page, baseUrl: string) {
    super(page, baseUrl);
  }

  async goto() {
    await this.navigateTo('/evaluators');
  }

  async clickAddEvaluator() {
    await this.page.locator(Carbon.buttonWithText('Add Evaluator')).click();
    await this.modal().waitFor({ state: 'visible' });
  }

  connectionSelect() {
    return this.modal().locator(Carbon.textInput('llm-connection'));
  }

  async isConnectionSelectDisabled(): Promise<boolean> {
    return this.connectionSelect().isDisabled();
  }

  async modalHasText(text: string): Promise<boolean> {
    const content = await this.modal().innerText();
    return content.includes(text);
  }
}
