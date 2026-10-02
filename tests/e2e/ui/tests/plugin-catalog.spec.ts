import { test, expect } from "@playwright/test";
import { PluginCatalogPage } from "../pages/plugin-catalog.page";

const routerUrl = process.env.WANAKU_ROUTER_URL ?? "http://localhost:8080";
const plugin = {
  id: "catalog-test",
  name: "Catalog Test",
  version: "1.2.3",
  description: "A catalog test plugin",
  publisher: "Wanaku",
  license: "Apache-2.0",
  dependencies: [{ id: "base-plugin", version: "1.0" }],
  requires: { hostApi: "1", services: [{ id: "backend", version: "2.0" }] },
  url: "https://example.com/catalog-test.zip",
  metadata: { category: "Test tools" },
};

test.describe("Plugin Catalog", () => {
  let catalog: PluginCatalogPage;
  test.beforeEach(async ({ page }) => {
    catalog = new PluginCatalogPage(page, `${routerUrl}/admin/`);
    await page.route("**/api/v1/plugins/catalog", (route) =>
      route.fulfill({ json: { data: [plugin], error: null } }),
    );
  });

  test("shows catalog separately from installed plugins and displays metadata", async ({
    page,
  }) => {
    await catalog.goto();
    expect(await catalog.getPageTitle()).toBe("Plugin Catalog");
    await expect(catalog.rowWithText(plugin.name)).toContainText(
      plugin.description,
    );
    const navigation = page.getByRole("navigation", { name: "Wanaku" });
    await navigation.getByRole("link", { name: "Admin", exact: true }).click();
    await expect(
      navigation.getByRole("link", { name: "Plugin Catalog", exact: true }),
    ).toBeVisible();
    await expect(
      navigation.getByRole("link", { name: "Installed Plugins", exact: true }),
    ).toBeVisible();
    await catalog.infoButton(plugin.name).click();
    await expect(catalog.modal()).toContainText("Apache-2.0");
    await expect(catalog.modal()).toContainText("Test tools");
    await expect(catalog.modal()).toContainText("base-plugin v1.0");
    await expect(catalog.modal()).toContainText("backend v2.0");
    await expect(catalog.modal().locator("dl")).toBeFocused();
    await page.keyboard.press("Escape");
    await expect(catalog.modal()).not.toBeVisible();
    await expect(catalog.infoButton(plugin.name)).toBeFocused();
  });

  test("installs catalog coordinates then configures required services", async ({
    page,
  }) => {
    await page.route("**/api/v1/plugins/install", async (route) => {
      expect(route.request().postDataJSON()).toEqual({
        id: plugin.id,
        version: plugin.version,
        url: plugin.url,
      });
      await route.fulfill({
        json: {
          data: { manifest: { ...plugin, entrypoint: "index.js" } },
          error: null,
        },
      });
    });
    await page.route(`**/api/v1/plugins/${plugin.id}/config`, async (route) => {
      expect(route.request().postDataJSON()).toEqual({
        services: { backend: { target: "http://localhost:9000" } },
      });
      await route.fulfill({ json: { data: {}, error: null } });
    });
    await catalog.goto();
    await catalog.installButton(plugin.name).click();
    await expect(catalog.modal()).toContainText(
      "Configure Services: Catalog Test",
    );
    await page
      .getByLabel("Service: backend (v2.0)")
      .fill("http://localhost:9000");
    await catalog.submitModal();
    await expect(page.getByText("Server restart required")).toBeVisible();
  });

  test("installs plugins without service requirements", async ({ page }) => {
    await page.route("**/api/v1/plugins/install", (route) =>
      route.fulfill({
        json: {
          data: {
            manifest: {
              id: plugin.id,
              name: plugin.name,
              version: plugin.version,
              entrypoint: "index.js",
            },
          },
          error: null,
        },
      }),
    );
    await catalog.goto();
    await catalog.installButton(plugin.name).click();
    await expect(page.getByText("Server restart required")).toBeVisible();
    await expect(catalog.modal()).not.toBeVisible();
  });

  test("keeps long descriptions compact and shows the full text in Info", async ({
    page,
  }) => {
    const description =
      "A plugin with detailed usage information. ".repeat(100) +
      "Final detail.";
    await page.route("**/api/v1/plugins/catalog", (route) =>
      route.fulfill({
        json: { data: [{ ...plugin, description }], error: null },
      }),
    );
    await catalog.goto();
    const summary = catalog
      .rowWithText(plugin.name)
      .locator(".plugin-catalog__description");
    await expect(summary).toHaveText(description);
    const dimensions = await summary.evaluate((element) => ({
      height: element.clientHeight,
      fullHeight: element.scrollHeight,
      lineHeight: Number.parseFloat(
        window.getComputedStyle(element).lineHeight,
      ),
    }));
    expect(dimensions.height).toBeLessThanOrEqual(
      dimensions.lineHeight * 3 + 1,
    );
    expect(dimensions.fullHeight).toBeGreaterThan(dimensions.height);
    await catalog.infoButton(plugin.name).click();
    await expect(
      catalog.modal().getByText(description, { exact: true }),
    ).toBeVisible();
    await expect(catalog.modal().locator("dl")).toBeFocused();
    await page.keyboard.press("Escape");
    await expect(catalog.modal()).not.toBeVisible();
    await expect(catalog.infoButton(plugin.name)).toBeFocused();
  });

  test("displays and installs a plugin from an independent publisher", async ({
    page,
  }) => {
    const communityPlugin = {
      id: "community-weather",
      name: "Community Weather",
      version: "2.4.0",
      description: "Weather tools for agents.",
      publisher: "Independent Tools",
      url: "https://plugins.example.org/weather/2.4.0/plugin.zip",
    };
    await page.route("**/api/v1/plugins/catalog", (route) =>
      route.fulfill({
        json: { data: [plugin, communityPlugin], error: null },
      }),
    );
    let installedCoordinates: unknown;
    await page.route("**/api/v1/plugins/install", async (route) => {
      installedCoordinates = route.request().postDataJSON();
      await route.fulfill({
        json: {
          data: { manifest: { ...communityPlugin, entrypoint: "index.js" } },
          error: null,
        },
      });
    });
    await catalog.goto();
    await expect(catalog.rowWithText(communityPlugin.name)).toBeVisible();
    await catalog.infoButton(communityPlugin.name).click();
    await expect(catalog.modal()).toContainText(communityPlugin.publisher);
    await expect(catalog.modal()).toContainText(communityPlugin.url);
    await expect(catalog.modal().locator("dl")).toBeFocused();
    await page.keyboard.press("Escape");
    await expect(catalog.modal()).not.toBeVisible();
    await expect(catalog.infoButton(communityPlugin.name)).toBeFocused();
    await catalog.installButton(communityPlugin.name).click();
    await expect(page.getByText("Server restart required")).toBeVisible();
    expect(installedCoordinates).toEqual({
      id: communityPlugin.id,
      version: communityPlugin.version,
      url: communityPlugin.url,
    });
  });

  test("shows install errors and permits retry", async ({ page }) => {
    await page.route("**/api/v1/plugins/install", (route) =>
      route.fulfill({ status: 502, json: { error: "Archive unavailable" } }),
    );
    await catalog.goto();
    await catalog.installButton(plugin.name).click();
    await expect(page.getByText("Archive unavailable")).toBeVisible();
    await expect(catalog.installButton(plugin.name)).toBeEnabled();
  });

  test("shows empty catalog", async ({ page }) => {
    await page.route("**/api/v1/plugins/catalog", (route) =>
      route.fulfill({ json: { data: [], error: null } }),
    );
    await catalog.goto();
    await expect(page.getByText("No plugins available")).toBeVisible();
  });

  test("shows catalog failure and retries", async ({ page }) => {
    await page.route("**/api/v1/plugins/catalog", (route) =>
      route.fulfill({ status: 502, json: { error: "Catalog unavailable" } }),
    );
    await catalog.goto();
    await expect(page.getByText("Catalog unavailable")).toBeVisible();
    await page.route("**/api/v1/plugins/catalog", (route) =>
      route.fulfill({ json: { data: [plugin], error: null } }),
    );
    await page.getByRole("button", { name: "Retry", exact: true }).click();
    await expect(catalog.rowWithText(plugin.name)).toBeVisible();
  });
});
