import { test, expect } from "@playwright/test";
import { DownloadsPage } from "../pages/downloads.page";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const manifest = readFileSync(
  resolve(__dirname, "../../../../Cargo.toml"),
  "utf8",
);
const version = manifest
  .split("[workspace.package]")[1]
  .match(/version\s*=\s*"([^"]+)"/)?.[1];
if (!version) throw new Error("Missing workspace version");
const earlyAccess = version.split("+")[0].includes("-");
const tag = earlyAccess ? "early-access" : "v0.3.0";
const artifactVersion = earlyAccess ? "0.3.0-SNAPSHOT" : "0.3.0";
const releasePage = `https://github.com/wanaku-ai/wanaku-barn/releases/tag/${tag}`;
const downloadBase = `https://github.com/wanaku-ai/wanaku-barn/releases/download/${tag}`;
const artifacts = [
  `wanaku-cli-${artifactVersion}.zip`,
  `wanaku-cli-${artifactVersion}-linux-x86_64.zip`,
];

const routerUrl = process.env.WANAKU_ROUTER_URL ?? "http://localhost:8080";

test.describe("CLI Downloads", () => {
  let downloads: DownloadsPage;

  test.beforeEach(async ({ page }) => {
    downloads = new DownloadsPage(page, `${routerUrl}/admin/`);
  });

  test("displays page title", async () => {
    await downloads.goto();
    const title = await downloads.getPageTitle();
    expect(title).toBe("CLI Downloads");
  });

  test("lists CLI Downloads in the Developer menu", async ({ page }) => {
    await downloads.goto();
    const headerNavigation = page.getByRole("navigation", { name: "Wanaku" });
    await headerNavigation
      .getByRole("link", { name: "Developer", exact: true })
      .click();

    await expect(
      headerNavigation.getByRole("link", { name: "CLI Downloads" }),
    ).toBeVisible();
  });

  test("lists CLI packages with download links", async () => {
    await downloads.goto();
    await expect(downloads.downloadButtons().first()).toBeVisible({
      timeout: 5_000,
    });
    expect(await downloads.downloadButtons().count()).toBeGreaterThan(0);
  });

  test("links to the full release page", async () => {
    await downloads.goto();
    await expect(downloads.viewAllPackagesLink()).toHaveAttribute(
      "href",
      releasePage,
    );
  });

  test("uses the package version channel for artifact and checksum links", async ({
    page,
  }) => {
    await downloads.goto();
    for (const [index, artifact] of artifacts.entries()) {
      await expect(downloads.downloadButtons().nth(index)).toHaveAttribute(
        "href",
        `${downloadBase}/${artifact}`,
      );
    }
    await expect(
      page.getByRole("link", { name: "published checksums" }),
    ).toHaveAttribute("href", `${downloadBase}/checksums_sha256.txt`);
  });
});
