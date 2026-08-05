#![deny(unsafe_code)]

use http::Response;
use reqwest::Client;

const PROXIED_PREFIXES: &[&str] = &[
    "/api/v1/service-catalog",
    "/api/v1/service-template",
    "/api/v1/data-store",
    "/api/v1/chat",
    "/api/v1/management/info",
    "/api/v1/toolset-repos",
    "/api/v2/code-execution",
    "/api/v2/tool-calls",
];

pub struct ClassicProxy {
    base_url: String,
    client: Client,
}

impl ClassicProxy {
    pub fn from_config() -> Option<Self> {
        let base_url = wanaku_praxis_apis::config::ENV.classic_url.as_ref()?;
        Some(Self {
            base_url: base_url.clone(),
            client: Client::new(),
        })
    }

    pub fn should_proxy(path: &str) -> bool {
        PROXIED_PREFIXES.iter().any(|prefix| path.starts_with(prefix))
    }

    pub async fn forward(
        &self,
        method: &str,
        path: &str,
        body: Option<String>,
    ) -> Response<Vec<u8>> {
        let url = format!("{}{path}", self.base_url);

        let req_method = match method.parse::<reqwest::Method>() {
            Ok(m) => m,
            Err(_) => return json_err(400, &format!("unsupported method: {method}")),
        };

        let mut request = self.client.request(req_method, &url);

        if let Some(b) = body {
            request = request
                .header("Content-Type", "application/json")
                .body(b);
        }

        let response = match request.send().await {
            Ok(r) => r,
            Err(e) => {
                tracing::warn!(url = %url, error = %e, "proxy request to Classic failed");
                return json_err(502, &format!("upstream error: {e}"));
            }
        };

        let status = response.status().as_u16();
        let response_body = match response.bytes().await {
            Ok(b) => b.to_vec(),
            Err(e) => {
                tracing::warn!(error = %e, "failed to read proxy response body");
                return json_err(502, &format!("upstream body read error: {e}"));
            }
        };

        build_response(status, response_body)
    }
}

fn json_err(status: u16, message: &str) -> Response<Vec<u8>> {
    crate::http_response::json_err(status, message)
}

#[expect(clippy::expect_used, reason = "valid static response")]
fn build_response(status: u16, body: Vec<u8>) -> Response<Vec<u8>> {
    Response::builder()
        .status(status)
        .header("Content-Type", "application/json")
        .header("Content-Length", body.len())
        .body(body)
        .expect("valid proxy response")
}
