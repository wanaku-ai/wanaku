//! OpenAPI request and response types for action-policy management.

use serde::{Deserialize, Serialize};
use wanaku_types::revision::RevisionMetadata;

use crate::ActionPolicy;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct UpdateActionPolicyRequest {
    pub policy: ActionPolicy,
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
pub struct ActionPolicyRevisionResponse {
    pub revision: RevisionMetadata,
    pub policy: ActionPolicy,
}
