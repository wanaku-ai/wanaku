import { BasePage } from './base.page';

export class PluginCatalogPage extends BasePage {
  async goto() { await this.navigateTo('/plugin-catalog'); }
  infoButton(name: string) { return this.page.getByRole('button', { name: `Info for ${name}`, exact: true }); }
  installButton(name: string) { return this.page.getByRole('button', { name: `Install ${name}`, exact: true }); }
}
