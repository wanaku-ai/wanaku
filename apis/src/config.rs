//! Centralized environment variable configuration for Wanaku Praxis.
//!
//! All `WANAKU_*` environment variables are read once at startup via
//! [`LazyLock`] and exposed through the [`ENV`] static. No other module
//! should call `std::env::var` for these variables directly.

use std::path::PathBuf;
use std::sync::LazyLock;

/// Management API listen address (default `0.0.0.0:9090`).
const WANAKU_MGMT_LISTEN: &str = "WANAKU_MGMT_LISTEN";

/// Ollama backend address used in the default Praxis config (default `127.0.0.1:11434`).
const WANAKU_OLLAMA_UPSTREAM: &str = "WANAKU_OLLAMA_UPSTREAM";

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
    /// Ollama upstream address for the default Praxis pipeline config.
    pub ollama_upstream: String,
    /// File-persistence config. `None` when persistence is disabled.
    pub persist: Option<PersistEnv>,
    /// Classic proxy base URL. `None` when proxying is disabled.
    pub classic_url: Option<String>,
    /// Override path for serving the admin UI from the filesystem.
    pub ui_path: Option<PathBuf>,
}

/// Global configuration, initialized lazily on first access.
pub static ENV: LazyLock<WanakuEnv> = LazyLock::new(WanakuEnv::from_env);

impl WanakuEnv {
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

        Self {
            mgmt_listen: std::env::var(WANAKU_MGMT_LISTEN)
                .unwrap_or_else(|_| "0.0.0.0:9090".to_owned()),
            ollama_upstream: std::env::var(WANAKU_OLLAMA_UPSTREAM)
                .unwrap_or_else(|_| "127.0.0.1:11434".to_owned()),
            persist,
            classic_url: std::env::var(WANAKU_CLASSIC_URL)
                .ok()
                .map(|u| u.trim_end_matches('/').to_owned()),
            ui_path: std::env::var(WANAKU_UI_PATH).ok().map(PathBuf::from),
        }
    }
}
