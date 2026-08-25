//! Centralized environment variable configuration for Wanaku.
//!
//! All `WANAKU_*` environment variables are read once at startup via
//! [`LazyLock`] and exposed through the [`ENV`] static. No other module
//! should call `std::env::var` for these variables directly.
//!
//! Feature-specific env vars are owned by their respective feature crates,
//! not this module.

use std::path::PathBuf;
use std::sync::LazyLock;

/// Management API listen address (default `0.0.0.0:8080`).
const WANAKU_MGMT_LISTEN: &str = "WANAKU_MGMT_LISTEN";

/// Inference backend address (default `127.0.0.1:11434`).
const WANAKU_INFERENCE_UPSTREAM: &str = "WANAKU_INFERENCE_UPSTREAM";

/// Persistence backend selector. Defaults to `"file"` (file-based persistence).
/// Set to `"none"` to disable persistence entirely.
const WANAKU_PERSIST_BACKEND: &str = "WANAKU_PERSIST_BACKEND";

/// Directory where `registry.json` is stored (default `$HOME/.wanaku/server`).
/// Only used when [`WANAKU_PERSIST_BACKEND`] is `"file"`.
const WANAKU_PERSIST_PATH: &str = "WANAKU_PERSIST_PATH";

/// Filesystem path to serve the admin UI from instead of the embedded assets.
/// Unset uses the compiled-in [`rust_embed`] bundle.
const WANAKU_UI_PATH: &str = "WANAKU_UI_PATH";

/// Value for the `Access-Control-Allow-Origin` header on all HTTP responses
/// (management API, MCP endpoint, and CORS preflight).
/// Defaults to `"*"`. Set to a specific origin (e.g. `http://localhost:3000`) in production.
const WANAKU_CORS_ORIGIN: &str = "WANAKU_CORS_ORIGIN";

/// Comma-separated list of HTTP header names to forward from incoming MCP
/// requests to downstream tool invocations (e.g. `Authorization,DPoP`).
/// Empty by default — no headers are forwarded unless explicitly configured.
/// Per-tool overrides are configured via the `wanaku.forward_headers` label
/// on individual `ToolEntry` records.
const WANAKU_FORWARD_HEADERS: &str = "WANAKU_FORWARD_HEADERS";

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
    /// Inference upstream `host:port` for the proxy load-balancer endpoint.
    pub inference_upstream: String,
    /// SNI hostname for TLS upstream connections. `None` means plain TCP.
    pub inference_tls_sni: Option<String>,
    /// File-persistence config. `None` when persistence is disabled.
    pub persist: Option<PersistEnv>,
    /// Override path for serving the admin UI from the filesystem.
    pub ui_path: Option<PathBuf>,
    /// Value for the `Access-Control-Allow-Origin` header on all HTTP responses.
    pub cors_origin: String,
    /// Global allowlist of HTTP header names forwarded to downstream tool calls.
    pub forward_headers: Vec<String>,
}

/// Global configuration, initialized lazily on first access.
pub static ENV: LazyLock<WanakuEnv> = LazyLock::new(WanakuEnv::from_env);

impl WanakuEnv {
    fn from_env() -> Self {
        let backend = std::env::var(WANAKU_PERSIST_BACKEND)
            .unwrap_or_else(|_| "file".to_owned());
        let persist = (backend != "none").then(|| {
            let dir = std::env::var(WANAKU_PERSIST_PATH).unwrap_or_else(|_| {
                let home = std::env::var("HOME").unwrap_or_else(|_| ".".to_owned());
                format!("{home}/.wanaku/server")
            });
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
            inference_tls_sni: parsed.tls_sni,
            persist,
            ui_path: std::env::var(WANAKU_UI_PATH).ok().map(PathBuf::from),
            cors_origin: std::env::var(WANAKU_CORS_ORIGIN)
                .unwrap_or_else(|_| "*".to_owned()),
            forward_headers: std::env::var(WANAKU_FORWARD_HEADERS)
                .unwrap_or_default()
                .split(',')
                .map(|s| s.trim().to_lowercase())
                .filter(|s| !s.is_empty())
                .collect(),
        }
    }
}

struct ParsedUpstream {
    host_port: String,
    tls_sni: Option<String>,
}

/// Accepts `host:port`, `http://host/path`, or `https://host:port/path`.
/// Any path component is discarded — only the authority (host and port)
/// matters for the proxy's load-balancer endpoint.
fn parse_upstream(raw: &str) -> ParsedUpstream {
    let (host_and_rest, default_port, is_tls) =
        if let Some(rest) = raw.strip_prefix("https://") {
            (rest, "443", true)
        } else if let Some(rest) = raw.strip_prefix("http://") {
            (rest, "80", false)
        } else {
            return ParsedUpstream {
                host_port: raw.to_owned(),
                tls_sni: None,
            };
        };

    let authority = match host_and_rest.find('/') {
        Some(i) => &host_and_rest[..i],
        None => host_and_rest,
    };

    let (hostname, port) = split_authority(authority);
    let host_port = match port {
        Some(_) => authority.to_owned(),
        None => format!("{authority}:{default_port}"),
    };

    ParsedUpstream {
        host_port,
        tls_sni: if is_tls { Some(hostname.to_owned()) } else { None },
    }
}

/// Splits an authority into a hostname and optional port, honoring IPv6
/// bracket notation (`[::1]:8443`) where a naive split on the first or
/// last `:` breaks.
fn split_authority(authority: &str) -> (&str, Option<&str>) {
    if let Some(bracket_end) = authority.find(']') {
        let host = &authority[..=bracket_end];
        return (host, authority[bracket_end + 1..].strip_prefix(':'));
    }
    match authority.rsplit_once(':') {
        Some((host, port)) => (host, Some(port)),
        None => (authority, None),
    }
}

#[cfg(test)]
mod tests {
    use super::parse_upstream;

    #[test]
    fn bare_host_port_unchanged() {
        let p = parse_upstream("127.0.0.1:11434");
        assert_eq!(p.host_port, "127.0.0.1:11434");
        assert!(p.tls_sni.is_none());
    }

    #[test]
    fn https_with_path() {
        let p = parse_upstream("https://openrouter.ai/api");
        assert_eq!(p.host_port, "openrouter.ai:443");
        assert_eq!(p.tls_sni.as_deref(), Some("openrouter.ai"));
    }

    #[test]
    fn https_with_deep_path() {
        let p = parse_upstream("https://host.com/some/long/path");
        assert_eq!(p.host_port, "host.com:443");
        assert_eq!(p.tls_sni.as_deref(), Some("host.com"));
    }

    #[test]
    fn https_with_port_and_path() {
        let p = parse_upstream("https://host.com:8443/v1");
        assert_eq!(p.host_port, "host.com:8443");
        assert_eq!(p.tls_sni.as_deref(), Some("host.com"));
    }

    #[test]
    fn http_plain_no_tls() {
        let p = parse_upstream("http://localhost:11434");
        assert_eq!(p.host_port, "localhost:11434");
        assert!(p.tls_sni.is_none());
    }

    #[test]
    fn http_no_port() {
        let p = parse_upstream("http://example.com");
        assert_eq!(p.host_port, "example.com:80");
        assert!(p.tls_sni.is_none());
    }

    #[test]
    fn https_ipv6_with_port() {
        let p = parse_upstream("https://[::1]:8443/v1");
        assert_eq!(p.host_port, "[::1]:8443");
        assert_eq!(p.tls_sni.as_deref(), Some("[::1]"));
    }

    #[test]
    fn https_ipv6_no_port() {
        let p = parse_upstream("https://[::1]/v1");
        assert_eq!(p.host_port, "[::1]:443");
        assert_eq!(p.tls_sni.as_deref(), Some("[::1]"));
    }
}
