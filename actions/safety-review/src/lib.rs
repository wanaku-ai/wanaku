#[allow(warnings)]
mod bindings;

use bindings::Guest;
use bindings::wanaku::evaluator::types::EvaluationContext;

struct SafetyReview;

impl Guest for SafetyReview {
    fn evaluate(ctx: EvaluationContext) {
        let details = format!(
            "Safety review triggered by method {} for tool name {} in namespace {} for conversation id {}",
            ctx.method,
            ctx.tool_name.clone().unwrap_or_default(),
            ctx.namespace,
            ctx.conversation_id.clone().unwrap_or_default()
        );
        bindings::wanaku::evaluator::log::info(&details);

        let (level, reason) = classify(&ctx.llm_result);

        match level.as_str() {
            "red" => {
                bindings::wanaku::evaluator::log::warn(&format!("Blocked: {reason}"));
                bindings::wanaku::evaluator::response::block(&format!(
                    "Tool call blocked by safety classification: {reason}"
                ));
            }
            "yellow" => {
                bindings::wanaku::evaluator::log::warn(&format!("Warning: {reason}"));
                bindings::wanaku::evaluator::response::warn(&format!("Safety warning: {reason}"));
            }
            _ => {
                bindings::wanaku::evaluator::response::pass();
            }
        }
    }
}

/// Extract the safety `level` and `reason` from the engine result.
///
/// A TypeSafe Noul result contains the probability that its safety condition
/// is true. A probability below 0.5 blocks the request. An LLM result uses
/// `{"level": "green|yellow|red", "reason": "..."}`.
fn classify(engine_result: &str) -> (String, String) {
    if let Ok(value) = serde_json::from_str::<serde_json::Value>(engine_result) {
        if let Some(result) = classify_system_one(&value) {
            return result;
        }
        let level = value
            .get("level")
            .and_then(serde_json::Value::as_str)
            .unwrap_or("green")
            .to_string();
        let reason = value
            .get("reason")
            .and_then(serde_json::Value::as_str)
            .unwrap_or(engine_result)
            .to_string();
        return (level, reason);
    }

    let lower = engine_result.to_lowercase();
    let level = if lower.contains("red") {
        "red"
    } else if lower.contains("yellow") {
        "yellow"
    } else {
        "green"
    };
    (level.to_string(), engine_result.to_string())
}

fn classify_system_one(value: &serde_json::Value) -> Option<(String, String)> {
    let is_noul = value.get("engine").and_then(serde_json::Value::as_str)
        == Some("typesafe-system-one")
        && value
            .pointer("/primitive/type")
            .and_then(serde_json::Value::as_str)
            == Some("noul");
    if !is_noul {
        return None;
    }
    let probability = value
        .pointer("/answer/noul")
        .and_then(serde_json::Value::as_f64)?;
    if !(0.0..=1.0).contains(&probability) {
        return None;
    }
    let level = if probability < 0.5 { "red" } else { "green" };
    Some((
        level.to_string(),
        format!("TypeSafe Noul safety probability: {probability:.2}"),
    ))
}

bindings::export!(SafetyReview with_types_in bindings);

#[cfg(test)]
mod tests {
    use super::classify;

    #[test]
    fn blocks_when_system_one_marks_the_request_unsafe() {
        let (level, reason) = classify(
            r#"{"engine":"typesafe-system-one","primitive":{"type":"noul"},"answer":{"noul":0.05}}"#,
        );
        assert_eq!(level, "red");
        assert!(reason.contains("0.05"));
    }

    #[test]
    fn allows_when_system_one_marks_the_request_safe() {
        let (level, _) = classify(
            r#"{"engine":"typesafe-system-one","primitive":{"type":"noul"},"answer":{"noul":0.95}}"#,
        );
        assert_eq!(level, "green");
    }
}
