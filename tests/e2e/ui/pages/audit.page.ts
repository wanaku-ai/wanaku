import { type Locator, type Page } from "@playwright/test";
import { BasePage } from "./base.page";

export class AuditPage extends BasePage {
  constructor(page: Page, baseUrl: string) {
    super(page, baseUrl);
  }

  async goto() {
    await this.navigateTo("/audit");
  }

  health(): Locator {
    return this.page.getByRole("region", { name: "Audit store health" });
  }

  filters(): Locator {
    return this.page.getByRole("form", { name: "Audit event filters" });
  }

  eventsTable(): Locator {
    return this.page.getByTestId("audit-table");
  }

  eventDetail(): Locator {
    return this.page.getByTestId("audit-event-detail");
  }
}
