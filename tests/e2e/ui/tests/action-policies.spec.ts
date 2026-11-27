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
    await expect(policies.revisionHistory().getByRole("button", { name: /Roll back/ })).toHaveCount(0);
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

  test.describe("editing", () => {
    const revision2 = actionPolicyRevision({ id: 2 });
    const savedPolicy = {
      rules: [
        ...policy.rules,
        { id: "audit-prompts", effect: "deny", selectors: { operation: "prompts/get" } },
      ],
    };

    test("activates a new rule only after confirmation", async ({ page }) => {
      const { updateRequests } = await mockActionPolicies(page, {
        active: { json: { data: { revision: activeRevision, policy } } },
        revisions: [activeRevision],
        policy,
        updates: [
          {
            json: { data: { revision: revision2, policy: savedPolicy } },
            thenActive: { json: { data: { revision: revision2, policy: savedPolicy } } },
          },
        ],
      });
      await policies.goto();

      await policies.startEditing();
      await policies.addRule("audit-prompts", "prompts/get");
      await policies.clickActivate();

      await expect(policies.confirmation()).toContainText("It replaces revision 1");
      expect(updateRequests).toHaveLength(0);

      await policies.confirmActivation();

      await expect(policies.activePolicy()).toContainText("Revision 2 is now active");
      expect(updateRequests).toHaveLength(1);
      expect(updateRequests[0]).toMatchObject({
        expected_revision: 1,
        policy: { rules: [{ id: "protect-production" }, { id: "audit-prompts", effect: "deny" }] },
      });
      await expect(policies.activePolicy()).toContainText("audit-prompts");
    });

    test("does not send an update when the confirmation is cancelled", async ({ page }) => {
      const { updateRequests } = await mockActionPolicies(page, {
        active: { json: { data: { revision: activeRevision, policy } } },
        revisions: [activeRevision],
        policy,
      });
      await policies.goto();

      await policies.startEditing();
      await policies.addRule("audit-prompts", "prompts/get");
      await policies.clickActivate();
      await policies.cancelModal();

      await expect(policies.editor()).toBeVisible();
      expect(updateRequests).toHaveLength(0);
    });

    test("shows validation problems before it sends the policy", async ({ page }) => {
      const { updateRequests } = await mockActionPolicies(page, {
        active: { json: { data: { revision: activeRevision, policy } } },
        revisions: [activeRevision],
        policy,
      });
      await policies.goto();

      await policies.startEditing();
      await policies.openRule(/protect-production/);
      await policies.editor().getByLabel("JSON Pointer").fill("arguments/service");
      await policies.editor().getByRole("button", { name: "Add rule" }).click();
      await policies.clickActivate();

      await expect(policies.editor()).toContainText("Fix these problems before you activate the policy");
      await expect(policies.editor()).toContainText('A JSON Pointer must start with "/"');
      await expect(policies.editor()).toContainText("Enter a rule ID.");
      await expect(policies.editor()).toContainText("Select at least one of namespace");
      await expect(policies.confirmation()).toHaveCount(0);
      expect(updateRequests).toHaveLength(0);
    });

    test("keeps the draft when the server rejects the policy with 422", async ({ page }) => {
      await mockActionPolicies(page, {
        active: { json: { data: { revision: activeRevision, policy } } },
        revisions: [activeRevision],
        policy,
        updates: [{ status: 422, json: { data: null, error: "configuration rejected: rule 'audit-prompts' is unknown" } }],
      });
      await policies.goto();

      await policies.startEditing();
      await policies.addRule("audit-prompts", "prompts/get");
      await policies.clickActivate();
      await policies.confirmActivation();

      await expect(policies.editor()).toContainText("The server rejected the policy");
      await expect(policies.editor()).toContainText("configuration rejected: rule 'audit-prompts' is unknown");
      await expect(policies.editor().getByLabel("Rule ID").last()).toHaveValue("audit-prompts");
      await expect(policies.editor().getByLabel("Operation").last()).toHaveValue("prompts/get");
    });

    test("explains a 409 conflict and reloads the active revision", async ({ page }) => {
      const newerPolicy = { rules: [{ id: "newer-rule", effect: "allow", selectors: { operation: "tools/list" } }] };
      const newerRevision = actionPolicyRevision({ id: 3 });
      await mockActionPolicies(page, {
        active: { json: { data: { revision: activeRevision, policy } } },
        revisions: [activeRevision],
        policy,
        updates: [
          {
            status: 409,
            json: { data: null, error: "expected active revision 1, but current is 3" },
            thenActive: { json: { data: { revision: newerRevision, policy: newerPolicy } } },
          },
        ],
      });
      await policies.goto();

      await policies.startEditing();
      await policies.addRule("audit-prompts", "prompts/get");
      await policies.clickActivate();
      await policies.confirmActivation();

      await expect(policies.editor()).toContainText("Another change was activated first");
      await expect(policies.editor()).toContainText("expected active revision 1, but current is 3");
      await expect(policies.editor().getByLabel("Rule ID").last()).toHaveValue("audit-prompts");

      await policies.editor().getByRole("button", { name: "Reload active revision" }).click();

      await expect(policies.editor()).toHaveCount(0);
      await expect(policies.activePolicy()).toContainText("newer-rule");
      await expect(policies.activePolicy()).not.toContainText("audit-prompts");
    });

    test("applies pasted JSON to the draft", async ({ page }) => {
      const { updateRequests } = await mockActionPolicies(page, {
        active: { status: 404, json: { error: "no action policy revision found" } },
        revisions: [],
        policy,
        updates: [{ json: { data: { revision: revision2, policy: savedPolicy } } }],
      });
      await policies.goto();

      await policies.startEditing();
      await policies.editor().getByRole("tab", { name: "JSON editor" }).click();
      await policies.editor().getByLabel("Policy JSON").fill(JSON.stringify(savedPolicy));
      await policies.editor().getByRole("tab", { name: "Structured editor" }).click();

      await expect(policies.editor()).toContainText("protect-production");
      await expect(policies.editor()).toContainText("audit-prompts");

      await policies.clickActivate();
      await expect(policies.confirmation()).toContainText("first active policy");
      await policies.confirmActivation();

      await expect.poll(() => updateRequests.length).toBe(1);
      expect(updateRequests[0]).toMatchObject({ expected_revision: null, policy: { rules: [{}, {}] } });
    });

    test("reports JSON that is not valid", async ({ page }) => {
      await mockActionPolicies(page, {
        active: { json: { data: { revision: activeRevision, policy } } },
        revisions: [activeRevision],
        policy,
      });
      await policies.goto();

      await policies.startEditing();
      await policies.editor().getByRole("tab", { name: "JSON editor" }).click();
      await policies.editor().getByLabel("Policy JSON").fill("{ not json");
      await policies.clickActivate();

      await expect(policies.editor()).toContainText("The text is not valid JSON");
      await expect(policies.confirmation()).toHaveCount(0);
    });
  });
});

test.describe("Action policy revision rollback", () => {
  const prior = { ...actionPolicyRevision({ id: 2, status: "superseded" }), actor: "operator" };
  const current = actionPolicyRevision({ id: 3 });
  const newer = actionPolicyRevision({ id: 4 });
  const added = { id: "prior-only", effect: "allow", selectors: { operation: "tools/list" } };
  const removed = { ...added, id: "active-only" };
  const currentPolicy = { rules: [...policy.rules, removed] };
  const priorPolicy = { rules: [{ ...policy.rules[0], effect: "allow" }, added] };
  const reply = (revision: typeof current, candidate: typeof currentPolicy) => ({
    json: { data: { revision, policy: candidate } },
  });
  const history = {
    active: reply(current, currentPolicy),
    revisions: [current, prior],
    policy: currentPolicy,
    details: { 2: { json: { data: { revision: prior, policy: priorPolicy } } } },
  };

  let policies: ActionPoliciesPage;

  test.beforeEach(async ({ page }) => {
    policies = new ActionPoliciesPage(page, `${routerUrl}/admin/`);
  });

  test("compares a selected revision with the active policy by rule ID", async ({ page }) => {
    await mockActionPolicies(page, history);
    await policies.goto();
    const mountedComparisons = policies.revisionHistory().locator('[aria-label="Rule comparison"]');
    await expect(policies.revisionHistory().getByRole("button", { name: "Revision 2 — superseded" })).toBeVisible();
    await expect(mountedComparisons).toHaveCount(0);
    await policies.openRevision(2, "superseded");

    const comparison = policies.revisionHistory().getByRole("region", { name: "Rule comparison" });
    await expect(comparison.getByRole("heading")).toHaveText("Comparison with active revision 3");
    await expect(comparison.getByText("Added — prior-only")).toBeVisible();
    await expect(comparison.getByText("Removed — active-only")).toBeVisible();
    const modified = comparison.locator("details").filter({ hasText: "Modified — protect-production" });
    await modified.getByText("Modified — protect-production").click();
    await expect(modified.getByText('"effect": "deny"', { exact: false })).toBeVisible();
    await expect(modified.getByText('"effect": "allow"', { exact: false })).toBeVisible();
    await expect(policies.revisionHistory().getByText("operator", { exact: true })).toBeVisible();
    await expect(policies.revisionHistory().getByText("test-checksum", { exact: true }).last()).toBeVisible();
    await policies.openRevision(2, "superseded");
    await expect(mountedComparisons).toHaveCount(0);
  });

  test("compares rejected rules with duplicate IDs and hides rollback", async ({ page }) => {
    const duplicateKeyWarnings: string[] = [];
    page.on("console", (message) => {
      if (message.text().includes("same key")) duplicateKeyWarnings.push(message.text());
    });
    const rejected = { ...rejectedRevision, failure_reason: "duplicate rule ID" };
    await mockActionPolicies(page, {
      ...history,
      revisions: [current, rejected],
      details: { 2: reply(rejected, { rules: [priorPolicy.rules[0], policy.rules[0], removed] }) },
    });
    await policies.goto();
    await policies.openRevision(2, "rejected");

    const comparison = policies.revisionHistory().getByRole("region", { name: "Rule comparison" });
    const modified = comparison.locator("details").filter({ hasText: "Modified — protect-production" });
    await modified.getByText("Modified — protect-production").click();
    await expect(modified.getByText('"effect": "allow"', { exact: false })).toBeVisible();
    const policyRules = policies.revisionHistory();
    const allow = policyRules.getByRole("button", { name: "protect-production — allow", exact: true });
    const deny = policyRules.getByRole("button", { name: "protect-production — deny", exact: true });
    await allow.click();
    await deny.click();
    await expect(allow).toHaveAttribute("aria-expanded", "true");
    await expect(deny).toHaveAttribute("aria-expanded", "true");
    await allow.click();
    await expect(allow).toHaveAttribute("aria-expanded", "false");
    await expect(deny).toHaveAttribute("aria-expanded", "true");
    expect(duplicateKeyWarnings).toEqual([]);
    await expect(policyRules).toContainText("duplicate rule ID");
    await expect(policies.rollbackButton(2)).toHaveCount(0);
  });

  test("confirms rollback before activating the prior policy as a new revision", async ({ page }) => {
    const { activationRequests } = await mockActionPolicies(page, {
      ...history,
      activations: [{ ...reply(newer, priorPolicy), thenActive: reply(newer, priorPolicy) }],
    });
    await policies.goto();
    await policies.openRevision(2, "superseded");
    await policies.rollbackButton(2).click();
    await expect(policies.confirmation()).toContainText("Rollback creates a new revision from revision 2");
    await expect(policies.confirmation()).toContainText("It replaces active revision 3");
    await policies.cancelModal();
    expect(activationRequests).toEqual([]);

    await policies.rollbackButton(2).click();
    await policies.confirmation().getByRole("button", { name: "Roll back", exact: true }).click();
    await expect(policies.activePolicy()).toContainText("Revision 4 is now active");
    await expect(policies.activePolicy()).toContainText("prior-only");
    await policies.openRevision(4, "active");
    await expect(policies.revisionHistory().getByRole("button", { name: "prior-only — allow" })).toBeVisible();
    await expect(policies.revisionHistory().getByRole("button", { name: "Revision 3 — superseded" })).toBeVisible();
    await expect(policies.revisionHistory().getByRole("button", { name: "Revision 2 — superseded" })).toBeVisible();
    expect(activationRequests).toEqual([{ id: 2, body: { expected_revision: 3 } }]);
  });

  test("keeps the comparison on conflict and requires confirmation against the new active revision", async ({ page }) => {
    const restored = actionPolicyRevision({ id: 5 });
    const { activationRequests } = await mockActionPolicies(page, {
      ...history,
      activations: [
        {
          status: 409,
          json: { error: "expected active revision 3, but current is 4" },
          thenActive: reply(newer, policy),
        },
        { ...reply(restored, priorPolicy), thenActive: reply(restored, priorPolicy) },
      ],
    });
    await policies.goto();
    await policies.openRevision(2, "superseded");
    await policies.rollbackButton(2).click();
    await policies.confirmation().getByRole("button", { name: "Roll back", exact: true }).click();

    await expect(policies.confirmation()).toContainText("The active revision changed");
    await expect(policies.confirmation()).toContainText("expected active revision 3, but current is 4");
    await policies.confirmation().getByRole("button", { name: "Reload active revision" }).click();
    const comparison = policies.revisionHistory().getByRole("region", { name: "Rule comparison" }).filter({
      hasText: "Added — prior-only",
    });
    await expect(comparison.getByRole("heading")).toHaveText("Comparison with active revision 4");
    await expect(policies.revisionHistory().getByRole("button", { name: "Revision 4 — active" })).toBeVisible();
    await expect(policies.revisionHistory().getByRole("button", { name: "Revision 3 — superseded" })).toBeVisible();
    expect(activationRequests).toHaveLength(1);

    await policies.rollbackButton(2).click();
    await expect(policies.confirmation()).toContainText("It replaces active revision 4");
    await policies.confirmation().getByRole("button", { name: "Roll back", exact: true }).click();
    await expect(policies.activePolicy()).toContainText("Revision 5 is now active");
    await expect(policies.revisionHistory().getByRole("button", { name: "Revision 5 — active" })).toBeVisible();
    await expect(policies.revisionHistory().getByRole("button", { name: "Revision 4 — superseded" })).toBeVisible();
    expect(activationRequests[1]).toEqual({ id: 2, body: { expected_revision: 4 } });
  });
});
