import { type Locator, type Page } from "@playwright/test";
import { BasePage } from "./base.page";

export class ActionPoliciesPage extends BasePage {
  constructor(page: Page, baseUrl: string) {
    super(page, baseUrl);
  }

  async goto() {
    await this.navigateTo("/action-policies");
  }

  activePolicy(): Locator {
    return this.page.getByRole("region", { name: "Active policy" });
  }

  revisionHistory(): Locator {
    return this.page.getByRole("region", { name: "Revision history" });
  }

  errorNotification(): Locator {
    return this.page.locator(".cds--inline-notification--error");
  }

  async startEditing() {
    await this.activePolicy().getByRole("button", { name: /Edit policy|Create policy/ }).click();
  }

  editor(): Locator {
    return this.activePolicy().locator(".policy-editor");
  }

  async openRule(title: string | RegExp) {
    await this.editor().getByRole("button", { name: title }).click();
  }

  async addRule(id: string, operation: string) {
    await this.editor().getByRole("button", { name: "Add rule" }).click();
    await this.editor().getByLabel("Rule ID").last().fill(id);
    await this.editor().getByLabel("Operation").last().fill(operation);
  }

  async clickActivate() {
    await this.editor().getByRole("button", { name: "Activate policy" }).click();
  }

  confirmation(): Locator {
    return this.modal();
  }

  async confirmActivation() {
    await this.modal().getByRole("button", { name: "Activate", exact: true }).click();
  }
}
