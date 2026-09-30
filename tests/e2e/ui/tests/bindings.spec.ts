import { expect, test, type Page } from "@playwright/test";
import { bindingData } from "../helpers/test-data";
import { BindingsPage } from "../pages/bindings.page";

const routerUrl = process.env.WANAKU_ROUTER_URL ?? "http://localhost:8080";

const routeBindings = async (page: Page, bindings: unknown[]) => {
  await page.route("**/api/v1/bindings", (route) => route.fulfill({
    json: { data: bindings },
  }));
};

test.describe("Credential Bindings", () => {
  let bindings: BindingsPage;

  test.beforeEach(async ({ page }) => {
    bindings = new BindingsPage(page, `${routerUrl}/admin/`);
  });

  test("lists credential bindings and shows read-only details", async ({ page }) => {
    const binding = bindingData();
    await routeBindings(page, [binding]);

    await bindings.goto();

    await expect.poll(() => bindings.getPageTitle()).toBe("Credential Bindings");

    const headerNavigation = page.getByRole("navigation", { name: "Wanaku" });
    await headerNavigation.getByRole("link", { name: "Admin" }).click();
    await expect(headerNavigation.getByRole("link", { name: "Credential Bindings" })).toBeVisible();

    await expect(bindings.bindingsTable()).toContainText(binding.forwardId);
    await expect(bindings.bindingsTable()).toContainText(binding.origin);
    await expect(bindings.bindingsTable()).toContainText(binding.secretRefs[0]);
    await expect(bindings.bindingsTable()).toContainText("invocation");

    await page.getByRole("button", { name: `View binding ${binding.id}` }).click();
    await expect(bindings.detailModal()).toContainText(binding.id);
    await expect(bindings.detailModal()).toContainText(binding.forwardId);
    await expect(bindings.detailModal()).toContainText(binding.secretRefs[0]);

    // Read-only: no add/edit/delete controls should be present on this page.
    await expect(
      page.getByRole("button", { name: /\b(Add|Create|New|Edit|Update|Delete|Remove|Rotate)\b/i }),
    ).toHaveCount(0);
  });

  test("renders the empty state when no bindings exist", async ({ page }) => {
    await routeBindings(page, []);

    await bindings.goto();

    await expect.poll(() => bindings.getPageTitle()).toBe("Credential Bindings");
    await expect(bindings.bindingsTable()).toContainText("No credential bindings");
  });
});
