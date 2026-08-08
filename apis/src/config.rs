//! Centralized environment variable configuration for Wanaku Praxis.
//!
//! All `WANAKU_*` environment variables are read once at startup via
//! [`LazyLock`] and exposed through the [`ENV`] static. No other module
//! should call `std::env::var` for these variables directly.
//!
//! Feature-specific env vars (e.g. `WANAKU_SAFETY_*`) are owned by their
//! respective feature crates, not this module.

use std::path::PathBuf;
use std::sync::LazyLock;

/// Management API listen address (default `0.0.0.0:8080`).
const WANAKU_MGMT_LISTEN: &str = "WANAKU_MGMT_LISTEN";

/// Inference backend address used in the default Praxis config (default `127.0.0.1:11434`).
const WANAKU_INFERENCE_UPSTREAM: &str = "WANAKU_INFERENCE_UPSTREAM";

/// Bearer token API key for the inference upstream. Empty means no auth.
const WANAKU_INFERENCE_API_KEY: &str = "WANAKU_INFERENCE_API_KEY";

/// Persistence backend selector. Set to `"file"` to enable file-based persistence.
/// Unset or any other value disables persistence.
const WANAKU_PERSIST_BACKEND: &str = "WANAKU_PERSIST_BACKEND";

/// Directory where `registry.json` is stored (default `/data/registry`).
/// Only used when [`WANAKU_PERSIST_BACKEND`] is `"file"`.
const WANAKU_PERSIST_PATH: &str = "WANAKU_PERSIST_PATH";

/// Base URL for the Classic proxy backend. Unset disables proxying.
const WANAKU_CLASSIC_URL: &str = "WANAKU_CLASSIC_URL";

/// Filesystem path to serve the admin UI from instead of the embedded assets.
/// Unset uses the compiled-in [`rust_embed`] bundle.
const WANAKU_UI_PATH: &str = "WANAKU_UI_PATH";

/// Value for the `Access-Control-Allow-Origin` header on management API responses.
/// Defaults to `"*"`. Set to a specific origin (e.g. `http://localhost:3000`) in production.
const WANAKU_CORS_ORIGIN: &str = "WANAKU_CORS_ORIGIN";

/// File-persistence settings, present only when enabled.
#[derive(Debug, Clone)]
pub struct PersistEnv {
    /// Directory containing `registry.json`.
    pub dir: PathBuf,
}

/// Typed snapshot of all `WANAKU_*` environment variables.
#[derive(Debug, Clone)]
pub struct WanakuEnv {
    /// Management API listen address.
    pub mgmt_listen: String,
    /// Inference upstream `host:port` for the Praxis proxy load-balancer endpoint.
    pub inference_upstream: String,
    /// Path prefix extracted from the upstream URL (e.g. `/api` from `https://host/api`).
    /// Empty when the upstream is a bare `host:port`.
    pub inference_path_prefix: String,
    /// SNI hostname for TLS upstream connections. `None` means plain TCP.
    pub inference_tls_sni: Option<String>,
    /// Bearer token API key for the inference upstream. Empty means no auth.
    pub inference_api_key: String,
    /// File-persistence config. `None` when persistence is disabled.
    pub persist: Option<PersistEnv>,
    /// Classic proxy base URL. `None` when proxying is disabled.
    pub classic_url: Option<String>,
    /// Override path for serving the admin UI from the filesystem.
    pub ui_path: Option<PathBuf>,
    /// Value for the `Access-Control-Allow-Origin` header on management API responses.
    pub cors_origin: String,
}

/// Global configuration, initialized lazily on first access.
pub static ENV: LazyLock<WanakuEnv> = LazyLock::new(WanakuEnv::from_env);

impl WanakuEnv {
    #[must_use]
    pub fn inference_proxy_port(&self) -> u16 {
        8083
    }

    fn from_env() -> Self {
        let persist = std::env::var(WANAKU_PERSIST_BACKEND)
            .ok()
            .filter(|b| b == "file")
            .map(|_| {
                let dir = std::env::var(WANAKU_PERSIST_PATH)
                    .unwrap_or_else(|_| "/data/registry".to_owned());
                PersistEnv {
                    dir: PathBuf::from(dir),
                }
            });

        let parsed = parse_upstream(
            &std::env::var(WANAKU_INFERENCE_UPSTREAM)
                .unwrap_or_else(|_| "127.0.0.1:11434".to_owned()),
        );

        Self {
            mgmt_listen: std::env::var(WANAKU_MGMT_LISTEN)
                .unwrap_or_else(|_| "0.0.0.0:8080".to_owned()),
            inference_upstream: parsed.host_port,
            inference_path_prefix: parsed.path_prefix,
            inference_tls_sni: parsed.tls_sni,
            inference_api_key: std::env::var(WANAKU_INFERENCE_API_KEY)
                .unwrap_or_default(),
            persist,
            classic_url: std::env::var(WANAKU_CLASSIC_URL)
                .ok()
                .map(|u| u.trim_end_matches('/').to_owned()),
            ui_path: std::env::var(WANAKU_UI_PATH).ok().map(PathBuf::from),
            cors_origin: std::env::var(WANAKU_CORS_ORIGIN)
                .unwrap_or_else(|_| "*".to_owned()),
        }
    }
}

struct ParsedUpstream {
    host_port: String,
    path_prefix: String,
    tls_sni: Option<String>,
}

/// Accepts `host:port`, `http://host/path`, or `https://host:port/path`.
fn parse_upstream(raw: &str) -> ParsedUpstream {
    let (host_and_rest, default_port, is_tls) =
        if let Some(rest) = raw.strip_prefix("https://") {
            (rest, "443", true)
        } else if let Some(rest) = raw.strip_prefix("http://") {
            (rest, "80", false)
        } else {
            return ParsedUpstream {
                host_port: raw.to_owned(),
                path_prefix: String::new(),
                tls_sni: None,
            };
        };

    let (authority, path) = match host_and_rest.find('/') {
        Some(i) => (&host_and_rest[..i], host_and_rest[i..].trim_end_matches('/')),
        None => (host_and_rest, ""),
    };

    let hostname = authority.split(':').next().unwrap_or(authority);
    let host_port = if authority.contains(':') {
        authority.to_owned()
    } else {
        format!("{authority}:{default_port}")
    };

    ParsedUpstream {
        host_port,
        path_prefix: path.to_owned(),
        tls_sni: if is_tls { Some(hostname.to_owned()) } else { None },
    }
}

#[cfg(test)]
mod tests {
    use super::parse_upstream;

    #[test]
    fn bare_host_port_unchanged() {
        let p = parse_upstream("127.0.0.1:11434");
        assert_eq!(p.host_port, "127.0.0.1:11434");
        assert_eq!(p.path_prefix, "");
        assert!(p.tls_sni.is_none());
    }

    #[test]
    fn https_with_path() {
        let p = parse_upstream("https://openrouter.ai/api");
        assert_eq!(p.host_port, "openrouter.ai:443");
        assert_eq!(p.path_prefix, "/api");
        assert_eq!(p.tls_sni.as_deref(), Some("openrouter.ai"));
    }

    #[test]
    fn https_with_deep_path() {
        let p = parse_upstream("https://host.com/some/long/path");
        assert_eq!(p.host_port, "host.com:443");
        assert_eq!(p.path_prefix, "/some/long/path");
        assert_eq!(p.tls_sni.as_deref(), Some("host.com"));
    }

    #[test]
    fn https_with_port_and_path() {
        let p = parse_upstream("https://host.com:8443/v1");
        assert_eq!(p.host_port, "host.com:8443");
        assert_eq!(p.path_prefix, "/v1");
        assert_eq!(p.tls_sni.as_deref(), Some("host.com"));
    }

    #[test]
    fn http_plain_no_tls() {
        let p = parse_upstream("http://localhost:11434");
        assert_eq!(p.host_port, "localhost:11434");
        assert_eq!(p.path_prefix, "");
        assert!(p.tls_sni.is_none());
    }

    #[test]
    fn http_no_port() {
        let p = parse_upstream("http://example.com");
        assert_eq!(p.host_port, "example.com:80");
        assert!(p.tls_sni.is_none());
    }

    #[test]
    fn trailing_slash_stripped() {
        let p = parse_upstream("https://host.com/api/");
        assert_eq!(p.host_port, "host.com:443");
        assert_eq!(p.path_prefix, "/api");
    }
}
