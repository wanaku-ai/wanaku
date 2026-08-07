use http::Response;
use tracing::warn;
use wanaku_praxis_apis::http_response::json_err;

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
        .header("Access-Control-Allow-Origin", wanaku_praxis_apis::config::ENV.cors_origin.as_str())
        .body(body)
        .expect("valid json response")
}

pub(crate) fn handle_chat_list_llms() -> Response<Vec<u8>> {
    raw_json_response(serde_json::to_vec(&serde_json::json!(["Inference"])).unwrap_or_default())
}

pub(crate) async fn handle_chat_list_models(
    client: &reqwest::Client,
    base_url: &str,
    upstream_host: Option<&str>,
    api_key: &str,
) -> Response<Vec<u8>> {
    let url = format!("{base_url}/v1/models");

    let mut request = client.get(&url);
    if let Some(host) = upstream_host {
        request = request.header("Host", host);
    }
    if !api_key.is_empty() {
        request = request.bearer_auth(api_key);
    }

    let response = match request.send().await {
        Ok(r) => r,
        Err(e) => {
            warn!(error = %e, "failed to fetch models from inference backend");
            return json_err(502, &format!("failed to reach inference backend: {e}"));
        }
    };

    let status = response.status();
    let raw = match response.text().await {
        Ok(t) => t,
        Err(e) => {
            warn!(error = %e, status = %status, "failed to read inference backend models response");
            return json_err(502, &format!("failed to read inference backend response: {e}"));
        }
    };

    if !status.is_success() {
        warn!(status = %status, body = %raw, "inference backend returned error for models");
        return json_err(status.as_u16(), &format!("inference backend error: {raw}"));
    }

    let body: serde_json::Value = match serde_json::from_str(&raw) {
        Ok(b) => b,
        Err(e) => {
            warn!(error = %e, status = %status, body = %raw, "failed to parse inference backend models response");
            return json_err(502, &format!("invalid response from inference backend: {e}"));
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

pub(crate) async fn handle_chat_completions(
    client: &reqwest::Client,
    base_url: &str,
    upstream_host: Option<&str>,
    api_key: &str,
    body: &str,
) -> Response<Vec<u8>> {
    let request: serde_json::Value = match serde_json::from_str(body) {
        Ok(r) => r,
        Err(e) => return json_err(400, &format!("invalid request: {e}")),
    };

    let request_api_key = request
        .get("apiKey")
        .and_then(serde_json::Value::as_str)
        .unwrap_or("");
    let effective_key = if request_api_key.is_empty() {
        api_key
    } else {
        request_api_key
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

    let url = format!("{base_url}/v1/chat/completions");

    let mut req = client.post(&url).json(&openai_request);
    if let Some(host) = upstream_host {
        req = req.header("Host", host);
    }
    if !effective_key.is_empty() {
        req = req.bearer_auth(effective_key);
    }

    let response = match req.send().await {
        Ok(r) => r,
        Err(e) => {
            warn!(error = %e, "chat completions request to inference backend failed");
            return json_err(502, &format!("failed to reach inference backend: {e}"));
        }
    };

    let status = response.status();
    let raw = match response.text().await {
        Ok(t) => t,
        Err(e) => {
            warn!(error = %e, status = %status, "failed to read inference backend completions response");
            return json_err(502, &format!("failed to read inference backend response: {e}"));
        }
    };

    if !status.is_success() {
        warn!(status = %status, body = %raw, "inference backend returned error for completions");
        return json_err(status.as_u16(), &format!("inference backend error: {raw}"));
    }

    let resp_body: serde_json::Value = match serde_json::from_str(&raw) {
        Ok(b) => b,
        Err(e) => {
            warn!(error = %e, status = %status, body = %raw, "failed to parse inference backend completions response");
            return json_err(502, &format!("invalid response from inference backend: {e}"));
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
        .header("Access-Control-Allow-Origin", wanaku_praxis_apis::config::ENV.cors_origin.as_str())
        .body(response_body)
        .unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn list_llms() {
        assert_eq!(
            resolve_chat_route("GET", "/api/v1/chat/llms"),
            ChatRoute::ListLlms
        );
    }

    #[test]
    fn list_models_for_llm() {
        assert_eq!(
            resolve_chat_route("GET", "/api/v1/chat/ollama/models"),
            ChatRoute::ListModels("ollama".to_owned())
        );
    }

    #[test]
    fn completions() {
        assert_eq!(
            resolve_chat_route("POST", "/api/v1/chat/completions"),
            ChatRoute::Completions
        );
    }

    #[test]
    fn wrong_method_for_llms() {
        assert_eq!(
            resolve_chat_route("POST", "/api/v1/chat/llms"),
            ChatRoute::NotFound
        );
    }

    #[test]
    fn wrong_method_for_completions() {
        assert_eq!(
            resolve_chat_route("GET", "/api/v1/chat/completions"),
            ChatRoute::NotFound
        );
    }

    #[test]
    fn wrong_prefix() {
        assert_eq!(
            resolve_chat_route("GET", "/api/v1/other/llms"),
            ChatRoute::NotFound
        );
    }

    #[test]
    fn models_with_empty_llm_name() {
        assert_eq!(
            resolve_chat_route("GET", "/api/v1/chat//models"),
            ChatRoute::NotFound
        );
    }

    #[test]
    fn bare_models_suffix() {
        assert_eq!(
            resolve_chat_route("GET", "/api/v1/chat/models"),
            ChatRoute::NotFound
        );
    }

    #[test]
    fn empty_suffix() {
        assert_eq!(
            resolve_chat_route("GET", "/api/v1/chat"),
            ChatRoute::NotFound
        );
    }
}
