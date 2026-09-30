import { type Locator, type Page } from "@playwright/test";
import { BasePage } from "./base.page";

export class BindingsPage extends BasePage {
  constructor(page: Page, baseUrl: string) {
    super(page, baseUrl);
  }

  async goto() {
    await this.navigateTo("/bindings");
  }

  bindingsTable(): Locator {
    return this.page.getByTestId("bindings-table");
  }

  detailModal(): Locator {
    return this.page.getByTestId("binding-detail-modal");
  }
}
