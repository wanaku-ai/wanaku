#[allow(warnings)]
mod bindings;

use bindings::wanaku::evaluator::types::EvaluationContext;
use bindings::Guest;

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

/// Extract the safety `level` and `reason` from the LLM result.
///
/// The LLM is expected to return a JSON object of the form
/// `{"level": "green|yellow|red", "reason": "..."}`. When the result is not
/// valid JSON, the level is inferred from the raw text and the whole result
/// is used as the reason.
fn classify(llm_result: &str) -> (String, String) {
    if let Ok(value) = serde_json::from_str::<serde_json::Value>(llm_result) {
        let level = value
            .get("level")
            .and_then(serde_json::Value::as_str)
            .unwrap_or("green")
            .to_string();
        let reason = value
            .get("reason")
            .and_then(serde_json::Value::as_str)
            .unwrap_or(llm_result)
            .to_string();
        return (level, reason);
    }

    let lower = llm_result.to_lowercase();
    let level = if lower.contains("red") {
        "red"
    } else if lower.contains("yellow") {
        "yellow"
    } else {
        "green"
    };
    (level.to_string(), llm_result.to_string())
}

bindings::export!(SafetyReview with_types_in bindings);
