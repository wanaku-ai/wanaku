use std::path::PathBuf;
use std::time::Duration;

use serde::{Deserialize, Serialize};
use wanaku_infra::opa::{OpaClient, OpaError};

use crate::evaluation::EvaluationError;

/// Version of the input document that Wanaku sends to OPA.
pub const INPUT_VERSION: &str = "wanaku.opa.input/v1";
/// Version of the normalized result that Wanaku sends to the processor.
pub const RESULT_VERSION: &str = "wanaku.opa.result/v1";

const DEFAULT_TIMEOUT_MS: u64 = 2_000;
const MAX_TIMEOUT_MS: u64 = 30_000;

/// A named OPA connection. Connections are loaded only from `wanaku.yaml`.
#[derive(Debug, Clone, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct OpaConnection {
    pub name: String,
    /// Base URL of the OPA server, for example `http://127.0.0.1:8181`.
    pub url: String,
    /// Optional bearer token for OPA `--authentication=token`.
    #[serde(default)]
    pub token: Option<String>,
    #[serde(default = "default_timeout_ms")]
    pub timeout_ms: u64,
    /// Optional PEM file with a CA certificate to trust for `https` URLs.
    #[serde(default)]
    pub ca_cert: Option<PathBuf>,
}

const fn default_timeout_ms() -> u64 {
    DEFAULT_TIMEOUT_MS
}

impl OpaConnection {
    /// Validate the connection and build its HTTP client.
    pub fn client(&self) -> Result<OpaClient, String> {
        if !(self.url.starts_with("http://") || self.url.starts_with("https://")) {
            return Err(format!(
                "OPA connection '{}': url must start with http:// or https://",
                self.name
            ));
        }
        if !(1..=MAX_TIMEOUT_MS).contains(&self.timeout_ms) {
            return Err(format!(
                "OPA connection '{}': timeout_ms must be between 1 and {MAX_TIMEOUT_MS}",
                self.name
            ));
        }
        let ca_cert = match &self.ca_cert {
            Some(path) => Some(std::fs::read(path).map_err(|error| {
                format!(
                    "OPA connection '{}': cannot read ca_cert {}: {error}",
                    self.name,
                    path.display()
                )
            })?),
            None => None,
        };
        OpaClient::new(
            &self.url,
            self.token.as_deref(),
            Duration::from_millis(self.timeout_ms),
            ca_cert.as_deref(),
        )
        .map_err(|error| format!("OPA connection '{}': {error}", self.name))
    }
}

/// OPA engine configuration.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(deny_unknown_fields)]
pub struct OpaDef {
    /// Name of an entry in `opa_connections`.
    pub connection: String,
    /// Slash-separated OPA data path, for example `wanaku/tool_call/allow`.
    pub decision_path: String,
}

/// A decision path has one or more non-empty segments of ASCII letters,
/// digits, and underscores, separated by `/`.
pub fn validate_decision_path(path: &str) -> Result<(), String> {
    let valid = !path.is_empty()
        && path.split('/').all(|segment| {
            !segment.is_empty()
                && segment
                    .chars()
                    .all(|c| c.is_ascii_alphanumeric() || c == '_')
        });
    if valid {
        Ok(())
    } else {
        Err(format!("invalid OPA decision_path '{path}'"))
    }
}

/// Build the versioned OPA input document. It contains only the request
/// fields below. Headers, conversation history, and credentials are excluded.
pub fn input(
    method: &str,
    namespace: &str,
    tool_name: Option<&str>,
    arguments: &serde_json::Value,
) -> serde_json::Value {
    serde_json::json!({
        "version": INPUT_VERSION,
        "method": method,
        "namespace": namespace,
        "tool_name": tool_name,
        "arguments": arguments,
    })
}

/// Query OPA and normalize its decision for a WASM processor.
pub async fn execute(
    definition: &OpaDef,
    client: &OpaClient,
    input: &serde_json::Value,
) -> Result<String, EvaluationError> {
    let start = std::time::Instant::now();
    let result = client
        .query(&definition.decision_path, input)
        .await
        .map_err(|error| match error {
            OpaError::Unavailable => EvaluationError::RemoteUnavailable,
            OpaError::Timeout => EvaluationError::RemoteTimeout,
            OpaError::Auth => EvaluationError::RemoteAuth,
            OpaError::Policy => EvaluationError::Policy,
            OpaError::InvalidResponse => EvaluationError::DecisionInvalid,
        })
        .and_then(|result| normalize(&definition.decision_path, result));
    tracing::debug!(
        decision_path = %definition.decision_path,
        outcome = result.as_ref().err().map_or("decided", |error| error.reason_code()),
        latency_ms = start.elapsed().as_millis(),
        "OPA evaluation"
    );
    result
}

/// A decision is a boolean, or an object with a boolean `allow` and an
/// optional string `reason`. A missing or `null` result is a failure, so an
/// evaluation failure never looks like an explicit policy denial.
fn normalize(
    decision_path: &str,
    result: Option<serde_json::Value>,
) -> Result<String, EvaluationError> {
    let result = result.ok_or(EvaluationError::DecisionUndefined)?;
    let (allow, reason) = match &result {
        serde_json::Value::Bool(allow) => (*allow, None),
        serde_json::Value::Object(decision) => {
            let allow = decision
                .get("allow")
                .and_then(serde_json::Value::as_bool)
                .ok_or(EvaluationError::DecisionInvalid)?;
            let reason = match decision.get("reason") {
                None | Some(serde_json::Value::Null) => None,
                Some(serde_json::Value::String(reason)) => Some(reason.clone()),
                Some(_) => return Err(EvaluationError::DecisionInvalid),
            };
            (allow, reason)
        }
        _ => return Err(EvaluationError::DecisionInvalid),
    };
    serde_json::to_string(&serde_json::json!({
        "engine": "opa",
        "version": RESULT_VERSION,
        "decision_path": decision_path,
        "allow": allow,
        "reason": reason,
        "result": result,
    }))
    .map_err(|_| EvaluationError::Internal)
}

#[cfg(test)]
mod tests {
    use std::time::Duration;

    use wiremock::matchers::{body_json, method, path};
    use wiremock::{Mock, MockServer, ResponseTemplate};

    use super::*;

    fn normalized(result: serde_json::Value) -> Result<serde_json::Value, EvaluationError> {
        normalize("wanaku/allow", Some(result))
            .map(|value| serde_json::from_str(&value).expect("normalized JSON"))
    }

    #[test]
    fn boolean_decisions_normalize() {
        for allow in [true, false] {
            let value = normalized(serde_json::json!(allow)).unwrap();
            assert_eq!(value["engine"], "opa");
            assert_eq!(value["version"], RESULT_VERSION);
            assert_eq!(value["decision_path"], "wanaku/allow");
            assert_eq!(value["allow"], allow);
            assert_eq!(value["reason"], serde_json::Value::Null);
        }
    }

    #[test]
    fn structured_decision_keeps_reason_and_raw_result() {
        let decision =
            serde_json::json!({ "allow": false, "reason": "limit_exceeded", "limit": 100 });
        let value = normalized(decision.clone()).unwrap();
        assert_eq!(value["allow"], false);
        assert_eq!(value["reason"], "limit_exceeded");
        assert_eq!(value["result"], decision);
    }

    #[test]
    fn undefined_null_and_malformed_decisions_are_failures() {
        assert_eq!(
            normalize("p", None),
            Err(EvaluationError::DecisionUndefined)
        );
        for result in [
            serde_json::Value::Null,
            serde_json::json!("true"),
            serde_json::json!(1),
            serde_json::json!([true]),
            serde_json::json!({}),
            serde_json::json!({ "allow": "yes" }),
            serde_json::json!({ "allow": true, "reason": 7 }),
        ] {
            assert_eq!(normalized(result), Err(EvaluationError::DecisionInvalid));
        }
    }

    #[test]
    fn decision_path_validation() {
        for path in ["allow", "wanaku/tool_call/allow", "a1/B_2"] {
            assert!(validate_decision_path(path).is_ok(), "{path}");
        }
        for path in [
            "",
            "/wanaku/allow",
            "wanaku/allow/",
            "wanaku//allow",
            "wanaku/../allow",
            "wanaku/allow?pretty=true",
            "wanaku allow",
        ] {
            assert!(validate_decision_path(path).is_err(), "{path}");
        }
    }

    #[test]
    fn input_preserves_json_types_and_absent_tool_name() {
        let arguments = serde_json::json!({
            "amount": 250.5, "count": 3, "urgent": true, "tags": ["a"], "meta": { "k": null }
        });
        let value = input("tools/call", "finance", None, &arguments);
        assert_eq!(
            value,
            serde_json::json!({
                "version": INPUT_VERSION,
                "method": "tools/call",
                "namespace": "finance",
                "tool_name": null,
                "arguments": arguments,
            })
        );
    }

    #[test]
    fn connection_validation() {
        let connection = |url: &str, timeout_ms: u64| OpaConnection {
            name: "opa".to_owned(),
            url: url.to_owned(),
            token: None,
            timeout_ms,
            ca_cert: None,
        };
        assert!(connection("http://127.0.0.1:8181", 1).client().is_ok());
        assert!(connection("ftp://opa", 1).client().is_err());
        assert!(connection("http://opa", 0).client().is_err());
        assert!(
            connection("http://opa", MAX_TIMEOUT_MS + 1)
                .client()
                .is_err()
        );
        let missing_ca = OpaConnection {
            ca_cert: Some("/nonexistent/ca.pem".into()),
            ..connection("https://opa", 1)
        };
        assert!(missing_ca.client().is_err());
    }

    #[tokio::test]
    async fn execute_maps_failure_categories() {
        for (template, expected) in [
            (ResponseTemplate::new(401), EvaluationError::RemoteAuth),
            (ResponseTemplate::new(500), EvaluationError::Policy),
            (
                ResponseTemplate::new(200).set_body_json(serde_json::json!({})),
                EvaluationError::DecisionUndefined,
            ),
            (
                ResponseTemplate::new(200).set_body_string("not json"),
                EvaluationError::DecisionInvalid,
            ),
            (
                ResponseTemplate::new(200).set_delay(Duration::from_secs(2)),
                EvaluationError::RemoteTimeout,
            ),
        ] {
            let server = MockServer::start().await;
            Mock::given(method("POST"))
                .respond_with(template)
                .mount(&server)
                .await;
            let client = OpaClient::new(&server.uri(), None, Duration::from_millis(200), None)
                .expect("client");
            let definition = OpaDef {
                connection: "opa".to_owned(),
                decision_path: "wanaku/allow".to_owned(),
            };
            let result = execute(&definition, &client, &serde_json::json!({})).await;
            assert_eq!(result, Err(expected));
        }
    }

    #[tokio::test]
    async fn policy_updates_change_decisions_without_reconfiguration() {
        let server = MockServer::start().await;
        let client =
            OpaClient::new(&server.uri(), None, Duration::from_secs(1), None).expect("client");
        let definition = OpaDef {
            connection: "opa".to_owned(),
            decision_path: "wanaku/allow".to_owned(),
        };
        let input = input("tools/call", "default", Some("t"), &serde_json::json!({}));
        for allow in [true, false] {
            server.reset().await;
            Mock::given(method("POST"))
                .and(path("/v1/data/wanaku/allow"))
                .and(body_json(serde_json::json!({ "input": input })))
                .respond_with(
                    ResponseTemplate::new(200)
                        .set_body_json(serde_json::json!({ "result": allow })),
                )
                .mount(&server)
                .await;
            let result = execute(&definition, &client, &input).await.unwrap();
            let result: serde_json::Value = serde_json::from_str(&result).unwrap();
            assert_eq!(result["allow"], allow);
        }
    }
}
