import { expect, test } from "@playwright/test";

test("does not expose the removed debugger in navigation", async ({ page }) => {
  await page.route("**/api/v1/plugins", (route) =>
    route.fulfill({ json: { data: [] } }),
  );
  await page.goto("./");
  const navigation = page.getByRole("navigation", { name: "Wanaku" });
  await expect(navigation).toBeVisible();
  await navigation
    .getByRole("link", { name: "Developer", exact: true })
    .click();
  await expect(
    navigation.getByRole("link", { name: "CLI Downloads" }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "Tool Call Debugger" }),
  ).toHaveCount(0);
});
