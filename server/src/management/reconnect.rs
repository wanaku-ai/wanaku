//! Background reconnect loop for forwards marked unavailable.
//!
//! When a forward becomes unreachable — at startup, or because a manual refresh
//! failed — its [`ForwardEntry::available`] flag is set to `false`. Without this
//! service nothing would ever flip it back once the upstream MCP server recovers,
//! leaving the forward shown as "Unavailable" until a human triggers a manual
//! refresh.
//!
//! This [`BackgroundService`] runs on the Pingora runtime and, on a configurable
//! interval, re-probes every forward that is currently `available == false` by
//! reusing the same [`discover_and_update_forward`] logic the manual refresh
//! endpoint uses. Because it iterates the live registry it naturally covers
//! forwards declared in `wanaku.yaml`, registered via the management API, and
//! restored from persistence alike.

use std::sync::Arc;
use std::time::Duration;

use async_trait::async_trait;
use pingora_core::server::ShutdownWatch;
use pingora_core::services::background::BackgroundService;
use tracing::{debug, info};

use wanaku_infra::registry::InMemoryRegistry;
use wanaku_types::registry::ForwardRegistry;

use super::handlers::discover_and_update_forward;

/// Periodically re-probes unavailable forwards and restores them once reachable.
pub struct ForwardReconnectService {
    registry: InMemoryRegistry,
    interval: Duration,
}

impl ForwardReconnectService {
    /// Create a new reconnect service that probes unavailable forwards every
    /// `interval`.
    #[must_use]
    pub const fn new(registry: InMemoryRegistry, interval: Duration) -> Self {
        Self { registry, interval }
    }

    /// Run a single reconnect sweep: probe every forward that is currently
    /// marked unavailable and let discovery flip it back to available if the
    /// upstream has recovered. Returns the number of forwards probed.
    async fn run_once(&self) -> usize {
        let unavailable: Vec<_> = self
            .registry
            .list_forwards()
            .into_iter()
            .filter(|fwd| !fwd.available)
            .collect();

        if unavailable.is_empty() {
            return 0;
        }

        let count = unavailable.len();
        debug!(forwards = count, "re-checking unavailable forwards");
        for fwd in &unavailable {
            debug!(forward = %fwd.name, address = %fwd.address, "probing unavailable forward");
            discover_and_update_forward(&self.registry, fwd).await;
        }
        count
    }
}

#[async_trait]
impl BackgroundService for ForwardReconnectService {
    async fn start(&self, mut shutdown: ShutdownWatch) {
        info!(interval_secs = self.interval.as_secs(), "forward reconnect loop started");
        let mut ticker = tokio::time::interval(self.interval);
        // Skip the immediate tick that `interval` fires at t=0; startup already
        // probes yaml forwards, and this avoids a redundant sweep at boot.
        ticker.tick().await;
        loop {
            tokio::select! {
                _ = ticker.tick() => {
                    self.run_once().await;
                }
                _ = shutdown.changed() => {
                    if *shutdown.borrow() {
                        info!("forward reconnect loop shutting down");
                        break;
                    }
                }
            }
        }
    }
}

/// Wraps a [`ForwardReconnectService`] in an [`Arc`] so it can be registered as
/// a Pingora background service via [`pingora_core::services::background::GenBackgroundService`].
#[must_use]
pub fn reconnect_service(
    registry: InMemoryRegistry,
    interval: Duration,
) -> Arc<ForwardReconnectService> {
    Arc::new(ForwardReconnectService::new(registry, interval))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;
    use wanaku_types::registry::ForwardEntry;

    fn forward(name: &str, available: bool) -> ForwardEntry {
        ForwardEntry {
            name: name.to_owned(),
            // Unroutable address so discovery fails fast and deterministically.
            address: "http://127.0.0.1:1/mcp".to_owned(),
            namespace: None,
            server_info: None,
            labels: HashMap::new(),
            available,
            status_message: None,
        }
    }

    #[tokio::test]
    async fn run_once_skips_when_all_available() {
        let registry = InMemoryRegistry::new();
        registry.register_forward(forward("healthy", true));

        let svc = ForwardReconnectService::new(registry, Duration::from_secs(30));
        assert_eq!(svc.run_once().await, 0, "available forwards must not be probed");
    }

    #[tokio::test]
    async fn run_once_probes_only_unavailable() {
        let registry = InMemoryRegistry::new();
        registry.register_forward(forward("healthy", true));
        registry.register_forward(forward("down-1", false));
        registry.register_forward(forward("down-2", false));

        let svc = ForwardReconnectService::new(registry.clone(), Duration::from_secs(30));
        assert_eq!(svc.run_once().await, 2, "only unavailable forwards should be probed");

        // Discovery against the unroutable address fails, so they stay
        // unavailable and gain a status message.
        let down = registry.get_forward("down-1").expect("forward exists");
        assert!(!down.available);
        assert!(down.status_message.is_some(), "failed probe records a status message");

        // The healthy forward is untouched.
        let healthy = registry.get_forward("healthy").expect("forward exists");
        assert!(healthy.available);
    }
}
