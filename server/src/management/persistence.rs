//! Pingora shutdown integration for registry persistence.

use std::sync::Arc;
use std::time::Duration;

use async_trait::async_trait;
use pingora_core::server::ShutdownWatch;
use pingora_core::services::background::BackgroundService;
use tracing::{info, warn};

use wanaku_infra::registry::InMemoryRegistry;

const SHUTDOWN_FLUSH_TIMEOUT: Duration = Duration::from_secs(10);

/// Flushes pending registry snapshots during orderly Pingora shutdown.
pub struct RegistryPersistenceService {
    registry: InMemoryRegistry,
}

impl RegistryPersistenceService {
    #[must_use]
    pub const fn new(registry: InMemoryRegistry) -> Self {
        Self { registry }
    }
}

#[async_trait]
impl BackgroundService for RegistryPersistenceService {
    async fn start(&self, mut shutdown: ShutdownWatch) {
        while shutdown.changed().await.is_ok() {
            if !*shutdown.borrow() {
                continue;
            }
            let registry = self.registry.clone();
            match tokio::task::spawn_blocking(move || {
                registry.flush_persistence(SHUTDOWN_FLUSH_TIMEOUT)
            })
            .await
            {
                Ok(Ok(())) => info!("registry persistence flushed during shutdown"),
                Ok(Err(error)) => {
                    warn!(error = %error, "registry persistence did not flush during shutdown")
                }
                Err(error) => warn!(error = %error, "registry persistence shutdown task failed"),
            }
            return;
        }
    }
}

/// Wrap the registry persistence shutdown service for Pingora registration.
#[must_use]
pub fn registry_persistence_service(registry: InMemoryRegistry) -> Arc<RegistryPersistenceService> {
    Arc::new(RegistryPersistenceService::new(registry))
}
