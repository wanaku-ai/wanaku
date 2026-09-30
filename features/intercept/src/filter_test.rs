use super::*;

#[test]
fn parse_body_preserves_json_and_uses_lossy_text_for_invalid_json() {
    assert_eq!(
        parse_body(br#"{"model":"test"}"#),
        serde_json::json!({"model": "test"})
    );
    assert_eq!(
        parse_body(&[b'a', 0xff]),
        serde_json::Value::String("a�".to_owned())
    );
}

#[test]
fn inject_system_prompt_prepends_tracking_instruction() -> Result<(), serde_json::Error> {
    let body = br#"{"messages":[{"role":"user","content":"hello"}]}"#;
    let (enriched, conversation_id) = inject_system_prompt(body, "wk-generated");

    assert_eq!(conversation_id, "wk-generated");
    let parsed: serde_json::Value =
        serde_json::from_slice(enriched.as_deref().unwrap_or_default())?;
    let messages = parsed
        .get("messages")
        .and_then(serde_json::Value::as_array)
        .cloned()
        .unwrap_or_default();
    assert_eq!(messages.len(), 2);
    assert_eq!(messages[0].get("role"), Some(&serde_json::json!("system")));
    assert!(
        messages[0]
            .get("content")
            .and_then(serde_json::Value::as_str)
            .is_some_and(|content| {
                content.contains("wk-generated") && content.contains(REQUEST_ID_ARG)
            })
    );
    Ok(())
}

#[test]
fn existing_tracking_id_prevents_duplicate_instruction() {
    let body = br#"{"messages":[{"role":"system","content":"Use wk-existing for calls."},{"role":"user","content":"hello"}]}"#;
    let (enriched, conversation_id) = inject_system_prompt(body, "wk-generated");

    assert!(enriched.is_none());
    assert_eq!(conversation_id, "wk-existing");
}

#[test]
fn invalid_chat_body_is_not_modified() {
    for body in [br#"{"model":"test"}"#.as_slice(), b"not-json".as_slice()] {
        let (enriched, conversation_id) = inject_system_prompt(body, "wk-generated");
        assert!(enriched.is_none());
        assert_eq!(conversation_id, "wk-generated");
    }
}

// #1964: captured bodies must be redacted before they reach the interaction
// store, while conversation content survives for intent analysis.
fn redacted_sample_bodies() -> (serde_json::Value, serde_json::Value) {
    let filter = InterceptFilter::new(InterceptConfig::default());
    let mut request_body = serde_json::json!({
        "model": "llama3.2",
        "authorization": "Bearer wanaku-sentinel-auth-1964",
        "messages": [
            {"role": "system", "content": "Use wk-abc for calls."},
            {"role": "user", "content": "give me a basic example and be the bearer of good news"},
            {"role": "assistant", "content": "here is a basic outline"}
        ],
        "metadata": {"api_key": "wanaku-sentinel-key-1964", "note": "ghp_wanakuSentinelToken1964"}
    });
    let mut response_body = serde_json::json!({
        "id": "chatcmpl-1",
        "model": "llama3.2",
        "choices": [{"message": {"role": "assistant", "content": "here is your basic summary"}}]
    });
    filter.redact_bodies(&mut request_body, &mut response_body);
    (request_body, response_body)
}

#[test]
fn redact_bodies_redacts_sensitive_fields_and_token_shapes() {
    let (request_body, response_body) = redacted_sample_bodies();

    // Sensitive field names are redacted regardless of value shape.
    assert_eq!(request_body["authorization"], "[REDACTED]");
    assert_eq!(request_body["metadata"]["api_key"], "[REDACTED]");
    // A credential token shape (ghp_) in an ordinary value is redacted.
    assert_eq!(request_body["metadata"]["note"], "[REDACTED]");

    // No field-based or token-shaped sentinel survives in the retained bodies.
    for sentinel in [
        "wanaku-sentinel-auth-1964",
        "wanaku-sentinel-key-1964",
        "ghp_wanakuSentinelToken1964",
    ] {
        assert!(
            !serde_json::to_string(&request_body)
                .unwrap()
                .contains(sentinel),
            "request body must not contain {sentinel}"
        );
        assert!(
            !serde_json::to_string(&response_body)
                .unwrap()
                .contains(sentinel)
        );
    }
}

#[test]
fn redact_bodies_preserves_conversation_content() {
    let (request_body, response_body) = redacted_sample_bodies();

    // Conversation context survives so intent analysis keeps working, even when a
    // message contains the common words "basic" or "bearer" (#1964 regression).
    assert_eq!(request_body["messages"][0]["role"], "system");
    assert_eq!(
        request_body["messages"][1]["content"],
        "give me a basic example and be the bearer of good news"
    );
    assert_eq!(
        request_body["messages"][2]["content"],
        "here is a basic outline"
    );
    assert_eq!(
        response_body["choices"][0]["message"]["content"],
        "here is your basic summary"
    );
    assert_eq!(response_body["id"], "chatcmpl-1");
}

#[test]
fn redact_bodies_applies_configured_rules() {
    let cfg = InterceptConfig {
        sensitive_json_pointers: vec!["/context/private".to_owned()],
        credential_markers: vec!["wanaku-marker ".to_owned()],
        token_prefixes: vec!["sentinelpfx_".to_owned()],
        ..InterceptConfig::default()
    };
    let filter = InterceptFilter::new(cfg);

    let mut request_body = serde_json::json!({
        "context": {"private": "wanaku-sentinel-pointer-1964", "public": "keep me"},
        "note": "prefixed sentinelpfx_wanaku-sentinel-prefix-1964",
        "marker": "wanaku-marker wanaku-sentinel-marker-1964"
    });
    let mut response_body = serde_json::Value::Null;

    filter.redact_bodies(&mut request_body, &mut response_body);

    assert_eq!(request_body["context"]["private"], "[REDACTED]");
    assert_eq!(request_body["context"]["public"], "keep me");
    assert_eq!(request_body["note"], "[REDACTED]");
    assert_eq!(request_body["marker"], "[REDACTED]");
}

#[test]
fn redact_bodies_redacts_shaped_token_in_non_json_string_body() {
    // #1964: a non-JSON body (for example a streaming response) is captured as a
    // single string. Only the value-shape rules apply. A shaped token collapses
    // the whole string; ordinary prose survives, even with the word "basic".
    let filter = InterceptFilter::new(InterceptConfig::default());
    let mut request_body =
        serde_json::Value::String("event: token ghp_wanakuSentinelToken1964".to_owned());
    let mut response_body = serde_json::Value::String("data: here is a basic answer".to_owned());

    filter.redact_bodies(&mut request_body, &mut response_body);

    assert_eq!(request_body, "[REDACTED]");
    assert_eq!(response_body, "data: here is a basic answer");
}

#[test]
fn redact_bodies_preserves_opaque_bearer_token_in_content() {
    // #1964 documented tradeoff: the content variant does not apply the bearer or
    // basic auth-scheme markers, so an opaque bearer token pasted into a message
    // survives for intent analysis. A sensitive field or a shaped token is still
    // redacted (see the other tests). Operators close this residual exposure per
    // deployment with a sensitive_json_pointer. See docs/redaction.md.
    let filter = InterceptFilter::new(InterceptConfig::default());
    let mut request_body = serde_json::json!({
        "messages": [
            {"role": "user", "content": "my header value is Bearer aGVsbG8xMjM0abcd"}
        ]
    });
    let mut response_body = serde_json::Value::Null;

    filter.redact_bodies(&mut request_body, &mut response_body);

    assert_eq!(
        request_body["messages"][0]["content"],
        "my header value is Bearer aGVsbG8xMjM0abcd"
    );
}

#[test]
fn redact_bodies_capture_disabled_drops_bodies() {
    let cfg = InterceptConfig {
        capture_payloads: false,
        ..InterceptConfig::default()
    };
    let filter = InterceptFilter::new(cfg);

    let mut request_body = serde_json::json!({"messages": [{"role": "user", "content": "hi"}]});
    let mut response_body = serde_json::json!({"choices": []});

    filter.redact_bodies(&mut request_body, &mut response_body);

    assert!(request_body.is_null());
    assert!(response_body.is_null());
}
