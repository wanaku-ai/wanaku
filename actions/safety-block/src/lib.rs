#[allow(warnings)]
mod bindings;

use bindings::wanaku::evaluator::types::EvaluationContext;
use bindings::Guest;

struct SafetyBlock;

impl Guest for SafetyBlock {
    fn evaluate(ctx: EvaluationContext) {
        let reason = format!(
            "Tool call blocked by safety classification: {}",
            ctx.llm_result
        );
        bindings::wanaku::evaluator::log::warn(&reason);
        bindings::wanaku::evaluator::response::block(&reason);
    }
}

bindings::export!(SafetyBlock with_types_in bindings);
