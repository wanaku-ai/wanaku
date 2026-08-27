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
}
