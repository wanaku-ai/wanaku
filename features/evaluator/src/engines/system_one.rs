use serde::{Deserialize, Serialize};
use wanaku_types::mcp::McpContext;

use crate::state::EvaluatorState;

/// A named TypeSafe System One connection. Connections are loaded only from
/// `wanaku.yaml`.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SystemOneConnection {
    pub name: String,
    #[serde(default = "default_model")]
    pub model: String,
    #[serde(default = "default_url")]
    pub url: String,
    pub api_key: String,
}

fn default_model() -> String {
    "jev-latest".to_owned()
}

fn default_url() -> String {
    "https://api.typesafe.ai".to_owned()
}

/// TypeSafe System One engine configuration. It currently supports Noul.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(deny_unknown_fields)]
pub struct SystemOneDef {
    pub connection: String,
    #[serde(default)]
    pub state: SystemOneState,
    pub noul: NoulDef,
}

/// Selects the MCP data that becomes the TypeSafe state.
#[derive(Debug, Clone, Copy, Serialize, Deserialize, Default)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(rename_all = "snake_case")]
pub enum SystemOneState {
    Arguments,
    #[default]
    Context,
}

/// A TypeSafe Noul primitive.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(deny_unknown_fields)]
pub struct NoulDef {
    pub id: String,
    pub instructions: serde_json::Value,
    #[serde(default)]
    pub criteria: Option<NoulCriteria>,
}

/// Optional descriptions for Noul true and false outcomes.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(deny_unknown_fields)]
pub struct NoulCriteria {
    #[serde(rename = "true")]
    pub yes: serde_json::Value,
    #[serde(rename = "false")]
    pub no: serde_json::Value,
}

/// Execute TypeSafe System One and normalize its response for a WASM processor.
pub async fn execute(
    definition: &SystemOneDef,
    state: &EvaluatorState,
    mcp: &McpContext<'_>,
) -> Result<String, String> {
    let connection = state
        .get_system_one_connection(&definition.connection)
        .ok_or_else(|| {
            format!(
                "evaluator TypeSafe System One connection '{}' not available",
                definition.connection
            )
        })?;
    let client = wanaku_infra::typesafe::SystemOneClient::new(
        &connection.url,
        &connection.model,
        &connection.api_key,
    )
    .ok_or_else(|| "failed to initialize TypeSafe System One client".to_owned())?;
    let response = client
        .evaluate(
            &request_state(definition.state, mcp),
            &definition.noul.id,
            &noul_question(&definition.noul),
        )
        .await?;
    normalize_noul_response(&definition.noul.id, &response)
}

fn request_state(mapping: SystemOneState, mcp: &McpContext<'_>) -> serde_json::Value {
    match mapping {
        SystemOneState::Arguments => serde_json::json!(mcp.arguments),
        SystemOneState::Context => serde_json::json!({
            "method": mcp.method,
            "tool_name": mcp.tool_name,
            "arguments": mcp.arguments,
            "tools": mcp.tools,
            "history": mcp.history,
        }),
    }
}

fn noul_question(definition: &NoulDef) -> serde_json::Value {
    let mut question = serde_json::json!({
        "type": "noul",
        "instructions": definition.instructions,
    });
    if let Some(criteria) = &definition.criteria {
        question["criteria"] = serde_json::json!({
            "true": criteria.yes,
            "false": criteria.no,
        });
    }
    question
}

fn normalize_noul_response(
    question_id: &str,
    response: &serde_json::Value,
) -> Result<String, String> {
    let answer = response
        .pointer(&format!("/answers/{question_id}"))
        .ok_or_else(|| format!("TypeSafe System One response has no answer for '{question_id}'"))?;
    let probability = answer
        .get("noul")
        .and_then(serde_json::Value::as_f64)
        .filter(|value| (0.0..=1.0).contains(value))
        .ok_or_else(|| {
            format!("TypeSafe System One answer '{question_id}' has an invalid noul value")
        })?;
    serde_json::to_string(&serde_json::json!({
        "engine": "typesafe-system-one",
        "primitive": { "id": question_id, "type": "noul" },
        "answer": { "noul": probability },
        "probabilities": { "true": probability, "false": 1.0 - probability },
    }))
    .map_err(|error| format!("failed to serialize normalized System One result: {error}"))
}

#[cfg(test)]
mod tests {
    use super::normalize_noul_response;

    #[test]
    fn normalizes_noul_response_for_the_processor() {
        let response = serde_json::json!({ "answers": { "allowed": { "noul": 0.8 } } });
        let normalized =
            normalize_noul_response("allowed", &response).expect("response normalizes");
        let normalized: serde_json::Value =
            serde_json::from_str(&normalized).expect("normalized JSON");
        assert_eq!(normalized["engine"], "typesafe-system-one");
        assert_eq!(normalized["primitive"]["id"], "allowed");
        assert_eq!(normalized["answer"]["noul"], 0.8);
        let false_probability = normalized["probabilities"]["false"]
            .as_f64()
            .expect("false probability");
        assert!((false_probability - 0.2).abs() < f64::EPSILON);
    }

    #[test]
    fn rejects_invalid_noul_response() {
        let response = serde_json::json!({ "answers": { "allowed": { "noul": 2.0 } } });
        assert!(normalize_noul_response("allowed", &response).is_err());
    }
}
