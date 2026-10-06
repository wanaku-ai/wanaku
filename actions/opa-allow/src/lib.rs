#[allow(warnings)]
mod bindings;

use bindings::Guest;
use bindings::wanaku::evaluator::types::EvaluationContext;

struct OpaAllow;

impl Guest for OpaAllow {
    fn evaluate(ctx: EvaluationContext) {
        match decide(&ctx.llm_result) {
            Decision::Allow => bindings::wanaku::evaluator::response::pass(),
            Decision::Deny(reason) => {
                bindings::wanaku::evaluator::log::warn(&format!("OPA denied: {reason}"));
                bindings::wanaku::evaluator::response::block(&format!(
                    "denied by policy: {reason}"
                ));
            }
        }
    }
}

#[derive(Debug, PartialEq)]
enum Decision {
    Allow,
    Deny(String),
}

/// Read the normalized OPA result (`wanaku.opa.result/v1`). Only an explicit
/// `"allow": true` passes. Any other input blocks.
fn decide(engine_result: &str) -> Decision {
    let Ok(value) = serde_json::from_str::<serde_json::Value>(engine_result) else {
        return Decision::Deny("invalid_decision".to_string());
    };
    if value.get("engine").and_then(serde_json::Value::as_str) != Some("opa") {
        return Decision::Deny("invalid_decision".to_string());
    }
    if value.get("allow").and_then(serde_json::Value::as_bool) == Some(true) {
        return Decision::Allow;
    }
    let reason = value
        .get("reason")
        .and_then(serde_json::Value::as_str)
        .unwrap_or("not_allowed");
    Decision::Deny(reason.to_string())
}

bindings::export!(OpaAllow with_types_in bindings);

#[cfg(test)]
mod tests {
    use super::{Decision, decide};

    #[test]
    fn allows_only_explicit_allow() {
        assert_eq!(decide(r#"{"engine":"opa","allow":true}"#), Decision::Allow);
    }

    #[test]
    fn denies_with_policy_reason() {
        assert_eq!(
            decide(r#"{"engine":"opa","allow":false,"reason":"amount_over_limit"}"#),
            Decision::Deny("amount_over_limit".to_string())
        );
        assert_eq!(
            decide(r#"{"engine":"opa","allow":false,"reason":null}"#),
            Decision::Deny("not_allowed".to_string())
        );
    }

    #[test]
    fn denies_unexpected_input() {
        for input in ["", "true", r#"{"allow":true}"#, r#"{"engine":"llm","allow":true}"#] {
            assert_eq!(
                decide(input),
                Decision::Deny("invalid_decision".to_string())
            );
        }
    }
}
