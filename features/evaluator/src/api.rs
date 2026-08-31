//! OpenAPI request and response types for evaluator management.

use std::collections::HashMap;

use serde::{Deserialize, Serialize};
use wanaku_types::revision::RevisionMetadata;

use crate::config::EvaluatorDef;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct UpdateEvaluatorsRequest {
    #[serde(default)]
    pub evaluators: Vec<EvaluatorDef>,
    #[serde(default)]
    pub expected_revision: Option<u64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct ActivateRevisionRequest {
    #[serde(default)]
    pub expected_revision: Option<u64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct EvaluatorRevisionResponse {
    pub revision: RevisionMetadata,
    pub evaluators: Vec<EvaluatorDef>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct BindNamespaceRequest {
    pub conversation_id: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct NamespaceBinding {
    pub namespace: String,
    pub conversation_id: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct UnbindNamespaceResponse {
    pub unbound: String,
}

pub type NamespaceBindings = HashMap<String, String>;
