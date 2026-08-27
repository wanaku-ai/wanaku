import { test, expect } from "@playwright/test";
import { ActionPoliciesPage } from "../pages/action-policies.page";

const routerUrl = process.env.WANAKU_ROUTER_URL ?? "http://localhost:8080";
const activeRevision = {
  id: 1,
  created_at: "2026-08-27T10:00:00Z",
  activated_at: "2026-08-27T10:00:00Z",
  status: "active",
  checksum: "test-checksum",
  origin: "startup",
  actor: null,
  failure_reason: null,
};
const policy = {
  rules: [{
    id: "protect-production",
    description: "Protect production services",
    effect: "deny",
    selectors: { operation: "tools/call", target_type: "tool", target_name: { matcher: "glob", value: "restart*" } },
    predicates: [{ operator: "equals", pointer: "/arguments/service", value: "production" }],
    reason_code: "production_restart_denied",
    message: "Production restarts are not permitted.",
    metadata: { owner: "platform" },
  }],
};

test.describe("Action Policies", () => {
  let policies: ActionPoliciesPage;

  test.beforeEach(async ({ page }) => {
    policies = new ActionPoliciesPage(page, `${routerUrl}/admin/`);
  });

  test("displays the active policy and revision history", async ({ page }) => {
    await page.route("**/api/v1/action-policies", (route) => route.fulfill({ json: { data: { revision: activeRevision, policy } } }));
    await page.route("**/api/v1/action-policies/revisions", (route) => route.fulfill({ json: { data: [activeRevision] } }));
    await page.route("**/api/v1/action-policies/revisions/1", (route) => route.fulfill({ json: { data: { revision: activeRevision, policy } } }));
    await policies.goto();
    await expect.poll(() => policies.getPageTitle()).toBe("Action Policies");
    await expect(policies.activePolicy()).toContainText("Revision");
    await expect(policies.activePolicy()).toContainText("Rules");
    await expect(policies.revisionHistory()).toBeVisible();
    await policies.activePolicy().getByRole("button", { name: /protect-production/ }).click();
    await expect(policies.activePolicy()).toContainText("production_restart_denied");
    await expect(policies.activePolicy()).toContainText("/arguments/service");
  });

  test("displays rejected revisions when no policy is active", async ({ page }) => {
    const rejectedRevision = {
      ...activeRevision,
      id: 2,
      activated_at: null,
      status: "rejected",
      failure_reason: "rule selectors must not be empty",
    };
    await page.route("**/api/v1/action-policies", (route) => route.fulfill({ status: 404, json: { error: "no action policy revision found" } }));
    await page.route("**/api/v1/action-policies/revisions", (route) => route.fulfill({ json: { data: [rejectedRevision] } }));
    await page.route("**/api/v1/action-policies/revisions/2", (route) => route.fulfill({ json: { data: { revision: rejectedRevision, policy } } }));

    await policies.goto();

    await expect(policies.activePolicy()).toContainText("No active policy");
    await expect(policies.revisionHistory()).toContainText("Revision 2 — rejected");
    await policies.revisionHistory().getByRole("button", { name: /Revision 2/ }).click();
    await expect(policies.revisionHistory()).toContainText("rule selectors must not be empty");
    await expect(policies.revisionHistory()).toContainText("protect-production");
  });
});
