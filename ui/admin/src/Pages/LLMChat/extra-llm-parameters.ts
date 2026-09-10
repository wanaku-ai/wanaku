/**
 *
 * @throws  SyntaxError - Thrown if the string to parse is not valid JSON.
 *
 * @throws  TypeError - Thrown if the JSON object contains invalid types.
 *
 * @throws  RangeError - Thrown if the JSON object contains invalid values.
 */
export function validateExtraLlmParameters(extraLlmParameters: string) {
  if (extraLlmParameters.trim().length === 0) {
    return
  }
  const parameters = JSON.parse(extraLlmParameters) as Record<string, unknown>
  
  if ("top_k" in parameters) {
    assertIsInteger("top_k", parameters)
    const top_k = parameters.top_k as number
    if (top_k < 0 && top_k !== -1) {
      throw new RangeError("Parameter 'top_k' must be positive number, greater than zero");
    }
  }
  assertNumberIfPresent("top_p", parameters)
  assertNumberIfPresent("min_p", parameters)
  assertNumberIfPresent("temperature", parameters)
  assertNumberIfPresent("presence_penalty", parameters)
  assertNumberIfPresent("frequency_penalty", parameters)
  assertNumberIfPresent("repetition_penalty", parameters)
  assertNumberIfPresent("typical_p", parameters)
  assertIntegerIfPresent("mirostat_mode", parameters)
  assertNumberIfPresent("mirostat_tau", parameters)
  assertNumberIfPresent("mirostat_eta", parameters)
  assertIntegerIfPresent("max_tokens", parameters)
  assertIntegerIfPresent("max_completion_tokens", parameters)
  assertStringOrStringArrayIfPresent("stop", parameters)
  assertIntegerIfPresent("n", parameters)
  assertIntegerOrNullIfPresent("seed", parameters)
  assertObjectIfPresent ("response_format", parameters)
  assertObjectIfPresent ("logit_bias", parameters)
  assertBooleanIfPresent("logprobs", parameters)
  assertIntegerIfPresent("top_logprobs", parameters)
  assertStringConstantIfPresent("reasoning_effort", ["low", "medium", "high"], parameters)
  assertBooleanIfPresent("stream", parameters)
  assertObjectIfPresent("stream_options", parameters)
  assertStringIfPresent("user", parameters)
}

function assertNumberIfPresent(parameter: string, parameters: Record<string, unknown>) {
  if (parameter in parameters) {
    if (typeof parameters[parameter] !== "number") {
      throw new TypeError(`Parameter '${parameter}' must be a number`)
    }
  }
}

function assertIntegerIfPresent(parameter: string, parameters: Record<string, unknown>) {
  if (parameter in parameters) {
    assertIsInteger(parameter, parameters)
  }
}

function assertIntegerOrNullIfPresent(parameter: string, parameters: Record<string, unknown>) {
  if (parameter in parameters) {
    if (!isIntegerOrNull(parameters[parameter])) {
      throw new TypeError(`Parameter ${parameter} must be an integer or null`)
    }
  }
}

function assertStringIfPresent(parameter: string, parameters: Record<string, unknown>) {
  if (parameter in parameters) {
    if (typeof parameters[parameter] !== "string") {
      throw new TypeError(`Parameter '${parameter}' must be a string`)
    }
  }
}

function assertStringConstantIfPresent(parameter: string, expected: string[], parameters: Record<string, unknown>) {
  if (parameter in parameters) {
    if (!expected.includes(parameters[parameter] as string)) {
      throw new TypeError(`Parameter '${parameter}' must be one of: ${expected.join(", ")}`)
    }
  }
}

function assertBooleanIfPresent(parameter: string, parameters: Record<string, unknown>) {
  if (parameter in parameters) {
    if (typeof parameters[parameter] !== "boolean") {
      throw new TypeError(`Parameter '${parameter}' must be a boolean`)
    }
  }
}

function assertStringOrStringArrayIfPresent(parameter: string, parameters: Record<string, unknown>) {
  if (parameter in parameters) {
    if (!isStringOrStringArray(parameters[parameter])) {
      throw new TypeError(`Parameter ${parameter} must be a string or an array of strings`)
    }
  }
}

function assertObjectIfPresent(parameter: string, parameters: Record<string, unknown>) {
  if (parameter in parameters) {
    if (typeof parameters[parameter] !== "object") {
      throw new TypeError(`Parameter '${parameter}' must be an object`)
    }
  }
}

function assertIsInteger(parameter: string, parameters: Record<string, unknown>) {
  if (!Number.isInteger(parameters[parameter])) {
    throw new TypeError(`Parameter '${parameter}' must be a number`)
  }
}

function isStringOrStringArray(value: unknown): value is string | string[] {
  if (typeof value === "string") {
    return true
  }
  if (Array.isArray(value)) {
    return value.every((item) => typeof item === 'string')
  }
  return false
}

function isIntegerOrNull(value: unknown): value is number | null {
  return (value === null) ? true : Number.isInteger(value)
}