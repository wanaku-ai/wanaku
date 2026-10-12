import { type Page } from "@playwright/test";
import { BasePage } from "./base.page";

export class PolicySimulationPage extends BasePage {
  constructor(page: Page, baseUrl: string) {
    super(page, baseUrl);
  }
  async goto() {
    await this.navigateTo("/policy-simulation");
  }
  candidate() {
    return this.page.getByLabel("Candidate configuration (YAML)");
  }
  report(name: "Validation report" | "Simulation report" | "Replay report") {
    return this.page.getByRole("region", { name, exact: true });
  }
  async selectAuditEvent(id: string) {
    await this.page.getByLabel("Find audit event by ID").fill(id);
    await this.page
      .getByRole("button", { name: "Find event", exact: true })
      .click();
    await this.page
      .getByLabel(`Select audit event ${id}`, { exact: true })
      .press("Space");
  }
}
