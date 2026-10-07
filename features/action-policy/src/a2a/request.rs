//! Version-specific validation without protocol translation.
use serde_json::Value;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(super) enum Version {
    Legacy,
    V1,
}
impl Version {
    pub(super) fn from_headers(headers: &http::HeaderMap) -> Result<Self, (i32, &'static str)> {
        match headers.get("a2a-version").map(http::HeaderValue::to_str) {
            None | Some(Ok("" | "0.3" | "0.3.0")) => Ok(Self::Legacy),
            Some(Ok("1.0")) => Ok(Self::V1),
            _ => Err((-32009, "A2A version is not supported.")),
        }
    }
    pub(super) const fn as_str(self) -> &'static str {
        match self {
            Self::Legacy => "0.3",
            Self::V1 => "1.0",
        }
    }
}

fn validate_v1_method(request: &Value, method: &str) -> Result<(), (i32, &'static str)> {
    match method {
        "SendStreamingMessage" | "SubscribeToTask" => Err((-32004, "Streaming is not supported.")),
        "CreateTaskPushNotificationConfig"
        | "GetTaskPushNotificationConfig"
        | "ListTaskPushNotificationConfigs"
        | "DeleteTaskPushNotificationConfig" => {
            Err((-32003, "Push notifications are not supported."))
        }
        _ if request.get("method").and_then(Value::as_str) != Some(method) => {
            Err((-32601, "A2A 1.0 requires canonical JSON-RPC method names."))
        }
        _ => Ok(()),
    }
}

fn valid_role(role: Option<&Value>, version: Version) -> bool {
    match version {
        Version::Legacy => matches!(role.and_then(Value::as_str), Some("user" | "agent")),
        Version::V1 => {
            matches!(
                role.and_then(Value::as_str),
                Some("ROLE_USER" | "ROLE_AGENT")
            ) || matches!(role.and_then(Value::as_u64), Some(1 | 2))
        }
    }
}

fn valid_part(part: &Value, version: Version) -> bool {
    let Some(part) = part.as_object() else {
        return false;
    };
    if version == Version::Legacy {
        return true;
    }
    let contents: Vec<_> = ["text", "raw", "url", "data"]
        .into_iter()
        .filter(|key| part.contains_key(*key))
        .collect();
    match contents.as_slice() {
        ["data"] => true,
        [key] => part.get(*key).is_some_and(Value::is_string),
        _ => false,
    }
}

pub(super) fn validate_request(
    request: &Value,
    method: &str,
    version: Version,
) -> Result<(), (i32, &'static str)> {
    if request.get("jsonrpc").and_then(Value::as_str) != Some("2.0")
        || !request
            .get("id")
            .is_some_and(|id| id.is_string() || id.is_number())
    {
        return Err((-32600, "A2A requires a JSON-RPC 2.0 request with an ID."));
    }
    if version == Version::V1 {
        validate_v1_method(request, method)?;
    }
    if !super::is_supported_method(method) {
        return Err((
            -32601,
            "A2A method is not supported; streaming and push are disabled.",
        ));
    }
    let params = request
        .get("params")
        .and_then(Value::as_object)
        .ok_or((-32602, "A2A params must be an object."))?;
    validate_configuration(params, version)?;
    match method {
        "GetTask" | "CancelTask"
            if !params
                .get("id")
                .and_then(Value::as_str)
                .is_some_and(|id| !id.is_empty()) =>
        {
            Err((-32602, "A2A task ID is required."))
        }
        "SendMessage" => validate_message(params.get("message"), version),
        _ => Ok(()),
    }
}

fn validate_configuration(
    params: &serde_json::Map<String, Value>,
    version: Version,
) -> Result<(), (i32, &'static str)> {
    let streaming = |options: &serde_json::Map<String, Value>| {
        ["stream", "streaming"].iter().any(|key| {
            options
                .get(*key)
                .is_some_and(|value| value != &Value::Bool(false))
        })
    };
    if version == Version::V1
        && params
            .get("configuration")
            .and_then(Value::as_object)
            .is_some_and(|options| {
                options.contains_key("taskPushNotificationConfig")
                    || options.contains_key("task_push_notification_config")
                    || options.contains_key("pushNotificationConfig")
            })
    {
        return Err((-32003, "Push notifications are not supported."));
    }
    if version == Version::V1
        && params
            .get("configuration")
            .is_some_and(|configuration| !configuration.is_object())
    {
        return Err((-32602, "A2A configuration must be an object."));
    }
    if streaming(params)
        || params.get("configuration").is_some_and(|configuration| {
            configuration.as_object().is_none_or(|options| {
                streaming(options) || options.contains_key("pushNotificationConfig")
            })
        })
    {
        return Err((
            if version == Version::V1 {
                -32004
            } else {
                -32602
            },
            "Streaming and push notifications are not supported.",
        ));
    }
    Ok(())
}

fn validate_message(message: Option<&Value>, version: Version) -> Result<(), (i32, &'static str)> {
    let Some(message) = message.and_then(Value::as_object) else {
        return Err((-32602, "A2A message must be an object."));
    };
    if !message
        .get("messageId")
        .and_then(Value::as_str)
        .is_some_and(|id| !id.is_empty())
        || !valid_role(message.get("role"), version)
        || !message
            .get("parts")
            .and_then(Value::as_array)
            .is_some_and(|parts| {
                !parts.is_empty() && parts.iter().all(|part| valid_part(part, version))
            })
    {
        return Err((-32602, "A2A message requires messageId, role and parts."));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn message(role: &Value, part: &Value) -> Value {
        serde_json::json!({"jsonrpc":"2.0", "id":"request", "method":"SendMessage", "params":{
            "message":{"messageId":"message", "role":role, "parts":[part]}
        }})
    }

    #[test]
    fn version_header_selection_is_explicit_and_defaults_to_legacy() {
        let mut headers = http::HeaderMap::new();
        assert_eq!(Version::from_headers(&headers), Ok(Version::Legacy));
        for version in ["", "0.3", "0.3.0", "1.0", "1.0.0", "2.0", "invalid"] {
            headers.insert(
                "a2a-version",
                http::HeaderValue::from_str(version).expect("header"),
            );
            match version {
                "" | "0.3" | "0.3.0" => {
                    assert_eq!(Version::from_headers(&headers), Ok(Version::Legacy));
                }
                "1.0" => assert_eq!(Version::from_headers(&headers), Ok(Version::V1)),
                _ => assert_eq!(
                    Version::from_headers(&headers).expect_err("unsupported").0,
                    -32009
                ),
            }
        }
    }

    #[test]
    fn v1_supports_protojson_roles_and_content_oneof() {
        for role in [
            serde_json::json!("ROLE_USER"),
            serde_json::json!("ROLE_AGENT"),
            serde_json::json!(1),
            serde_json::json!(2),
        ] {
            for part in [
                serde_json::json!({"text":"hello"}),
                serde_json::json!({"raw":"aGVsbG8=", "mediaType":"text/plain"}),
                serde_json::json!({"url":"https://files.example/document"}),
                serde_json::json!({"data":{"custom":"value"}}),
                serde_json::json!({"data":null}),
            ] {
                assert!(
                    validate_request(&message(&role, &part), "SendMessage", Version::V1).is_ok()
                );
            }
        }
        for role in [
            serde_json::json!("user"),
            serde_json::json!("ROLE_UNSPECIFIED"),
            serde_json::json!(0),
            Value::Null,
        ] {
            assert!(
                validate_request(
                    &message(&role, &serde_json::json!({"text":"hello"})),
                    "SendMessage",
                    Version::V1
                )
                .is_err()
            );
        }
        for part in [
            serde_json::json!({}),
            serde_json::json!({"text":7}),
            serde_json::json!({"text":"hello", "data":{}}),
            Value::Null,
        ] {
            assert!(
                validate_request(
                    &message(&serde_json::json!("ROLE_USER"), &part),
                    "SendMessage",
                    Version::V1
                )
                .is_err()
            );
        }
    }

    #[test]
    fn v1_rejects_unsupported_operations_and_hidden_push_before_dispatch() {
        for (method, code) in [
            ("SendStreamingMessage", -32004),
            ("SubscribeToTask", -32004),
            ("CreateTaskPushNotificationConfig", -32003),
            ("GetTaskPushNotificationConfig", -32003),
            ("ListTaskPushNotificationConfigs", -32003),
            ("DeleteTaskPushNotificationConfig", -32003),
            ("unknown", -32601),
        ] {
            let request =
                serde_json::json!({"jsonrpc":"2.0", "id":1, "method":method, "params":{}});
            assert_eq!(
                validate_request(&request, method, Version::V1)
                    .expect_err("unsupported")
                    .0,
                code
            );
        }
        for field in [
            "taskPushNotificationConfig",
            "task_push_notification_config",
            "pushNotificationConfig",
        ] {
            let mut request = message(
                &serde_json::json!("ROLE_USER"),
                &serde_json::json!({"text":"hello"}),
            );
            request["params"]["configuration"] =
                serde_json::json!({field: {"url":"http://callback"}});
            assert_eq!(
                validate_request(&request, "SendMessage", Version::V1)
                    .expect_err("push")
                    .0,
                -32003
            );
        }
        let mut alias = message(
            &serde_json::json!("ROLE_USER"),
            &serde_json::json!({"text":"hello"}),
        );
        alias["method"] = serde_json::json!("message/send");
        assert_eq!(
            validate_request(&alias, "SendMessage", Version::V1)
                .expect_err("legacy alias")
                .0,
            -32601
        );
    }

    #[test]
    fn v1_task_operations_require_ids_and_do_not_accept_names() {
        for method in ["GetTask", "CancelTask"] {
            let mut request = serde_json::json!({"jsonrpc":"2.0", "id":1, "method":method, "params":{"id":"task"}});
            assert!(validate_request(&request, method, Version::V1).is_ok());
            request["params"] = serde_json::json!({"name":"task"});
            assert_eq!(
                validate_request(&request, method, Version::V1)
                    .expect_err("missing id")
                    .0,
                -32602
            );
        }
    }
}
