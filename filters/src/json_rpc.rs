use bytes::Bytes;
use serde_json::{Map, Value};
use thiserror::Error;

/// A parsed JSON-RPC request with fallible, typed MCP adapters.
///
/// Parsing occurs once. Accessors borrow values from the parsed request and do
/// not convert missing or malformed fields to empty values.
pub struct McpRequestView {
    request: Map<String, Value>,
}

impl McpRequestView {
    /// Parse a complete buffered JSON-RPC request body.
    pub fn parse(body: Option<&Bytes>) -> Result<Self, RequestViewError> {
        let body = body.ok_or(RequestViewError::MissingBody)?;
        let value: Value = serde_json::from_slice(body)
            .map_err(|error| RequestViewError::MalformedJson(error.to_string()))?;
        let Value::Object(request) = value else {
            return Err(RequestViewError::RequestNotObject);
        };
        Ok(Self { request })
    }

    pub fn method(&self) -> Result<&str, RequestViewError> {
        required_string(&self.request, "method")
    }

    pub fn tool_call(&self) -> Result<ToolCallRequest<'_>, RequestViewError> {
        self.require_method("tools/call")?;
        Ok(ToolCallRequest {
            name: self.required_param_string("name")?,
            arguments: self.optional_param_object("arguments")?,
        })
    }

    pub fn resource_read(&self) -> Result<ResourceReadRequest<'_>, RequestViewError> {
        self.require_method("resources/read")?;
        Ok(ResourceReadRequest {
            uri: self.required_param_string("uri")?,
        })
    }

    pub fn prompt_get(&self) -> Result<PromptGetRequest<'_>, RequestViewError> {
        self.require_method("prompts/get")?;
        Ok(PromptGetRequest {
            name: self.required_param_string("name")?,
            arguments: self.optional_param_object("arguments")?,
        })
    }

    /// Return the typed JSON-RPC params object.
    pub fn params(&self) -> Result<&Map<String, Value>, RequestViewError> {
        match self.request.get("params") {
            None => Err(RequestViewError::MissingParams),
            Some(Value::Object(params)) => Ok(params),
            Some(_) => Err(RequestViewError::ParamsNotObject),
        }
    }

    fn require_method(&self, expected: &'static str) -> Result<(), RequestViewError> {
        let actual = self.method()?;
        if actual == expected {
            Ok(())
        } else {
            Err(RequestViewError::UnexpectedMethod {
                expected,
                actual: actual.to_owned(),
            })
        }
    }

    fn required_param_string(&self, field: &'static str) -> Result<&str, RequestViewError> {
        required_string(self.params()?, field)
    }

    fn optional_param_string(&self, field: &'static str) -> Result<Option<&str>, RequestViewError> {
        optional_string(self.params()?, field)
    }

    fn optional_param_object(
        &self,
        field: &'static str,
    ) -> Result<Option<&Map<String, Value>>, RequestViewError> {
        match self.params()?.get(field) {
            None => Ok(None),
            Some(Value::Object(value)) => Ok(Some(value)),
            Some(_) => Err(RequestViewError::InvalidFieldType {
                field,
                expected: "object",
            }),
        }
    }
}

/// A validated `tools/call` request view.
pub struct ToolCallRequest<'a> {
    name: &'a str,
    arguments: Option<&'a Map<String, Value>>,
}

impl ToolCallRequest<'_> {
    #[must_use]
    pub const fn name(&self) -> &str {
        self.name
    }

    #[must_use]
    pub const fn arguments(&self) -> Option<&Map<String, Value>> {
        self.arguments
    }
}

/// A validated `resources/read` request view.
pub struct ResourceReadRequest<'a> {
    uri: &'a str,
}

impl ResourceReadRequest<'_> {
    #[must_use]
    pub const fn uri(&self) -> &str {
        self.uri
    }
}

/// A validated `prompts/get` request view.
pub struct PromptGetRequest<'a> {
    name: &'a str,
    arguments: Option<&'a Map<String, Value>>,
}

impl PromptGetRequest<'_> {
    #[must_use]
    pub const fn name(&self) -> &str {
        self.name
    }

    #[must_use]
    pub const fn arguments(&self) -> Option<&Map<String, Value>> {
        self.arguments
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Error)]
pub enum RequestViewError {
    #[error("request body is missing")]
    MissingBody,
    #[error("request body is not valid JSON: {0}")]
    MalformedJson(String),
    #[error("JSON-RPC request must be an object")]
    RequestNotObject,
    #[error("JSON-RPC params are missing")]
    MissingParams,
    #[error("JSON-RPC params must be an object")]
    ParamsNotObject,
    #[error("expected JSON-RPC method '{expected}', got '{actual}'")]
    UnexpectedMethod {
        expected: &'static str,
        actual: String,
    },
    #[error("required field '{0}' is missing")]
    MissingField(&'static str),
    #[error("field '{field}' must be a {expected}")]
    InvalidFieldType {
        field: &'static str,
        expected: &'static str,
    },
}

fn required_string<'a>(
    object: &'a Map<String, Value>,
    field: &'static str,
) -> Result<&'a str, RequestViewError> {
    match object.get(field) {
        None => Err(RequestViewError::MissingField(field)),
        Some(Value::String(value)) => Ok(value),
        Some(_) => Err(RequestViewError::InvalidFieldType {
            field,
            expected: "string",
        }),
    }
}

fn optional_string<'a>(
    object: &'a Map<String, Value>,
    field: &'static str,
) -> Result<Option<&'a str>, RequestViewError> {
    match object.get(field) {
        None => Ok(None),
        Some(Value::String(value)) => Ok(Some(value)),
        Some(_) => Err(RequestViewError::InvalidFieldType {
            field,
            expected: "string",
        }),
    }
}

/// Compatibility parser for existing filters. New governed filters must use
/// [`McpRequestView`] so malformed input remains distinguishable.
#[derive(Default)]
pub(crate) struct JsonRpcParams {
    pub(crate) name: Option<String>,
    pub(crate) uri: Option<String>,
    pub(crate) arguments: Map<String, Value>,
}

impl JsonRpcParams {
    pub(crate) fn parse(body: &Option<Bytes>) -> Self {
        let Ok(view) = McpRequestView::parse(body.as_ref()) else {
            return Self::default();
        };
        let name = view
            .optional_param_string("name")
            .ok()
            .flatten()
            .map(str::to_owned);
        let uri = view
            .optional_param_string("uri")
            .ok()
            .flatten()
            .map(str::to_owned);
        let arguments = view
            .optional_param_object("arguments")
            .ok()
            .flatten()
            .cloned()
            .unwrap_or_default();
        Self {
            name,
            uri,
            arguments,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn body(value: &str) -> Option<Bytes> {
        Some(Bytes::copy_from_slice(value.as_bytes()))
    }

    #[test]
    fn adapters_preserve_typed_arguments() -> Result<(), Box<dyn std::error::Error>> {
        let request = body(
            r#"{"method":"tools/call","params":{"name":"lookup","arguments":{"null":null,"false":false,"zero":0,"empty":"","nested":{"a":1}}}}"#,
        );
        let view = McpRequestView::parse(request.as_ref())?;
        let call = view.tool_call()?;
        let arguments = call.arguments().ok_or("missing arguments")?;

        assert_eq!(view.method()?, "tools/call");
        assert_eq!(call.name(), "lookup");
        assert_eq!(arguments.get("null"), Some(&Value::Null));
        assert_eq!(arguments.get("false"), Some(&serde_json::json!(false)));
        assert_eq!(arguments.get("zero"), Some(&serde_json::json!(0)));
        assert_eq!(arguments.get("empty"), Some(&serde_json::json!("")));
        assert_eq!(arguments.get("nested"), Some(&serde_json::json!({"a": 1})));
        Ok(())
    }

    #[test]
    fn supports_resource_and_prompt_adapters() -> Result<(), Box<dyn std::error::Error>> {
        let resource = body(r#"{"method":"resources/read","params":{"uri":"file:///safe/item"}}"#);
        let resource_view = McpRequestView::parse(resource.as_ref())?;
        assert_eq!(resource_view.resource_read()?.uri(), "file:///safe/item");

        let prompt = body(r#"{"method":"prompts/get","params":{"name":"summary"}}"#);
        let prompt_view = McpRequestView::parse(prompt.as_ref())?;
        let prompt = prompt_view.prompt_get()?;
        assert_eq!(prompt.name(), "summary");
        assert!(prompt.arguments().is_none());
        Ok(())
    }

    #[test]
    fn distinguishes_body_and_request_errors() {
        assert_eq!(
            McpRequestView::parse(None).err(),
            Some(RequestViewError::MissingBody)
        );
        assert!(matches!(
            McpRequestView::parse(body("not json").as_ref()),
            Err(RequestViewError::MalformedJson(_))
        ));
        assert_eq!(
            McpRequestView::parse(body("[]").as_ref()).err(),
            Some(RequestViewError::RequestNotObject)
        );
    }

    #[test]
    fn distinguishes_missing_and_non_object_params() -> Result<(), Box<dyn std::error::Error>> {
        let missing = body(r#"{"method":"tools/call"}"#);
        let view = McpRequestView::parse(missing.as_ref())?;
        assert_eq!(
            view.tool_call().err(),
            Some(RequestViewError::MissingParams)
        );

        let invalid = body(r#"{"method":"tools/call","params":null}"#);
        let view = McpRequestView::parse(invalid.as_ref())?;
        assert_eq!(
            view.tool_call().err(),
            Some(RequestViewError::ParamsNotObject)
        );
        Ok(())
    }

    #[test]
    fn distinguishes_missing_and_wrong_method_types() -> Result<(), Box<dyn std::error::Error>> {
        let missing = body(r#"{"params":{}}"#);
        let view = McpRequestView::parse(missing.as_ref())?;
        assert_eq!(
            view.method().err(),
            Some(RequestViewError::MissingField("method"))
        );
        assert_eq!(
            view.tool_call().err(),
            Some(RequestViewError::MissingField("method"))
        );

        let invalid = body(r#"{"method":7,"params":{}}"#);
        let view = McpRequestView::parse(invalid.as_ref())?;
        assert_eq!(
            view.method().err(),
            Some(RequestViewError::InvalidFieldType {
                field: "method",
                expected: "string",
            })
        );
        assert_eq!(
            view.tool_call().err(),
            Some(RequestViewError::InvalidFieldType {
                field: "method",
                expected: "string",
            })
        );
        Ok(())
    }

    #[test]
    fn rejects_a_request_through_the_wrong_adapter() -> Result<(), Box<dyn std::error::Error>> {
        let request = body(r#"{"method":"prompts/get","params":{"name":"summary"}}"#);
        let view = McpRequestView::parse(request.as_ref())?;
        assert_eq!(
            view.tool_call().err(),
            Some(RequestViewError::UnexpectedMethod {
                expected: "tools/call",
                actual: "prompts/get".to_owned(),
            })
        );
        assert_eq!(
            view.resource_read().err(),
            Some(RequestViewError::UnexpectedMethod {
                expected: "resources/read",
                actual: "prompts/get".to_owned(),
            })
        );
        assert!(view.prompt_get().is_ok());
        Ok(())
    }

    #[test]
    #[expect(clippy::too_many_lines, reason = "field error matrix")]
    fn distinguishes_missing_and_wrong_field_types() -> Result<(), Box<dyn std::error::Error>> {
        for (request, expected) in [
            (
                r#"{"method":"tools/call","params":{"arguments":{}}}"#,
                RequestViewError::MissingField("name"),
            ),
            (
                r#"{"method":"tools/call","params":{"name":99}}"#,
                RequestViewError::InvalidFieldType {
                    field: "name",
                    expected: "string",
                },
            ),
            (
                r#"{"method":"tools/call","params":{"name":"tool","arguments":null}}"#,
                RequestViewError::InvalidFieldType {
                    field: "arguments",
                    expected: "object",
                },
            ),
        ] {
            let request = body(request);
            let view = McpRequestView::parse(request.as_ref())?;
            assert_eq!(view.tool_call().err(), Some(expected));
        }

        let missing_uri = body(r#"{"method":"resources/read","params":{}}"#);
        let view = McpRequestView::parse(missing_uri.as_ref())?;
        assert_eq!(
            view.resource_read().err(),
            Some(RequestViewError::MissingField("uri"))
        );
        let wrong_uri = body(r#"{"method":"resources/read","params":{"uri":false}}"#);
        let view = McpRequestView::parse(wrong_uri.as_ref())?;
        assert_eq!(
            view.resource_read().err(),
            Some(RequestViewError::InvalidFieldType {
                field: "uri",
                expected: "string",
            })
        );
        Ok(())
    }

    #[test]
    fn compatibility_parser_keeps_existing_lossy_behavior() {
        let malformed = body("not json");
        let params = JsonRpcParams::parse(&malformed);
        assert!(params.name.is_none());
        assert!(params.uri.is_none());
        assert!(params.arguments.is_empty());

        let complete =
            body(r#"{"params":{"name":"summary","uri":"file:///x","arguments":{"count":42}}}"#);
        let params = JsonRpcParams::parse(&complete);
        assert_eq!(params.name.as_deref(), Some("summary"));
        assert_eq!(params.uri.as_deref(), Some("file:///x"));
        assert_eq!(params.arguments.get("count"), Some(&serde_json::json!(42)));
    }
}
