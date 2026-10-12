import { test, expect } from "@playwright/test";
import { PolicySimulationPage } from "../pages/policy-simulation.page";
import {
  simulationAction,
  simulationAuditEvent,
  simulationIdentity,
  simulationExampleCandidate,
  simulationReport,
  simulationReplay,
} from "../helpers/test-data";

const routerUrl = process.env.WANAKU_ROUTER_URL ?? "http://localhost:8080";

test.describe("Policy Simulation", () => {
  let simulation: PolicySimulationPage;
  test.beforeEach(async ({ page }) => {
    simulation = new PolicySimulationPage(page, `${routerUrl}/admin/`);
    await page.route("**/api/v1/audit/events**", (route) => {
      const path = new URL(route.request().url()).pathname;
      const id = path.split("/").at(-1);
      return route.fulfill({
        json: {
          data:
            id === "events"
              ? {
                  events: [
                    simulationAuditEvent("audit-1"),
                    simulationAuditEvent("audit-2"),
                  ],
                  total: 2,
                  offset: 0,
                  limit: 20,
                }
              : simulationAuditEvent(id ?? "audit-1"),
        },
      });
    });

    await page.route("**/api/v1/action-policies", (route) =>
      route.fulfill({ status: 404, json: { error: "no active revision" } }),
    );
    await page.route("**/api/v1/evaluators/revisions/active", (route) =>
      route.fulfill({ status: 404, json: { error: "no active revision" } }),
    );

    await page.route("**/api/v1/tools", (route) =>
      route.fulfill({
        json: {
          data: [
            {
              name: "summary",
              namespace: "default",
              description: "",
              type: "mcp-forward",
              uri: "http://example.com/mcp",
              inputSchema: {},
            },
            {
              name: "summary",
              namespace: "production",
              description: "",
              type: "mcp-forward",
              uri: "http://example.com/mcp",
              inputSchema: {},
            },
            {
              name: "restart",
              namespace: "production",
              description: "",
              type: "mcp-forward",
              uri: "http://example.com/mcp",
              inputSchema: {},
            },
          ],
        },
      }),
    );
    await page.route("**/api/v1/prompts", (route) =>
      route.fulfill({
        json: {
          data: [
            { name: "summary", namespace: "production" },
            { name: "default-prompt", namespace: "default" },
          ],
        },
      }),
    );
    await page.route("**/api/v1/resources", (route) =>
      route.fulfill({
        json: {
          data: [
            {
              name: "report",
              location: "file:///report",
              type: "file",
              namespace: "production",
            },
            {
              name: "default-resource",
              location: "file:///default-report",
              type: "file",
            },
          ],
        },
      }),
    );

    await page.route("**/api/v1/namespaces", (route) =>
      route.fulfill({
        json: {
          data: [
            { name: "production" },
            { name: "default" },
            { name: "staging" },
          ],
        },
      }),
    );
  });

  test("opens from admin navigation and validates a transient candidate with all diagnostics", async ({
    page,
  }) => {
    const candidate = { base_policy_revision: 1, policy_patch: { rules: [] } };
    await page.route("**/api/v1/policy-simulations/validate", async (route) => {
      expect(route.request().postDataJSON()).toEqual({ candidate });
      await route.fulfill({
        json: {
          data: {
            schema_version: "1.0",
            valid: false,
            stale: true,
            identity: simulationIdentity,
            diagnostics: [
              {
                severity: "error",
                reason_code: "invalid_selector",
                path: "/rules/0",
              },
              {
                severity: "warning",
                reason_code: "broad_wildcard",
                path: "/rules/1",
              },
            ],
          },
        },
      });
    });
    await page.goto(`${routerUrl}/admin/`);
    await page
      .getByRole("navigation", { name: "Wanaku", exact: true })
      .getByRole("link", { name: "Admin", exact: true })
      .click();
    await page
      .getByRole("link", { name: "Policy Simulation", exact: true })
      .click();
    await expect
      .poll(() => simulation.getPageTitle())
      .toBe("Policy Simulation");
    await simulation.candidate().fill(`base_policy_revision: 1
policy_patch:
  rules: []
`);
    await page.getByRole("button", { name: "Validate candidate" }).click();
    await expect(simulation.report("Validation report")).toContainText(
      "Invalid",
    );
    await expect(simulation.report("Validation report")).toContainText(
      "invalid_selector",
    );
    await expect(simulation.report("Validation report")).toContainText(
      "broad_wildcard",
    );
    await expect(simulation.report("Validation report")).toContainText(
      "Stale report",
    );
  });

  for (const method of ["tools/call", "resources/read", "prompts/get"]) {
    test(`simulates ${method} with external evaluators disabled`, async ({
      page,
    }) => {
      await page.route(
        "**/api/v1/policy-simulations/simulate",
        async (route) => {
          const body = route.request().postDataJSON();
          expect(body.candidate).toEqual({ policy_revision: 2 });
          expect(body.allow_external_evaluators).toBe(false);
          expect(body.action).toEqual({
            namespace: "production",
            request: {
              jsonrpc: "2.0",
              id: 1,
              method,
              params:
                method === "resources/read"
                  ? { uri: "file:///report" }
                  : { name: "summary", arguments: { city: "Prague" } },
            },
          });
          await route.fulfill({
            json: {
              data: {
                ...simulationReport,
                operation: method,
                conditional: true,
                baseline_used: true,
                failure_used: true,
              },
            },
          });
        },
      );
      await simulation.goto();
      await simulation.candidate().fill("policy_revision: 2");
      await page
        .getByLabel("Namespace", { exact: true })
        .selectOption("production");
      await page.getByLabel("MCP method").selectOption(method);
      if (method === "resources/read")
        await page.getByLabel("Resource URI").selectOption("file:///report");
      else {
        await page.getByLabel("Tool or prompt name").selectOption("summary");
        await page.getByLabel("Arguments (JSON)").fill('{"city":"Prague"}');
      }
      await page.getByRole("button", { name: "Simulate action" }).click();
      await expect(simulation.report("Simulation report")).toContainText(
        "Conditional result",
      );
      await expect(simulation.report("Simulation report")).toContainText(
        "external_evaluator_skipped",
      );
      await expect(simulation.report("Simulation report")).toContainText(
        "protect-production",
      );
      await expect(simulation.report("Simulation report")).toContainText(
        "Baseline used: Yes",
      );
    });
  }

  test("polls a replay and highlights impact, skipped events, and evidence", async ({
    page,
  }) => {
    let polls = 0;
    await page.route("**/api/v1/policy-simulations/replays", async (route) => {
      expect(route.request().postDataJSON()).toEqual({
        candidate: simulationExampleCandidate,
        actions: [simulationAction],
        audit_event_ids: ["audit-1"],
        allow_external_evaluators: false,
      });
      await route.fulfill({
        status: 202,
        json: { data: { ...simulationReplay, status: "running", details: [] } },
      });
    });
    await page.route(
      "**/api/v1/policy-simulations/replays/replay-1",
      async (route) => {
        polls++;
        await route.fulfill({ json: { data: simulationReplay } });
      },
    );
    await simulation.goto();
    await page
      .getByRole("button", { name: "Add selected action to replay" })
      .click();
    await simulation.selectAuditEvent("audit-1");
    await page.getByRole("button", { name: "Start replay" }).click();
    await expect(simulation.report("Replay report")).toContainText("completed");
    expect(polls).toBeGreaterThan(0);
    await expect(simulation.report("Replay report")).toContainText(
      "deny_to_allow: 1",
    );
    await expect(simulation.report("Replay report")).toContainText(
      "Skipped audit events",
    );
    await simulation
      .report("Replay report")
      .getByRole("button", { name: "Action 1: deny_to_allow" })
      .click();
    await expect(simulation.report("Replay report")).toContainText(
      "Access expansion",
    );
    await expect(simulation.report("Replay report")).toContainText(
      "Outside supporting evidence",
    );
    await simulation
      .report("Replay report")
      .getByRole("button", { name: "Action 2: allow_to_deny" })
      .click();
    await expect(simulation.report("Replay report")).toContainText(
      "Compatibility impact",
    );
  });

  test("cancels a running replay and deletes its retained result", async ({
    page,
  }) => {
    let deleted = false;
    await page.route("**/api/v1/policy-simulations/replays", (route) =>
      route.fulfill({
        status: 202,
        json: { data: { ...simulationReplay, status: "running", details: [] } },
      }),
    );
    await page.route(
      "**/api/v1/policy-simulations/replays/replay-1/cancel",
      (route) =>
        route.fulfill({
          json: {
            data: { ...simulationReplay, status: "cancelled", details: [] },
          },
        }),
    );
    await page.route(
      "**/api/v1/policy-simulations/replays/replay-1",
      async (route) => {
        if (route.request().method() === "DELETE") {
          deleted = true;
          await route.fulfill({ json: { data: null } });
        } else
          await route.fulfill({
            json: {
              data: { ...simulationReplay, status: "running", details: [] },
            },
          });
      },
    );
    await simulation.goto();
    await page
      .getByRole("button", { name: "Add selected action to replay" })
      .click();
    await page.getByRole("button", { name: "Start replay" }).click();
    await page
      .getByRole("button", { name: "Cancel replay", exact: true })
      .click();
    await expect(simulation.report("Replay report")).toContainText("cancelled");
    await page.getByRole("button", { name: "Delete replay result" }).click();
    await expect(simulation.report("Replay report")).toHaveCount(0);
    expect(deleted).toBe(true);
  });

  test("rejects malformed inputs and empty replay sets before sending requests", async ({
    page,
  }) => {
    const calls: string[] = [];
    await page.route("**/api/v1/policy-simulations/**", (route) => {
      calls.push(route.request().url());
      return route.abort();
    });
    await simulation.goto();
    await simulation.candidate().fill("{");
    await page.getByRole("button", { name: "Validate candidate" }).click();
    await expect(
      page.getByText(/Candidate must contain valid YAML\./),
    ).toBeVisible();
    await simulation.candidate().fill("[]");
    await page.getByRole("button", { name: "Validate candidate" }).click();
    await expect(
      page.getByText("Candidate must be a YAML mapping."),
    ).toBeVisible();
    await simulation.candidate().fill("{}");
    await expect(
      page.getByRole("button", { name: "Start replay" }),
    ).toBeDisabled();
    await expect(
      page.getByText(
        "Select an audit event or add a synthetic action before starting replay.",
      ),
    ).toBeVisible();
    expect(calls).toHaveLength(0);
  });

  test("reports API failure and permits retry", async ({ page }) => {
    let calls = 0;
    await page.route("**/api/v1/policy-simulations/validate", async (route) => {
      calls++;
      if (calls === 1)
        await route.fulfill({
          status: 503,
          json: { error: "simulation_unavailable" },
        });
      else
        await route.fulfill({
          json: {
            data: {
              schema_version: "1.0",
              valid: true,
              stale: false,
              identity: simulationIdentity,
              diagnostics: [],
            },
          },
        });
    });
    await simulation.goto();
    await page.getByRole("button", { name: "Validate candidate" }).click();
    await expect(page.getByText("simulation_unavailable")).toBeVisible();
    await page.getByRole("button", { name: "Validate candidate" }).click();
    await expect(simulation.report("Validation report")).toContainText("Valid");
    await expect(page.getByText("simulation_unavailable")).toHaveCount(0);
  });
  test("replays only explicitly selected audit events", async ({ page }) => {
    const candidate = {
      base_policy_revision: 1,
      inline_policy: { rules: [] },
      evidence_ids: ["audit-1"],
    };
    await page.route("**/api/v1/policy-simulations/replays", async (route) => {
      expect(route.request().postDataJSON()).toEqual({
        candidate,
        actions: [],
        audit_event_ids: ["audit-1", "audit-2"],
        allow_external_evaluators: false,
      });
      await route.fulfill({ status: 202, json: { data: simulationReplay } });
    });
    await simulation.goto();
    await simulation.candidate().fill(`base_policy_revision: 1
inline_policy:
  rules: []
evidence_ids:
  - audit-1
`);
    await simulation.selectAuditEvent("audit-1");
    await simulation.selectAuditEvent("audit-2");
    await page.getByRole("button", { name: "Start replay" }).click();
    await expect(simulation.report("Replay report")).toContainText("completed");
  });

  test("recovers a replay after a failed status request", async ({ page }) => {
    let refreshes = 0;
    await page.route("**/api/v1/policy-simulations/replays", (route) =>
      route.fulfill({
        status: 202,
        json: { data: { ...simulationReplay, status: "running", details: [] } },
      }),
    );
    await page.route(
      "**/api/v1/policy-simulations/replays/replay-1",
      async (route) => {
        refreshes++;
        if (refreshes === 1)
          await route.fulfill({
            status: 503,
            json: { error: "replay_status_unavailable" },
          });
        else await route.fulfill({ json: { data: simulationReplay } });
      },
    );
    await simulation.goto();
    await page
      .getByRole("button", { name: "Add selected action to replay" })
      .click();
    await page.getByRole("button", { name: "Start replay" }).click();
    await expect(page.getByText("replay_status_unavailable")).toBeVisible();
    await expect(simulation.report("Replay report")).toContainText("running");
    await page.getByRole("button", { name: "Refresh replay" }).click();
    await expect(simulation.report("Replay report")).toContainText("completed");
    await expect(page.getByText("replay_status_unavailable")).toHaveCount(0);
    expect(refreshes).toBe(2);
  });
  test("converts YAML policy and evaluator configuration to the JSON API contract", async ({
    page,
  }) => {
    await page.route("**/api/v1/policy-simulations/validate", async (route) => {
      expect(route.request().postDataJSON()).toEqual({
        candidate: {
          base_policy_revision: 1,
          base_evaluator_revision: 0,
          inline_policy: {
            rules: [
              {
                id: "deny-delete",
                effect: "deny",
                selectors: {
                  operation: "tools/call",
                  target_name: { matcher: "exact", value: "delete_file" },
                },
                message: "Deletion is blocked.\nRequest approval first.\n",
              },
            ],
          },
          inline_evaluators: [
            {
              name: "review",
              trigger: { method: "tools/call" },
              engine: { type: "passthrough" },
              processor: { path: "/wasm/review.wasm" },
            },
          ],
        },
      });
      await route.fulfill({
        json: {
          data: {
            schema_version: "1.0",
            valid: true,
            stale: false,
            identity: simulationIdentity,
            diagnostics: [],
          },
        },
      });
    });
    await simulation.goto();
    await simulation.candidate().fill(`# Transient candidate
base_policy_revision: 1
base_evaluator_revision: 0
inline_policy:
  rules:
    - id: deny-delete
      effect: deny
      selectors:
        operation: tools/call
        target_name:
          matcher: exact
          value: delete_file
      message: |
        Deletion is blocked.
        Request approval first.
inline_evaluators:
  - name: review
    trigger:
      method: tools/call
    engine:
      type: passthrough
    processor:
      path: /wasm/review.wasm
`);
    await page.getByRole("button", { name: "Validate candidate" }).click();
    await expect(simulation.report("Validation report")).toContainText("Valid");
  });
  test("starts with an editable YAML candidate template", async ({ page }) => {
    await page.route("**/api/v1/policy-simulations/validate", async (route) => {
      expect(route.request().postDataJSON()).toEqual({
        candidate: simulationExampleCandidate,
      });
      await route.fulfill({
        json: {
          data: {
            schema_version: "1.0",
            valid: true,
            stale: false,
            identity: simulationIdentity,
            diagnostics: [],
          },
        },
      });
    });
    await simulation.goto();
    await expect(simulation.candidate()).toHaveValue(/action_policy:/);
    await expect(simulation.candidate()).toHaveValue(/example-deny-tool-calls/);
    await page.getByRole("button", { name: "Validate candidate" }).click();
    await expect(simulation.report("Validation report")).toContainText("Valid");
  });
  test("highlights YAML while editing and keeps markup as plain text", async ({
    page,
  }) => {
    await simulation.goto();
    const highlighted = page.locator(".yaml-editor__highlight");
    await expect(highlighted.locator(".hljs-comment").first()).toContainText(
      "Transient example",
    );
    await simulation
      .candidate()
      .fill('policy_revision: 2\nmessage: "<img src=x onerror=alert(1)>"\n');
    await expect(highlighted.locator(".hljs-number")).toHaveText("2");
    await expect(highlighted.locator(".hljs-string")).toContainText(
      "<img src=x onerror=alert(1)>",
    );
    await expect(highlighted.locator("img")).toHaveCount(0);
    await simulation.candidate().press("ControlOrMeta+End");
    await simulation.candidate().pressSequentially("# Review candidate");
    await expect(highlighted.locator(".hljs-comment")).toHaveText(
      "# Review candidate",
    );
  });

  test("keeps YAML highlighting aligned when scrolling a long candidate", async ({
    page,
  }) => {
    await simulation.goto();
    await simulation
      .candidate()
      .fill(
        Array.from({ length: 100 }, (_, index) => `# Rule ${index}`).join("\n"),
      );
    await simulation.candidate().press("ControlOrMeta+End");
    await expect
      .poll(() =>
        simulation.candidate().evaluate((element) => element.scrollTop),
      )
      .toBeGreaterThan(0);
    const scrollTop = await simulation
      .candidate()
      .evaluate((element) => element.scrollTop);
    await expect
      .poll(() =>
        page
          .locator(".yaml-editor__highlight")
          .evaluate((element) => element.scrollTop),
      )
      .toBe(scrollTop);
  });
  test("lists registered namespaces and selects default before other namespaces", async ({
    page,
  }) => {
    await simulation.goto();
    const selector = page.getByLabel("Namespace", { exact: true });
    await expect(selector).toHaveValue("default");
    await expect(selector.locator("option:not([hidden])")).toHaveText([
      "default",
      "production",
      "staging",
    ]);
    await selector.selectOption("staging");
    await expect(selector).toHaveValue("staging");
  });

  test("selects a registered namespace when default is unavailable", async ({
    page,
  }) => {
    await page.route("**/api/v1/namespaces", (route) =>
      route.fulfill({ json: { data: [{ name: "staging" }] } }),
    );
    await simulation.goto();
    await expect(page.getByLabel("Namespace", { exact: true })).toHaveValue(
      "staging",
    );
  });

  test("disables simulation when no namespaces are registered", async ({
    page,
  }) => {
    await page.route("**/api/v1/namespaces", (route) =>
      route.fulfill({ json: { data: [] } }),
    );
    await simulation.goto();
    await expect(
      page.getByText(
        "No registered namespaces. Add a namespace before simulation.",
      ),
    ).toBeVisible();
    await expect(page.getByLabel("Namespace", { exact: true })).toBeDisabled();
    await expect(
      page.getByRole("button", { name: "Simulate action" }),
    ).toBeDisabled();
  });

  test("shows namespace loading errors without allowing simulation", async ({
    page,
  }) => {
    await page.route("**/api/v1/namespaces", (route) =>
      route.fulfill({
        status: 503,
        json: { error: "namespace_registry_unavailable" },
      }),
    );
    await simulation.goto();
    await expect(
      page.getByText("namespace_registry_unavailable"),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Simulate action" }),
    ).toBeDisabled();
    await expect(
      page.getByRole("button", { name: "Validate candidate" }),
    ).toBeEnabled();
  });
  test("filters registered targets by namespace and MCP method", async ({
    page,
  }) => {
    await simulation.goto();
    const target = page.getByLabel("Tool or prompt name");
    await expect(target.locator("option:not([hidden])")).toHaveText([
      "summary",
    ]);
    await page
      .getByLabel("Namespace", { exact: true })
      .selectOption("production");
    await expect(target.locator("option:not([hidden])")).toHaveText([
      "restart",
      "summary",
    ]);
    await target.selectOption("summary");
    await page.getByLabel("MCP method").selectOption("prompts/get");
    await expect(target.locator("option:not([hidden])")).toHaveText([
      "summary",
    ]);
    await page.getByLabel("MCP method").selectOption("resources/read");
    const resource = page.getByLabel("Resource URI");
    await expect(resource.locator("option:not([hidden])")).toHaveText([
      "report — file:///report",
    ]);
    await page.getByLabel("Namespace", { exact: true }).selectOption("default");
    await expect(resource).toHaveValue("file:///default-report");
    await expect(resource.locator("option:not([hidden])")).toHaveText([
      "default-resource — file:///default-report",
    ]);
  });

  test("disables simulation when the namespace has no targets or target loading fails", async ({
    page,
  }) => {
    await page.route("**/api/v1/tools", (route) =>
      route.fulfill({ json: { data: [] } }),
    );
    await page.route("**/api/v1/prompts", (route) =>
      route.fulfill({
        status: 503,
        json: { error: "prompt_registry_unavailable" },
      }),
    );
    await simulation.goto();
    await expect(
      page.getByText(
        "No actions for this method are registered in this namespace.",
      ),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Simulate action" }),
    ).toBeDisabled();
    await page.getByLabel("MCP method").selectOption("prompts/get");
    await expect(page.getByText("prompt_registry_unavailable")).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Simulate action" }),
    ).toBeDisabled();
  });

  test("generates and simulates an inline example with current bases and no inherited evaluators", async ({
    page,
  }) => {
    const expected = {
      base_policy_revision: 4,
      base_evaluator_revision: 21,
      inline_policy: {
        rules: [
          {
            id: "example-deny-selected-action",
            effect: "deny",
            selectors: {
              namespace: "production",
              operation: "tools/call",
              target_name: { matcher: "exact", value: "summary" },
            },
            reason_code: "example_action_denied",
          },
        ],
      },
      inline_evaluators: [],
    };
    await page.route("**/api/v1/policy-simulations/validate", async (route) => {
      const candidate = route.request().postDataJSON().candidate;
      const active = Object.keys(candidate).length === 0;
      if (!active) expect(candidate).toEqual(expected);
      await route.fulfill({
        json: {
          data: {
            schema_version: "1.0",
            valid: !active,
            stale: false,
            identity: {
              ...simulationIdentity,
              base_policy_revision: 4,
              base_evaluator_revision: 21,
            },
            diagnostics: active
              ? [
                  {
                    severity: "error",
                    reason_code: "namespace_unavailable",
                    path: "evaluators/0/trigger/namespace",
                  },
                ]
              : [],
          },
        },
      });
    });
    await page.route("**/api/v1/policy-simulations/simulate", async (route) => {
      expect(route.request().postDataJSON().candidate).toEqual(expected);
      expect(route.request().postDataJSON().action.namespace).toBe(
        "production",
      );
      await route.fulfill({ json: { data: simulationReport } });
    });
    await simulation.goto();
    await page
      .getByLabel("Namespace", { exact: true })
      .selectOption("production");
    await page.getByLabel("Tool or prompt name").selectOption("summary");
    await page.getByRole("button", { name: "Load example candidate" }).click();
    await expect(
      page.getByText("Base policy revision: 4 · Base evaluator revision: 21"),
    ).toBeVisible();
    await expect(simulation.candidate()).toHaveValue(/evaluators: \[\]/);
    await page.getByRole("button", { name: "Validate candidate" }).click();
    await expect(simulation.report("Validation report")).toContainText("Valid");
    await page.getByRole("button", { name: "Simulate action" }).click();
    await expect(simulation.report("Simulation report")).toBeVisible();
  });

  for (const operation of ["simulate", "replays"]) {
    test(`displays the structured validation report when ${operation} rejects a candidate`, async ({
      page,
    }) => {
      await page.route(`**/api/v1/policy-simulations/${operation}`, (route) =>
        route.fulfill({
          status: 422,
          json: {
            error: null,
            data: {
              reason_code: "candidate_invalid",
              validation: {
                schema_version: "1.0",
                valid: false,
                stale: false,
                identity: simulationIdentity,
                diagnostics: [
                  {
                    severity: "error",
                    reason_code: "candidate_base_revision_required",
                    path: "candidate/policy",
                  },
                  {
                    severity: "error",
                    reason_code: "namespace_unavailable",
                    path: "evaluators/0/trigger/namespace",
                  },
                ],
              },
            },
          },
        }),
      );
      await simulation.goto();
      if (operation === "simulate")
        await page.getByRole("button", { name: "Simulate action" }).click();
      else {
        await page
          .getByRole("button", { name: "Add selected action to replay" })
          .click();
        await page.getByRole("button", { name: "Start replay" }).click();
      }
      await expect(
        page.getByText("candidate_invalid", { exact: true }),
      ).toBeVisible();
      await expect(simulation.report("Validation report")).toContainText(
        "candidate_base_revision_required",
      );
      await expect(simulation.report("Validation report")).toContainText(
        "namespace_unavailable",
      );
      await expect(
        page.getByText("Request failed with status 422"),
      ).toHaveCount(0);
    });
  }
  test("fills current base revisions into the visible starter example", async ({
    page,
  }) => {
    await page.route("**/api/v1/action-policies", (route) =>
      route.fulfill({
        json: { data: { revision: { id: 4 }, policy: { rules: [] } } },
      }),
    );
    await page.route("**/api/v1/evaluators/revisions/active", (route) =>
      route.fulfill({
        json: { data: { revision: { id: 21 }, evaluators: [] } },
      }),
    );
    await page.route("**/api/v1/policy-simulations/validate", async (route) => {
      expect(route.request().postDataJSON().candidate).toEqual({
        ...simulationExampleCandidate,
        base_policy_revision: 4,
        base_evaluator_revision: 21,
      });
      await route.fulfill({
        json: {
          data: {
            schema_version: "1.0",
            valid: true,
            stale: false,
            identity: simulationIdentity,
            diagnostics: [],
          },
        },
      });
    });
    await simulation.goto();
    await expect(
      page.getByText("Base policy revision: 4 · Base evaluator revision: 21"),
    ).toBeVisible();
    await expect(simulation.candidate()).toHaveValue(/evaluators: \[\]/);
    await page.getByRole("button", { name: "Validate candidate" }).click();
    await expect(simulation.report("Validation report")).toContainText("Valid");
  });
  test("accepts the wanaku.yaml action_policy format without changing rule content", async ({
    page,
  }) => {
    const policy = {
      rules: [
        {
          id: "deny-production-delete",
          description: "Prevent destructive production calls",
          effect: "deny",
          selectors: {
            namespace: "production",
            operation: "tools/call",
            target_type: "tool",
            target_name: { matcher: "glob", value: "delete-*" },
            labels: { risk: "high" },
          },
          predicates: [
            { operator: "equals", pointer: "/arguments/force", value: true },
          ],
          reason_code: "destructive_action_denied",
          message: "This action is not permitted.",
          metadata: { owner: "platform-security" },
        },
      ],
    };
    await page.route("**/api/v1/policy-simulations/validate", async (route) => {
      expect(route.request().postDataJSON().candidate).toEqual({
        base_policy_revision: 0,
        inline_policy: policy,
      });
      await route.fulfill({
        json: {
          data: {
            schema_version: "1.0",
            valid: true,
            stale: false,
            identity: simulationIdentity,
            diagnostics: [],
          },
        },
      });
    });
    await simulation.goto();
    await simulation.candidate().fill(`action_policy:
  rules:
    - id: deny-production-delete
      description: Prevent destructive production calls
      effect: deny
      selectors:
        namespace: production
        operation: tools/call
        target_type: tool
        target_name:
          matcher: glob
          value: "delete-*"
        labels:
          risk: high
      predicates:
        - operator: equals
          pointer: /arguments/force
          value: true
      reason_code: destructive_action_denied
      message: This action is not permitted.
      metadata:
        owner: platform-security
`);
    await page.getByRole("button", { name: "Validate candidate" }).click();
    await expect(simulation.report("Validation report")).toContainText("Valid");
  });

  test("maps configuration evaluator definitions into the simulation request", async ({
    page,
  }) => {
    await page.route("**/api/v1/policy-simulations/validate", async (route) => {
      expect(route.request().postDataJSON().candidate).toEqual({
        base_policy_revision: 0,
        inline_policy: { rules: [] },
        base_evaluator_revision: 0,
        inline_evaluators: [],
      });
      await route.fulfill({
        json: {
          data: {
            schema_version: "1.0",
            valid: true,
            stale: false,
            identity: simulationIdentity,
            diagnostics: [],
          },
        },
      });
    });
    await simulation.goto();
    await simulation
      .candidate()
      .fill("action_policy:\n  rules: []\nevaluators: []\n");
    await page.getByRole("button", { name: "Validate candidate" }).click();
    await expect(simulation.report("Validation report")).toContainText("Valid");
  });

  test("rejects unsupported configuration sections instead of ignoring them", async ({
    page,
  }) => {
    let requests = 0;
    await page.route("**/api/v1/policy-simulations/validate", (route) => {
      requests++;
      return route.abort();
    });
    await simulation.goto();
    await simulation
      .candidate()
      .fill("action_policy:\n  rules: []\ngovernance: {}\n");
    await page.getByRole("button", { name: "Validate candidate" }).click();
    await expect(
      page.getByText(
        "Unsupported simulation configuration fields: governance. Use action_policy and evaluators.",
      ),
    ).toBeVisible();
    expect(requests).toBe(0);
  });
  test("shows audit replay eligibility and prevents selection of unusable events", async ({
    page,
  }) => {
    const events = [
      simulationAuditEvent("ready"),
      { ...simulationAuditEvent("no-payload"), payload: null },
      {
        ...simulationAuditEvent("redacted"),
        redaction: {
          payload_captured: true,
          payload_truncated: false,
          redacted_fields: ["/params/arguments/token"],
        },
      },
      {
        ...simulationAuditEvent("truncated"),
        redaction: {
          payload_captured: true,
          payload_truncated: true,
          redacted_fields: [],
        },
      },
      { ...simulationAuditEvent("administrative"), category: "administrative" },
      {
        ...simulationAuditEvent("unsupported"),
        payload: { jsonrpc: "2.0", id: 1, method: "tools/list", params: {} },
      },
    ];
    await page.route("**/api/v1/audit/events?**", (route) =>
      route.fulfill({
        json: { data: { events, total: events.length, offset: 0, limit: 20 } },
      }),
    );
    await simulation.goto();
    for (const id of [
      "no-payload",
      "redacted",
      "truncated",
      "administrative",
      "unsupported",
    ])
      await expect(
        page.getByLabel(`Select audit event ${id}`, { exact: true }),
      ).toBeDisabled();
    await expect(
      page.getByText("No captured request payload", { exact: true }),
    ).toBeVisible();
    await page
      .getByLabel("Select audit event ready", { exact: true })
      .press("Space");
    await expect(
      page.getByText("1 audit events selected", { exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Start replay" }),
    ).toBeEnabled();
    await expect(page.getByLabel("Replay actions (JSON array)")).toHaveCount(0);
  });

  test("searches and pages audit events while retaining selections", async ({
    page,
  }) => {
    const searches: URL[] = [];
    await page.route("**/api/v1/audit/events?**", (route) => {
      const url = new URL(route.request().url());
      searches.push(url);
      const offset = Number(url.searchParams.get("offset") ?? 0);
      return route.fulfill({
        json: {
          data: {
            events: [simulationAuditEvent(offset === 0 ? "first" : "second")],
            total: 21,
            offset,
            limit: 20,
          },
        },
      });
    });
    await simulation.goto();
    await page
      .getByLabel("Select audit event first", { exact: true })
      .press("Space");
    await page.getByRole("button", { name: "Next events" }).click();
    await page
      .getByLabel("Select audit event second", { exact: true })
      .press("Space");
    await expect(
      page.getByText("2 audit events selected", { exact: true }),
    ).toBeVisible();
    await page.getByLabel("Audit namespace").selectOption("production");
    await page.getByLabel("Audit operation").selectOption("prompts/get");
    await page.getByLabel("Audit target", { exact: true }).fill("summary");
    await page
      .getByRole("button", { name: "Search events", exact: true })
      .click();
    await expect(
      page.getByLabel("Select audit event first", { exact: true }),
    ).toBeVisible();
    const last = searches.at(-1);
    expect(last?.searchParams.get("namespace")).toBe("production");
    expect(last?.searchParams.get("operation")).toBe("prompts/get");
    expect(last?.searchParams.get("target")).toBe("summary");
    expect(last?.searchParams.get("offset")).toBe("0");
    await page
      .getByRole("button", { name: "Remove audit event first", exact: true })
      .click();
    await expect(
      page.getByText("1 audit events selected", { exact: true }),
    ).toBeVisible();
  });

  test("reports unavailable audit IDs and offers an event search retry", async ({
    page,
  }) => {
    await page.route("**/api/v1/audit/events/missing", (route) =>
      route.fulfill({ status: 404, json: { error: "not found" } }),
    );
    await simulation.goto();
    await page.getByLabel("Find audit event by ID").fill(" missing ");
    await page.getByRole("button", { name: "Find event", exact: true }).click();
    await expect(
      page.getByText("Audit event not found or no longer retained."),
    ).toBeVisible();
    await simulation.selectAuditEvent("audit-1");
    await expect(
      page.getByText("1 audit events selected", { exact: true }),
    ).toBeVisible();
  });

  test("builds a batch of synthetic actions without entering a JSON array", async ({
    page,
  }) => {
    await simulation.goto();
    await page
      .getByRole("button", { name: "Add selected action to replay" })
      .click();
    await page
      .getByLabel("Namespace", { exact: true })
      .selectOption("production");
    await page.getByLabel("Tool or prompt name").selectOption("restart");
    await page
      .getByRole("button", { name: "Add selected action to replay" })
      .click();
    await expect(
      page
        .getByRole("table", { name: "Actions in this batch" })
        .getByRole("row"),
    ).toHaveCount(3);
    await page
      .getByRole("button", { name: "Remove action 1", exact: true })
      .click();
    await page.route("**/api/v1/policy-simulations/replays", async (route) => {
      expect(route.request().postDataJSON().actions).toEqual([
        {
          namespace: "production",
          request: {
            jsonrpc: "2.0",
            id: 1,
            method: "tools/call",
            params: { name: "restart", arguments: {} },
          },
        },
      ]);
      await route.fulfill({ status: 202, json: { data: simulationReplay } });
    });
    await page.getByRole("button", { name: "Start replay" }).click();
    await expect(simulation.report("Replay report")).toBeVisible();
  });

  test("explains a completed replay when selected audit events expired", async ({
    page,
  }) => {
    await page.route("**/api/v1/policy-simulations/replays", (route) =>
      route.fulfill({
        status: 202,
        json: {
          data: {
            ...simulationReplay,
            details: [],
            counts: {},
            skipped_audit_ids: ["audit-1"],
          },
        },
      }),
    );
    await simulation.goto();
    await simulation.selectAuditEvent("audit-1");
    await page.getByRole("button", { name: "Start replay" }).click();
    await expect(simulation.report("Replay report")).toContainText(
      "No actions were replayed",
    );
    await expect(simulation.report("Replay report")).toContainText("audit-1");
  });

  for (const legacy of [false, true]) {
    test(`explains invalid active configuration with ${legacy ? "legacy" : "structured"} replay errors`, async ({
      page,
    }) => {
      const validation = {
        schema_version: "1.0",
        valid: false,
        stale: false,
        identity: simulationIdentity,
        diagnostics: [
          {
            severity: "error",
            reason_code: "namespace_unavailable",
            path: "evaluators/0/trigger/namespace",
          },
        ],
      };
      await page.route("**/api/v1/policy-simulations/replays", (route) =>
        route.fulfill({
          status: 422,
          json: legacy
            ? { data: null, error: "active_configuration_invalid" }
            : {
                data: {
                  reason_code: "active_configuration_invalid",
                  validation,
                },
                error: null,
              },
        }),
      );
      await page.route(
        "**/api/v1/policy-simulations/validate",
        async (route) => {
          expect(route.request().postDataJSON()).toEqual({ candidate: {} });
          await route.fulfill({ json: { data: validation } });
        },
      );
      await simulation.goto();
      await page
        .getByRole("button", { name: "Add selected action to replay" })
        .click();
      await page.getByRole("button", { name: "Start replay" }).click();
      await expect(
        page.getByText(/The active configuration is invalid\./),
      ).toBeVisible();
      await expect(simulation.report("Validation report")).toContainText(
        "namespace_unavailable",
      );
    });
  }
});
