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
