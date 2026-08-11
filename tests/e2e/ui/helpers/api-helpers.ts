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

  async addTool(tool: { name: string; description: string; uri: string; type?: string; inputSchema?: object }) {
    const resp = await this.request.post(`${this.baseUrl}/api/v1/tools`, {
      data: {
        name: tool.name,
        description: tool.description,
        uri: tool.uri,
        type: tool.type ?? 'http',
        inputSchema: tool.inputSchema ?? { type: 'object', properties: {} },
      },
    });
    return this.assertOk(resp, `addTool(${tool.name})`);
  }

  async getTool(name: string) {
    const resp = await this.request.get(`${this.baseUrl}/api/v1/tools/${name}`);
    return this.assertOk(resp, `getTool(${name})`);
  }

  async deleteTool(name: string) {
    return this.request.delete(`${this.baseUrl}/api/v1/tools/${name}`);
  }

  async addResource(resource: { name: string; description: string; location: string; type?: string; mimeType?: string }) {
    const resp = await this.request.post(`${this.baseUrl}/api/v1/resources`, {
      data: {
        name: resource.name,
        description: resource.description,
        location: resource.location,
        type: resource.type ?? 'file',
        mimeType: resource.mimeType ?? 'application/json',
      },
    });
    return this.assertOk(resp, `addResource(${resource.name})`);
  }

  async getResource(name: string) {
    const resp = await this.request.get(`${this.baseUrl}/api/v1/resources/${name}`);
    return this.assertOk(resp, `getResource(${name})`);
  }

  async deleteResource(name: string) {
    return this.request.delete(`${this.baseUrl}/api/v1/resources/${name}`);
  }

  async addPrompt(prompt: { name: string; description: string; messages?: unknown[]; arguments?: unknown[] }) {
    const resp = await this.request.post(`${this.baseUrl}/api/v1/prompts`, {
      data: {
        name: prompt.name,
        description: prompt.description,
        messages: prompt.messages ?? [],
        arguments: prompt.arguments ?? [],
      },
    });
    return this.assertOk(resp, `addPrompt(${prompt.name})`);
  }

  async deletePrompt(name: string) {
    return this.request.delete(`${this.baseUrl}/api/v1/prompts/${name}`);
  }
}
