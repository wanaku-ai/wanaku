import { type Page, expect } from '@playwright/test';
import { BasePage } from './base.page';
import type { AgentEntry } from '../../../../ui/admin/src/models/agentEntry';

export class A2aAgentsPage extends BasePage {
  constructor(page: Page, baseUrl: string) {
    super(page, baseUrl);
  }

  async goto() {
    await this.navigateTo('/a2a-agents');
  }

  async clickAddAgent() {
    await this.page.getByRole('button', { name: 'Add A2A agent', exact: true }).click();
    await this.modal().waitFor({ state: 'visible' });
  }

  async fillAgentForm(agent: AgentEntry) {
    await this.page.locator('#agent-name').fill(agent.name);
    await this.page.locator('#agent-description').fill(agent.description ?? '');
    await this.page.locator('#agent-namespace').selectOption(agent.namespace ?? 'default');
    await this.page.locator('#agent-address').fill(agent.address);
    if (agent.cardAddress) {
      await this.page.locator('#agent-card-address').fill(agent.cardAddress);
    }
  }

  async clickEditAgent(name: string) {
    await this.rowWithText(name).getByRole('button', { name: 'Options', exact: true }).click();
    await this.page.getByRole('menuitem', { name: 'Edit', exact: true }).click();
    await this.modal().waitFor({ state: 'visible' });
  }

  async clickDeleteAgent(name: string) {
    await this.rowWithText(name).getByRole('button', { name: 'Options', exact: true }).click();
    await this.page.getByRole('menuitem', { name: 'Delete', exact: true }).click();
    await this.modal().waitFor({ state: 'visible' });
    await this.modal().getByRole('button', { name: 'Delete', exact: true }).click();
    await this.modal().waitFor({ state: 'hidden' });
  }

  async openDetails(name: string) {
    await this.rowWithText(name).getByRole('button', { name: 'Options', exact: true }).click();
    await this.page.getByRole('menuitem', { name: 'Details', exact: true }).click();
    await this.modal().waitFor({ state: 'visible' });
  }

  async closeDetails() {
    await this.modal().getByRole('button', { name: 'Close', exact: true }).click();
    await this.modal().waitFor({ state: 'hidden' });
  }

  async waitForAgent(name: string) {
    await expect(this.rowWithText(name)).toBeVisible();
  }

  async waitForAgentRemoved(name: string) {
    await expect(this.rowWithText(name)).toBeHidden();
  }
}
