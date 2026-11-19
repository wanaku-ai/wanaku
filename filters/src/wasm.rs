//! Terminal WASM dispatch shares the same preceding filters as MCP forwarding.

use std::{collections::HashMap, sync::Arc};

use bytes::Bytes;
use praxis_filter::{FilterAction, FilterError, HttpFilterContext};
use serde_json::{Value, json};
use wanaku_types::registry::ToolEntry;
use wanaku_types::wasm::{
    HostError, HostErrorKind, InvocationContext, McpHostFactory, ToolHandler, ToolTarget,
};

pub(crate) fn validate_arguments(
    tool: &ToolEntry,
    arguments: &HashMap<String, Value>,
) -> Result<(), String> {
    // Correlation is transport metadata. The registry advertises this reserved
    // argument, but tool_call strips it before either local or remote execution.
    let mut schema = tool.input_schema.clone();
    if let Some(required) = schema.get_mut("required").and_then(Value::as_array_mut) {
        required.retain(|name| name.as_str() != Some(wanaku_types::correlation::REQUEST_ID_ARG));
    }
    let validator = jsonschema::validator_for(&schema)
        .map_err(|_| "registered tool input schema is invalid".to_owned())?;
    if !validator.is_valid(&json!(arguments)) {
        return Err("tool arguments do not match the registered input schema".to_owned());
    }
    Ok(())
}

pub(crate) async fn call_handler(
    ctx: &mut HttpFilterContext<'_>,
    id: &Value,
    tool: &ToolEntry,
    arguments: &HashMap<String, Value>,
) -> Result<FilterAction, FilterError> {
    if let Err(message) = validate_arguments(tool, arguments) {
        return Ok(crate::response::json_rpc_error(
            id,
            crate::response::JSONRPC_INVALID_PARAMS,
            &message,
        ));
    }
    Ok(match execute(ctx, tool, json!(arguments)).await {
        Ok(result) => success_response(ctx, id, tool, result),
        Err(error) => error_response(id, &error),
    })
}

fn success_response(
    ctx: &HttpFilterContext<'_>,
    id: &Value,
    tool: &ToolEntry,
    mut result: Value,
) -> FilterAction {
    if ctx
        .get_metadata(wanaku_types::VIRTUAL_NAMESPACE_METADATA_KEY)
        .is_some()
    {
        let namespace = tool
            .namespace
            .as_deref()
            .unwrap_or(wanaku_types::registry::DEFAULT_NAMESPACE);
        crate::namespace::qualify_resource_result(&mut result, namespace, crate::TOOLS_CALL);
    }
    FilterAction::Reject(crate::response::json_response(Bytes::from(
        json!({"jsonrpc":"2.0", "id":id, "result":result}).to_string(),
    )))
}

fn error_response(id: &Value, error: &HostError) -> FilterAction {
    let code = error.code.unwrap_or(match error.kind {
        HostErrorKind::Denied => -32003,
        HostErrorKind::InvalidArguments => crate::response::JSONRPC_INVALID_PARAMS,
        _ => crate::response::JSONRPC_INTERNAL_ERROR,
    });
    crate::response::json_rpc_host_error(id, code, error.kind, &error.message)
}

async fn execute(
    ctx: &mut HttpFilterContext<'_>,
    tool: &ToolEntry,
    arguments: Value,
) -> Result<Value, HostError> {
    let target = ToolTarget {
        namespace: tool
            .namespace
            .clone()
            .unwrap_or_else(|| wanaku_types::registry::DEFAULT_NAMESPACE.to_owned()),
        name: tool.name.clone(),
        binding_id: tool.id.clone(),
    };
    let context = ctx
        .extensions
        .get::<InvocationContext>()
        .cloned()
        .unwrap_or_default()
        .enter(target.clone())?;
    ctx.extensions.insert(context);
    let factory = ctx
        .extensions
        .get::<Arc<dyn McpHostFactory>>()
        .ok_or_else(|| HostError::new(HostErrorKind::Unavailable, "MCP host client unavailable"))?;
    let client = factory.create(ctx)?;
    let handler = ctx
        .extensions
        .get::<Arc<dyn ToolHandler>>()
        .ok_or_else(|| {
            HostError::new(HostErrorKind::Unavailable, "WASM tool runtime unavailable")
        })?;
    handler.call(target, arguments, client).await
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tool(schema: &Value) -> ToolEntry {
        serde_json::from_value(json!({
            "name":"test", "description":"test", "uri":"test", "type":"wasm",
            "inputSchema":schema,
        }))
        .unwrap()
    }

    #[test]
    fn validates_typed_arguments_after_transport_correlation_is_removed() {
        let tool = tool(&json!({
            "type":"object", "properties":{"enabled":{"type":"boolean"},"x-request-id":{"type":"string"}},
            "required":["enabled","x-request-id"], "additionalProperties":false,
        }));
        assert!(
            validate_arguments(&tool, &HashMap::from([("enabled".into(), json!(true))])).is_ok()
        );
        assert!(
            validate_arguments(&tool, &HashMap::from([("enabled".into(), json!("true"))])).is_err()
        );
        assert!(validate_arguments(&tool, &HashMap::new()).is_err());
    }
}
