//! Request-scoped contracts shared by the WASM runtime and MCP pipeline.

use std::sync::{
    Arc,
    atomic::{AtomicUsize, Ordering},
};
use std::time::{Duration, Instant};

use serde::{Deserialize, Serialize};
use serde_json::Value;

/// Stable identity of a tool in its concrete owning namespace.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ToolTarget {
    pub namespace: String,
    pub name: String,
    /// Runtime binding selected before policy evaluation; absent for ordinary tools.
    #[serde(default)]
    pub binding_id: Option<String>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum HostErrorKind {
    Denied,
    InvalidArguments,
    NotFound,
    Unavailable,
    Timeout,
    Limit,
    Protocol,
    Internal,
    Execution,
}

/// A host or protocol failure. MCP `isError` results remain successful values.
#[derive(Debug, Clone, Serialize, Deserialize, thiserror::Error)]
#[error("{message}")]
pub struct HostError {
    pub kind: HostErrorKind,
    pub message: String,
    pub code: Option<i32>,
}

impl HostError {
    pub fn new(kind: HostErrorKind, message: impl Into<String>) -> Self {
        Self {
            kind,
            message: message.into(),
            code: None,
        }
    }
}

/// A client bound to the current caller, namespace scope, and invocation budget.
#[async_trait::async_trait]
pub trait McpHostClient: Send + Sync {
    async fn request(
        &self,
        method: &str,
        namespace: &str,
        params: Value,
    ) -> Result<Value, HostError>;
}

/// Registry definition selected during namespace resolution, before governance.
#[derive(Debug, Clone)]
pub struct ResolvedTool(pub crate::registry::ToolEntry);

/// Namespace resolution ran, including when the selected tool did not exist.
#[derive(Debug, Clone, Copy)]
pub struct ToolResolutionComplete;

/// Request-context adapter supplied by the server, never by the guest.
pub trait McpHostFactory: Send + Sync {
    fn create(
        &self,
        ctx: &praxis_filter::HttpFilterContext<'_>,
    ) -> Result<Arc<dyn McpHostClient>, HostError>;
}

/// Runtime adapter supplied by the tool-handler feature.
#[async_trait::async_trait]
pub trait ToolHandler: Send + Sync {
    async fn call(
        &self,
        target: ToolTarget,
        arguments: Value,
        client: Arc<dyn McpHostClient>,
    ) -> Result<Value, HostError>;
}

#[derive(Debug)]
struct InvocationBudget {
    deadline: Instant,
    remaining: AtomicUsize,
}

/// Trusted invocation ancestry and a budget shared by every nested host call.
#[derive(Debug, Clone)]
pub struct InvocationContext {
    budget: Arc<InvocationBudget>,
    ancestors: Vec<ToolTarget>,
}

impl Default for InvocationContext {
    fn default() -> Self {
        Self {
            budget: Arc::new(InvocationBudget {
                deadline: Instant::now() + Duration::from_secs(30),
                remaining: AtomicUsize::new(64),
            }),
            ancestors: Vec::new(),
        }
    }
}

impl InvocationContext {
    pub fn ancestry(&self) -> &[ToolTarget] {
        &self.ancestors
    }

    pub fn enter(&self, target: ToolTarget) -> Result<Self, HostError> {
        if self
            .ancestors
            .iter()
            .any(|ancestor| ancestor.namespace == target.namespace && ancestor.name == target.name)
            || self.ancestors.len() >= 8
        {
            return Err(HostError::new(
                HostErrorKind::Limit,
                "recursive handler call or maximum handler depth exceeded",
            ));
        }
        self.remaining()?;
        let mut next = self.clone();
        next.ancestors.push(target);
        Ok(next)
    }

    pub fn remaining(&self) -> Result<Duration, HostError> {
        self.budget
            .deadline
            .checked_duration_since(Instant::now())
            .ok_or_else(|| HostError::new(HostErrorKind::Timeout, "invocation deadline exceeded"))
    }

    pub fn consume(&self) -> Result<(), HostError> {
        self.remaining()?;
        self.budget
            .remaining
            .fetch_update(Ordering::Relaxed, Ordering::Relaxed, |remaining| {
                remaining.checked_sub(1)
            })
            .map(|_| ())
            .map_err(|_| HostError::new(HostErrorKind::Limit, "nested MCP call budget exceeded"))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ancestry_and_call_budget_are_shared_and_bounded() {
        let context = InvocationContext::default();
        let target = ToolTarget {
            namespace: "a".into(),
            name: "tool".into(),
            binding_id: None,
        };
        let child = context.enter(target.clone()).unwrap();
        assert!(child.enter(target).is_err());
        for _ in 0..64 {
            child.consume().unwrap();
        }
        assert!(context.consume().is_err());
    }
}
