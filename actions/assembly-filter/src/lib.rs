#[allow(warnings)]
mod bindings;

use bindings::wanaku::evaluator::types::EvaluationContext;
use bindings::Guest;

struct AssemblyFilter;

impl Guest for AssemblyFilter {
    fn evaluate(ctx: EvaluationContext) {
        let approved: Vec<String> = match serde_json::from_str(&ctx.llm_result) {
            Ok(names) => names,
            Err(_) => {
                bindings::wanaku::evaluator::log::warn(
                    "Failed to parse LLM result as tool name array, returning all tools",
                );
                bindings::wanaku::evaluator::response::pass();
                return;
            }
        };

        if approved.is_empty() {
            bindings::wanaku::evaluator::log::info(
                "LLM returned empty tool list, returning all tools (fail-open)",
            );
            bindings::wanaku::evaluator::response::pass();
            return;
        }

        let ns = &ctx.namespace;
        for name in &approved {
            bindings::wanaku::evaluator::registry::copy_tool_to_namespace(name, ns);
        }
        bindings::wanaku::evaluator::log::info(&format!(
            "Registered {} tools into namespace '{ns}'",
            approved.len(),
        ));

        bindings::wanaku::evaluator::response::filter_tools(&approved);
    }
}

bindings::export!(AssemblyFilter with_types_in bindings);
