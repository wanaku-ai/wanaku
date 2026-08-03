use std::collections::VecDeque;
use std::sync::{Arc, RwLock};

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Interaction {
    pub epoch_ms: u64,
    pub path: String,
    pub request_body: serde_json::Value,
    pub response_body: serde_json::Value,
    pub status_code: u16,
    pub duration_ms: u64,
}

pub trait InteractionStore: Send + Sync {
    fn record(&self, interaction: Interaction);
    fn list(&self) -> Vec<Interaction>;
    fn len(&self) -> usize;
    fn is_empty(&self) -> bool;
    fn clear(&self);
}

#[derive(Clone)]
pub struct InMemoryInteractionStore {
    interactions: Arc<RwLock<VecDeque<Interaction>>>,
    capacity: usize,
}

impl InMemoryInteractionStore {
    #[must_use]
    pub fn new(capacity: usize) -> Self {
        Self {
            interactions: Arc::new(RwLock::new(VecDeque::with_capacity(capacity))),
            capacity,
        }
    }
}

impl InteractionStore for InMemoryInteractionStore {
    fn record(&self, interaction: Interaction) {
        if let Ok(mut store) = self.interactions.write() {
            if store.len() >= self.capacity {
                store.pop_front();
            }
            store.push_back(interaction);
        }
    }

    fn list(&self) -> Vec<Interaction> {
        self.interactions
            .read()
            .map(|store| store.iter().cloned().collect())
            .unwrap_or_default()
    }

    fn len(&self) -> usize {
        self.interactions
            .read()
            .map(|store| store.len())
            .unwrap_or(0)
    }

    fn is_empty(&self) -> bool {
        self.len() == 0
    }

    fn clear(&self) {
        if let Ok(mut store) = self.interactions.write() {
            store.clear();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn store_and_retrieve() {
        let store = InMemoryInteractionStore::new(10);
        store.record(Interaction {
            epoch_ms: 1000,
            path: "/test".to_owned(),
            request_body: serde_json::json!({"prompt": "hello"}),
            response_body: serde_json::json!({"response": "hi"}),
            status_code: 200,
            duration_ms: 42,
        });

        assert_eq!(store.len(), 1);
        let items = store.list();
        assert_eq!(items[0].path, "/test");
    }

    #[test]
    fn ring_buffer_evicts_oldest() {
        let store = InMemoryInteractionStore::new(2);
        for i in 0..3 {
            store.record(Interaction {
                epoch_ms: i,
                path: format!("/req-{i}"),
                request_body: serde_json::Value::Null,
                response_body: serde_json::Value::Null,
                status_code: 200,
                duration_ms: 0,
            });
        }

        assert_eq!(store.len(), 2);
        let items = store.list();
        assert_eq!(items[0].path, "/req-1");
        assert_eq!(items[1].path, "/req-2");
    }

    #[test]
    fn clear_empties_store() {
        let store = InMemoryInteractionStore::new(10);
        store.record(Interaction {
            epoch_ms: 0,
            path: "/test".to_owned(),
            request_body: serde_json::Value::Null,
            response_body: serde_json::Value::Null,
            status_code: 200,
            duration_ms: 0,
        });

        store.clear();
        assert!(store.is_empty());
    }
}
