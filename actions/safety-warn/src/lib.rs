#[allow(warnings)]
mod bindings;

use bindings::wanaku::evaluator::types::EvaluationContext;
use bindings::Guest;

struct SafetyWarn;

impl Guest for SafetyWarn {
    fn evaluate(ctx: EvaluationContext) {
        let message = format!(
            "Safety warning for tool call: {}",
            ctx.llm_result
        );
        bindings::wanaku::evaluator::log::warn(&message);
        bindings::wanaku::evaluator::response::warn(&message);
    }
}

bindings::export!(SafetyWarn with_types_in bindings);
