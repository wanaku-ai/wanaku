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
