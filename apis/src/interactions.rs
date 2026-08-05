use std::collections::VecDeque;
use std::sync::{Arc, RwLock};

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, utoipa::ToSchema)]
pub struct Interaction {
    pub epoch_ms: u64,
    pub path: String,
    pub conversation_id: Option<String>,
    pub completion_id: Option<String>,
    pub model: Option<String>,
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
        match self.interactions.write() {
            Ok(mut store) => {
                if store.len() >= self.capacity {
                    store.pop_front();
                }
                store.push_back(interaction);
            }
            Err(e) => {
                tracing::warn!("interaction store write lock poisoned, dropping record: {e}");
            }
        }
    }

    fn list(&self) -> Vec<Interaction> {
        match self.interactions.read() {
            Ok(store) => store.iter().cloned().collect(),
            Err(e) => {
                tracing::warn!("interaction store read lock poisoned: {e}");
                Vec::new()
            }
        }
    }

    fn len(&self) -> usize {
        match self.interactions.read() {
            Ok(store) => store.len(),
            Err(e) => {
                tracing::warn!("interaction store read lock poisoned: {e}");
                0
            }
        }
    }

    fn is_empty(&self) -> bool {
        self.len() == 0
    }

    fn clear(&self) {
        match self.interactions.write() {
            Ok(mut store) => store.clear(),
            Err(e) => {
                tracing::warn!("interaction store write lock poisoned, cannot clear: {e}");
            }
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
            conversation_id: Some("wk-test1234".to_owned()),
            completion_id: Some("chatcmpl-1".to_owned()),
            model: Some("llama3.2".to_owned()),
        });

        assert_eq!(store.len(), 1);
        let items = store.list();
        assert_eq!(items[0].path, "/test");
        assert_eq!(items[0].completion_id.as_deref(), Some("chatcmpl-1"));
        assert_eq!(items[0].model.as_deref(), Some("llama3.2"));
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
                conversation_id: None,
                completion_id: None,
                model: None,
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
            conversation_id: None,
            completion_id: None,
            model: None,
        });

        store.clear();
        assert!(store.is_empty());
    }
}
