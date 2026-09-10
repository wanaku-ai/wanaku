import {getInferenceUrl} from "../../custom-fetch.ts"


interface ModelCache {
  models: string[]
}

let cache: ModelCache | null = null


export async function listModels(apiKey: string): Promise<string[]> {
  if (cache) {
    return cache.models
  }
  if (!apiKey) {
    throw new Error("API key is required")
  }
  const response = await fetch(getInferenceUrl("/v1/models"), {
    headers: { Authorization: `Bearer ${apiKey}` }
  })
  if (response.ok) {
    const body: { data: { id: string }[] } = await response.json()
    const models = body.data.map(model => model.id)
    cache = { models }
    return models
  } else {
    throw new Error(`Failed to fetch models: ${response.status} ${response.statusText}`)
  }
}