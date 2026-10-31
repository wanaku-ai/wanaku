import { test, expect } from "@playwright/test";
import { ActionPoliciesPage } from "../pages/action-policies.page";
import { mockActionPolicies } from "../helpers/action-policy-mock";
import { actionPolicy as policy, actionPolicyRevision } from "../helpers/test-data";

const routerUrl = process.env.WANAKU_ROUTER_URL ?? "http://localhost:8080";
const activeRevision = actionPolicyRevision();
const rejectedRevision = actionPolicyRevision({
  id: 2,
  activated_at: null,
  status: "rejected",
  failure_reason: "rule selectors must not be empty",
});

test.describe("Action Policies", () => {
  let policies: ActionPoliciesPage;

  test.beforeEach(async ({ page }) => {
    policies = new ActionPoliciesPage(page, `${routerUrl}/admin/`);
  });

  test("displays the active policy and revision history", async ({ page }) => {
    await mockActionPolicies(page, {
      active: { json: { data: { revision: activeRevision, policy } } },
      revisions: [activeRevision],
      policy,
    });
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
    await mockActionPolicies(page, {
      active: { status: 404, json: { error: "no action policy revision found" } },
      revisions: [rejectedRevision],
      policy,
    });

    await policies.goto();

    await expect(policies.activePolicy()).toContainText("No active policy");
    await expect(policies.revisionHistory()).toContainText("Revision 2 — rejected");
    await policies.revisionHistory().getByRole("button", { name: /Revision 2/ }).click();
    await expect(policies.revisionHistory()).toContainText("rule selectors must not be empty");
    await expect(policies.revisionHistory()).toContainText("protect-production");
  });

  test("uses the 404 status, not the message, for the empty state", async ({ page }) => {
    await mockActionPolicies(page, {
      active: { status: 404, contentType: "text/plain", body: "Not Found" },
      revisions: [],
      policy,
    });

    await policies.goto();

    await expect(policies.activePolicy()).toContainText("No active policy");
    await expect(policies.revisionHistory()).toContainText("No policy revisions are available.");
    await expect(policies.errorNotification()).toHaveCount(0);
  });

  test("displays the server error message when the active policy fails to load", async ({ page }) => {
    await mockActionPolicies(page, {
      active: { status: 500, json: { data: null, error: "policy store unavailable" } },
      revisions: [activeRevision],
      policy,
    });

    await policies.goto();

    await expect(policies.errorNotification()).toContainText("Action policies are unavailable");
    await expect(policies.errorNotification()).toContainText("policy store unavailable");
    await expect(policies.activePolicy()).toHaveCount(0);
  });

  test("displays an error, not the empty state, when a revision is missing", async ({ page }) => {
    await mockActionPolicies(page, {
      active: { status: 404, json: { error: "no action policy revision found" } },
      revisions: [rejectedRevision],
      policy,
      details: { 2: { status: 404, json: { data: null, error: "revision 2 not found" } } },
    });

    await policies.goto();

    await expect(policies.errorNotification()).toContainText("revision 2 not found");
    await expect(policies.activePolicy()).toHaveCount(0);
  });

  test("keeps the status message when the error body is malformed", async ({ page }) => {
    await mockActionPolicies(page, {
      active: { status: 502, contentType: "application/json", body: '{"error": "trunc' },
      revisions: [],
      policy,
    });

    await policies.goto();

    await expect(policies.errorNotification()).toContainText("Request failed with status 502");
  });
});
