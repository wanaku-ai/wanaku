use http::Response;
use tracing::warn;

#[derive(Debug, PartialEq, Eq)]
pub(crate) enum ChatRoute {
    ListLlms,
    ListModels(String),
    Completions,
    NotFound,
}

pub(crate) fn resolve_chat_route(method: &str, path: &str) -> ChatRoute {
    let suffix = match path.strip_prefix("/api/v1/chat") {
        Some(s) => s,
        None => return ChatRoute::NotFound,
    };

    match (method, suffix) {
        ("GET", "/llms") => ChatRoute::ListLlms,
        ("GET", s) if s.ends_with("/models") => {
            let llm = s.strip_prefix('/').and_then(|s| s.strip_suffix("/models"));
            match llm {
                Some(name) if !name.is_empty() => ChatRoute::ListModels(name.to_owned()),
                _ => ChatRoute::NotFound,
            }
        }
        ("POST", "/completions") => ChatRoute::Completions,
        _ => ChatRoute::NotFound,
    }
}

#[expect(clippy::expect_used, reason = "valid static json response")]
fn raw_json_response(body: Vec<u8>) -> Response<Vec<u8>> {
    Response::builder()
        .status(200)
        .header("Content-Type", "application/json")
        .header("Content-Length", body.len())
        .body(body)
        .expect("valid json response")
}

#[expect(clippy::expect_used, reason = "valid static json response")]
fn json_err(status: u16, message: &str) -> Response<Vec<u8>> {
    let wrapper = serde_json::json!({"data": null, "error": message});
    let body = serde_json::to_vec(&wrapper).unwrap_or_default();
    Response::builder()
        .status(status)
        .header("Content-Type", "application/json")
        .header("Content-Length", body.len())
        .body(body)
        .expect("valid json error response")
}

pub(crate) fn handle_chat_list_llms() -> Response<Vec<u8>> {
    raw_json_response(serde_json::to_vec(&serde_json::json!(["Ollama"])).unwrap_or_default())
}

pub(crate) async fn handle_chat_list_models(ollama_proxy: &str) -> Response<Vec<u8>> {
    let url = format!("{ollama_proxy}/v1/models");
    let client = reqwest::Client::new();

    let response = match client.get(&url).send().await {
        Ok(r) => r,
        Err(e) => {
            warn!(error = %e, "failed to fetch models from Ollama proxy");
            return json_err(502, &format!("failed to reach Ollama: {e}"));
        }
    };

    let body: serde_json::Value = match response.json().await {
        Ok(b) => b,
        Err(e) => {
            warn!(error = %e, "failed to parse Ollama models response");
            return json_err(502, &format!("invalid response from Ollama: {e}"));
        }
    };

    let models: Vec<String> = body
        .get("data")
        .and_then(|d| d.as_array())
        .map(|arr| {
            arr.iter()
                .filter_map(|m| m.get("id").and_then(serde_json::Value::as_str))
                .map(String::from)
                .collect()
        })
        .unwrap_or_default();

    raw_json_response(serde_json::to_vec(&serde_json::json!(models)).unwrap_or_default())
}

pub(crate) async fn handle_chat_completions(ollama_proxy: &str, body: &str) -> Response<Vec<u8>> {
    let request: serde_json::Value = match serde_json::from_str(body) {
        Ok(r) => r,
        Err(e) => return json_err(400, &format!("invalid request: {e}")),
    };

    let model = request
        .get("model")
        .and_then(serde_json::Value::as_str)
        .unwrap_or("");
    let system_prompt = request
        .get("systemPrompt")
        .and_then(serde_json::Value::as_str)
        .unwrap_or("");
    let user_prompt = request
        .get("userPrompt")
        .and_then(serde_json::Value::as_str)
        .unwrap_or("");

    let mut messages = Vec::new();

    if !system_prompt.is_empty() {
        messages.push(serde_json::json!({"role": "system", "content": system_prompt}));
    }

    if let Some(history) = request.get("chatHistory").and_then(|h| h.as_array()) {
        for msg in history {
            messages.push(msg.clone());
        }
    }

    messages.push(serde_json::json!({"role": "user", "content": user_prompt}));

    let openai_request = serde_json::json!({
        "model": model,
        "messages": messages,
    });

    let url = format!("{ollama_proxy}/v1/chat/completions");
    let client = reqwest::Client::new();

    let response = match client.post(&url).json(&openai_request).send().await {
        Ok(r) => r,
        Err(e) => {
            warn!(error = %e, "chat completions request to Ollama proxy failed");
            return json_err(502, &format!("failed to reach Ollama: {e}"));
        }
    };

    let resp_body: serde_json::Value = match response.json().await {
        Ok(b) => b,
        Err(e) => {
            warn!(error = %e, "failed to parse Ollama completions response");
            return json_err(502, &format!("invalid response from Ollama: {e}"));
        }
    };

    let content = resp_body
        .get("choices")
        .and_then(|c| c.get(0))
        .and_then(|c| c.get("message"))
        .and_then(|m| m.get("content"))
        .and_then(serde_json::Value::as_str)
        .unwrap_or("");

    let response_body = content.as_bytes().to_vec();
    Response::builder()
        .status(200)
        .header("Content-Type", "text/plain")
        .header("Content-Length", response_body.len())
        .body(response_body)
        .unwrap_or_default()
}
