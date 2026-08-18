import { APIRequestContext, APIResponse, expect } from '@playwright/test';

export class ApiHelper {
  private readonly baseUrl: string;

  constructor(
    private readonly request: APIRequestContext,
    routerUrl: string,
  ) {
    this.baseUrl = routerUrl;
  }

  private async assertOk(response: APIResponse, context: string): Promise<APIResponse> {
    expect(response.ok(), `${context}: expected 2xx but got ${response.status()}`).toBeTruthy();
    return response;
  }

  async getTool(name: string) {
    const resp = await this.request.get(`${this.baseUrl}/api/v1/tools/${name}`);
    return this.assertOk(resp, `getTool(${name})`);
  }

  async deleteTool(name: string) {
    return this.request.delete(`${this.baseUrl}/api/v1/tools/${name}`);
  }

  async getResource(name: string) {
    const resp = await this.request.get(`${this.baseUrl}/api/v1/resources/${name}`);
    return this.assertOk(resp, `getResource(${name})`);
  }

  async deleteResource(name: string) {
    return this.request.delete(`${this.baseUrl}/api/v1/resources/${name}`);
  }

  async deletePrompt(name: string) {
    return this.request.delete(`${this.baseUrl}/api/v1/prompts/${name}`);
  }

  async addForward(forward: { name: string; address: string; namespace?: string }) {
    const resp = await this.request.post(`${this.baseUrl}/api/v1/forwards`, {
      data: {
        name: forward.name,
        address: forward.address,
        namespace: forward.namespace,
      },
    });
    return this.assertOk(resp, `addForward(${forward.name})`);
  }

  async getForward(name: string) {
    const resp = await this.request.get(`${this.baseUrl}/api/v1/forwards/${name}`);
    return this.assertOk(resp, `getForward(${name})`);
  }

  async deleteForward(name: string) {
    return this.request.delete(`${this.baseUrl}/api/v1/forwards/${name}`);
  }
}
