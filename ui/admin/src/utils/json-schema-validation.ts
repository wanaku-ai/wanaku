import Ajv, { AnySchema, ErrorObject } from "ajv"


export interface ValidationResult {
  valid: boolean
  errors: string[]
}


export function validateJsonSchema(jsonString: string): ValidationResult {
  let parsedSchema: AnySchema
  
  // Syntax Validation
  try {
    parsedSchema = JSON.parse(jsonString)
  } catch (error) {
    return { valid: false, errors: [(error as Error).message] }
  }
  
  // Semantic Validation
  const ajv = new Ajv({ allErrors: true })
  const valid = ajv.validateSchema(parsedSchema)
  
  if (valid) {
    return { valid: true, errors: [] }
  }

  const errors = ajv.errors || []
  return {
    valid: false,
    errors: errors.map(
      (error: ErrorObject) => `Error at '${error.instancePath || '/'}': ${error.message}`
    )
  }
}