import { defineConfig, devices } from '@playwright/test';

const routerUrl = process.env.WANAKU_ROUTER_URL ?? 'http://localhost:8080';
const isLocalhost = new URL(routerUrl).hostname === 'localhost' || new URL(routerUrl).hostname === '127.0.0.1';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['html', { open: 'never' }], ['list']],

  use: {
    baseURL: `${routerUrl}/admin/`,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  ...(isLocalhost ? {
    webServer: {
      command: 'WANAKU_HTTP_AUTH=none java -jar ../../../apps/wanaku-router-backend/target/quarkus-app/quarkus-run.jar',
      url: `${routerUrl}/q/health/ready`,
      reuseExistingServer: true,
      timeout: 60_000,
      stdout: 'pipe',
      stderr: 'pipe',
    },
  } : {}),
});
