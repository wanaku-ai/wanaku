import { expect, test, type Page } from "@playwright/test";
import { AuditPage } from "../pages/audit.page";

const routerUrl = process.env.WANAKU_ROUTER_URL ?? "http://localhost:8080";

const auditEvent = {
  actor: null,
  attributes: {
    enforcement_action: "reject",
    matched_rule_ids: ["protect-production"],
  },
  audience: null,
  category: "decision",
  conversation_id: "conversation-17",
  correlation_id: "request-42",
  coverage_complete: true,
  decision: "block",
  dropped_events: 0,
  duration_ms: 7,
  evaluator: null,
  event_id: "event-1",
  explanation: "Action policy blocked the request.",
  filter: "wanaku_action_policy",
  namespace: "finance",
  operation: "tools/call",
  payload: null,
  policy_revision: "12",
  protocol: "mcp",
  reason_code: "production_restart_denied",
  redaction: {
    payload_captured: false,
    payload_truncated: false,
    redacted_fields: [],
  },
  request_id: "request-42",
  response_status: null,
  schema_version: "1.0",
  sequence: 3,
  stream_id: "request-42",
  target: "restart-service",
  target_type: "tool",
  timestamp: "2026-09-14T12:00:00Z",
  upstream_id: null,
  workload: null,
};

const routeSupportingRequests = async (page: Page, healthy = true) => {
  await page.route("**/api/v1/audit/health", (route) => route.fulfill({
    json: {
      data: {
        capacity: 10000,
        dropped_events: healthy ? 0 : 2,
        healthy,
        last_error: healthy ? null : "audit storage unavailable",
        retained_events: 1,
      },
    },
  }));
  await page.route("**/api/v1/audit/schema", (route) => route.fulfill({
    json: { data: { schema_version: "1.0" } },
  }));
};

test.describe("Audit", () => {
  let audit: AuditPage;

  test.beforeEach(async ({ page }) => {
    audit = new AuditPage(page, `${routerUrl}/admin/`);
  });

  test("lists events and opens complete event details", async ({ page }) => {
    await routeSupportingRequests(page);
    await page.route("**/api/v1/audit/events?*", (route) => route.fulfill({
      json: { data: { events: [auditEvent], limit: 50, offset: 0, total: 1 } },
    }));
    await page.route("**/api/v1/audit/events/event-1", (route) => route.fulfill({
      json: { data: auditEvent },
    }));

    await audit.goto();

    await expect.poll(() => audit.getPageTitle()).toBe("Audit");
    const headerNavigation = page.getByRole("navigation", { name: "Wanaku" });
    await headerNavigation.getByRole("link", { name: "Admin" }).click();
    await expect(headerNavigation.getByRole("link", { name: "Audit" })).toBeVisible();
    await expect(audit.health()).toContainText("Healthy");
    await expect(audit.eventsTable()).toContainText("production_restart_denied");
    await expect(audit.eventsTable()).toContainText("restart-service");

    await page.getByRole("button", { name: "View audit event event-1" }).click();
    await expect(audit.eventDetail()).toContainText("Audit event details");
    await expect(audit.eventDetail()).toContainText("request-42");
    await expect(audit.eventDetail()).toContainText("protect-production");
    await expect(audit.eventDetail()).toContainText("Payload captured");
  });

  test("applies server-side filters and renders the empty state", async ({ page }) => {
    await routeSupportingRequests(page);
    const requests: URL[] = [];
    await page.route("**/api/v1/audit/events?*", (route) => {
      const url = new URL(route.request().url());
      requests.push(url);
      const isFiltered = url.searchParams.get("decision") === "allow"
        && url.searchParams.get("namespace") === "engineering";
      return route.fulfill({
        json: {
          data: {
            events: isFiltered ? [] : [auditEvent],
            limit: 50,
            offset: 0,
            total: isFiltered ? 0 : 1,
          },
        },
      });
    });

    await audit.goto();
    await expect(audit.eventsTable()).toContainText("production_restart_denied");
    await audit.filters().getByLabel("Decision").selectOption("allow");
    await audit.filters().getByLabel("Namespace").fill("engineering");
    const filteredResponse = page.waitForResponse((response) => {
      const url = new URL(response.url());
      return url.pathname.endsWith("/api/v1/audit/events")
        && url.searchParams.get("decision") === "allow"
        && url.searchParams.get("namespace") === "engineering";
    });
    await audit.filters().getByRole("button", { name: "Apply filters" }).click();
    await filteredResponse;

    await expect.poll(() => requests.at(-1)?.searchParams.get("decision")).toBe("allow");
    await expect.poll(() => requests.at(-1)?.searchParams.get("namespace")).toBe("engineering");
    await expect(audit.eventsTable()).toContainText("No audit events");
  });

  test("shows degraded health without hiding events", async ({ page }) => {
    await routeSupportingRequests(page, false);
    await page.route("**/api/v1/audit/events?*", (route) => route.fulfill({
      json: { data: { events: [auditEvent], limit: 50, offset: 0, total: 1 } },
    }));

    await audit.goto();

    await expect(audit.health()).toContainText("Degraded");
    await expect(audit.health()).toContainText("audit storage unavailable");
    await expect(audit.eventsTable()).toContainText("production_restart_denied");
  });

  test("reports an event that is no longer retained", async ({ page }) => {
    await routeSupportingRequests(page);
    await page.route("**/api/v1/audit/events?*", (route) => route.fulfill({
      json: { data: { events: [auditEvent], limit: 50, offset: 0, total: 1 } },
    }));
    await page.route("**/api/v1/audit/events/event-1", (route) => route.fulfill({
      status: 404,
      json: { error: "audit event not found" },
    }));

    await audit.goto();
    await page.getByRole("button", { name: "View audit event event-1" }).click();

    await expect(page.getByText("This audit event is no longer retained.")).toBeVisible();
    await expect(audit.eventDetail()).toBeHidden();
  });
});
