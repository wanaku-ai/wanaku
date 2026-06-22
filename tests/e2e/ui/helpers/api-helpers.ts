import { APIRequestContext } from '@playwright/test';

export class ApiHelper {
  private readonly baseUrl: string;

  constructor(
    private readonly request: APIRequestContext,
    routerUrl: string,
  ) {
    this.baseUrl = routerUrl;
  }

  async addTool(tool: { name: string; description: string; uri: string; type?: string }) {
    return this.request.post(`${this.baseUrl}/api/v1/tools`, {
      data: {
        name: tool.name,
        description: tool.description,
        uri: tool.uri,
        type: tool.type ?? 'http',
      },
    });
  }

  async deleteTool(name: string) {
    return this.request.delete(`${this.baseUrl}/api/v1/tools/${name}`);
  }

  async addResource(resource: { name: string; description: string; location: string; type?: string; mimeType?: string }) {
    return this.request.post(`${this.baseUrl}/api/v1/resources`, {
      data: {
        name: resource.name,
        description: resource.description,
        location: resource.location,
        type: resource.type ?? 'file',
        mimeType: resource.mimeType ?? 'application/json',
      },
    });
  }

  async deleteResource(name: string) {
    return this.request.delete(`${this.baseUrl}/api/v1/resources/${name}`);
  }

  async addPrompt(prompt: { name: string; description: string; messages?: unknown[]; arguments?: unknown[] }) {
    return this.request.post(`${this.baseUrl}/api/v1/prompts`, {
      data: {
        name: prompt.name,
        description: prompt.description,
        messages: prompt.messages ?? [],
        arguments: prompt.arguments ?? [],
      },
    });
  }

  async deletePrompt(name: string) {
    return this.request.delete(`${this.baseUrl}/api/v1/prompts/${name}`);
  }
}
