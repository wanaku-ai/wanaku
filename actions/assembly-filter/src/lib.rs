#[allow(warnings)]
mod bindings;

use bindings::Guest;
use bindings::wanaku::evaluator::types::EvaluationContext;

/// Namespace that holds the shared tool catalog.
const DEFAULT_NAMESPACE: &str = "default";

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

        // Assemble only from the shared catalog or the request namespace, so
        // the LLM result cannot pull in tools that belong to other namespaces.
        let ns = &ctx.namespace;
        let mut assembled = Vec::new();
        for name in approved {
            let Some(tool) = bindings::wanaku::evaluator::registry::get_tool(&name) else {
                continue;
            };
            let source = tool.namespace.as_deref().unwrap_or(DEFAULT_NAMESPACE);
            if source != DEFAULT_NAMESPACE && source != ns {
                bindings::wanaku::evaluator::log::warn(&format!(
                    "Skipping tool '{name}' from namespace '{source}'"
                ));
                continue;
            }
            bindings::wanaku::evaluator::registry::copy_tool_to_namespace(&name, ns);
            assembled.push(name);
        }
        bindings::wanaku::evaluator::log::info(&format!(
            "Registered {} tools into namespace '{ns}'",
            assembled.len(),
        ));

        bindings::wanaku::evaluator::response::filter_tools(&assembled);
    }
}

bindings::export!(AssemblyFilter with_types_in bindings);
