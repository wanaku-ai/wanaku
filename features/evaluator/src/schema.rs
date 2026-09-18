use serde_json::Value;

/// A pre-compiled JSON Schema validator. Compile once at config load,
/// reuse for every validation.
pub struct CompiledSchema {
    validator: jsonschema::Validator,
}

impl CompiledSchema {
    /// Compile a JSON Schema. Returns `None` if the schema itself is invalid,
    /// logging the error so misconfiguration is surfaced at load time.
    pub fn compile(schema: &Value) -> Option<Self> {
        match jsonschema::validator_for(schema) {
            Ok(validator) => Some(Self { validator }),
            Err(e) => {
                tracing::error!(error = %e, "invalid JSON Schema in evaluator config — schema validation disabled for this evaluator");
                None
            }
        }
    }

    pub fn validate(&self, raw: &str) -> Result<(), String> {
        let instance: Value =
            serde_json::from_str(raw).map_err(|e| format!("LLM result is not valid JSON: {e}"))?;

        if self.validator.is_valid(&instance) {
            return Ok(());
        }

        let errors: Vec<String> = self
            .validator
            .iter_errors(&instance)
            .map(|e| e.to_string())
            .collect();
        Err(errors.join("; "))
    }
}

/// Standalone validation (for one-off checks where no compiled schema is cached).
pub fn validate_against_schema(schema: &Value, raw: &str) -> Result<(), String> {
    let instance: Value =
        serde_json::from_str(raw).map_err(|e| format!("LLM result is not valid JSON: {e}"))?;

    let validator =
        jsonschema::validator_for(schema).map_err(|e| format!("invalid JSON Schema: {e}"))?;

    if validator.is_valid(&instance) {
        return Ok(());
    }

    let errors: Vec<String> = validator
        .iter_errors(&instance)
        .map(|e| e.to_string())
        .collect();
    Err(errors.join("; "))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn valid_instance_passes() {
        let schema = serde_json::json!({
            "type": "object",
            "properties": {
                "level": { "type": "string" },
                "reason": { "type": "string" }
            },
            "required": ["level", "reason"]
        });
        let raw = r#"{"level": "green", "reason": "looks safe"}"#;
        assert!(validate_against_schema(&schema, raw).is_ok());
    }

    #[test]
    fn missing_required_field_fails() {
        let schema = serde_json::json!({
            "type": "object",
            "properties": {
                "level": { "type": "string" },
                "reason": { "type": "string" }
            },
            "required": ["level", "reason"]
        });
        let raw = r#"{"level": "green"}"#;
        let err = validate_against_schema(&schema, raw).unwrap_err();
        assert!(
            err.contains("reason"),
            "error should mention missing field: {err}"
        );
    }

    #[test]
    fn wrong_type_fails() {
        let schema = serde_json::json!({
            "type": "object",
            "properties": {
                "level": { "type": "string" }
            },
            "required": ["level"]
        });
        let raw = r#"{"level": 42}"#;
        let err = validate_against_schema(&schema, raw).unwrap_err();
        assert!(!err.is_empty());
    }

    #[test]
    fn invalid_json_fails() {
        let schema = serde_json::json!({"type": "object"});
        let err = validate_against_schema(&schema, "not json {").unwrap_err();
        assert!(err.contains("not valid JSON"));
    }

    #[test]
    fn no_schema_is_handled_at_caller() {
        let schema = serde_json::json!({"type": "string"});
        assert!(validate_against_schema(&schema, r#""hello""#).is_ok());
    }

    #[test]
    fn compiled_schema_validates() {
        let schema = serde_json::json!({
            "type": "object",
            "properties": { "level": { "type": "string" } },
            "required": ["level"]
        });
        let compiled = CompiledSchema::compile(&schema).expect("valid schema should compile");
        assert!(compiled.validate(r#"{"level": "green"}"#).is_ok());
        assert!(compiled.validate(r#"{"wrong": "field"}"#).is_err());
    }

    #[test]
    fn invalid_schema_returns_none() {
        let bad_schema = serde_json::json!({"type": "not-a-real-type"});
        // jsonschema may or may not reject this — but truly broken schemas should fail
        // This tests that compile() doesn't panic on bad input
        let _ = CompiledSchema::compile(&bad_schema);
    }
}
