use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::BTreeMap;
use wanaku_types::{audit::RedactionMetadata, governance::GovernancePosture};

#[derive(Debug, Clone, Default, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(default, deny_unknown_fields)]
pub struct CandidateSelection {
    pub policy_revision: Option<u64>,
    pub evaluator_revision: Option<u64>,
    pub base_policy_revision: Option<u64>,
    pub base_evaluator_revision: Option<u64>,
    pub inline_policy: Option<Value>,
    pub inline_evaluators: Option<Value>,
    pub policy_patch: Option<Value>,
    pub evaluator_patch: Option<Value>,
    pub evidence_ids: Vec<String>,
}
#[derive(Debug, Clone, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(deny_unknown_fields)]
pub struct SyntheticAction {
    #[serde(default = "default_namespace")]
    pub namespace: String,
    pub request: Value,
    #[serde(default)]
    pub evidence_id: Option<String>,
}
fn default_namespace() -> String {
    "default".to_owned()
}
#[derive(Debug, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(deny_unknown_fields)]
pub struct ValidationRequest {
    #[serde(default)]
    pub candidate: CandidateSelection,
    #[serde(default)]
    pub allow_external_evaluators: bool,
}
#[derive(Debug, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(deny_unknown_fields)]
pub struct SimulationRequest {
    #[serde(default)]
    pub candidate: CandidateSelection,
    pub action: SyntheticAction,
    #[serde(default)]
    pub allow_external_evaluators: bool,
}
#[derive(Debug, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[serde(deny_unknown_fields)]
pub struct ReplayRequest {
    #[serde(default)]
    pub candidate: CandidateSelection,
    #[serde(default)]
    pub actions: Vec<SyntheticAction>,
    #[serde(default)]
    pub audit_event_ids: Vec<String>,
    #[serde(default)]
    pub allow_external_evaluators: bool,
}
#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct Diagnostic {
    pub severity: String,
    pub reason_code: String,
    pub path: String,
}
#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct PolicyIdentity {
    pub policy_revision: Option<u64>,
    pub evaluator_revision: Option<u64>,
    pub policy_checksum: Option<String>,
    pub evaluator_checksum: Option<String>,
    pub base_policy_revision: Option<u64>,
    pub base_evaluator_revision: Option<u64>,
}
#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct ValidationReport {
    pub schema_version: String,
    pub valid: bool,
    pub stale: bool,
    pub identity: PolicyIdentity,
    pub diagnostics: Vec<Diagnostic>,
}
#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct StageDecision {
    pub stage: String,
    pub decision: String,
    pub reason_code: String,
    pub matched_ids: Vec<String>,
}
#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[expect(
    clippy::struct_excessive_bools,
    reason = "independent report flags in versioned wire schema"
)]
pub struct SimulationReport {
    pub schema_version: String,
    pub identity: PolicyIdentity,
    pub stale: bool,
    pub namespace: String,
    pub operation: String,
    pub target: Option<String>,
    pub posture: GovernancePosture,
    pub stages: Vec<StageDecision>,
    pub decision: String,
    pub baseline_used: bool,
    pub failure_used: bool,
    pub conditional: bool,
    pub redaction: RedactionMetadata,
    pub duration_ms: u64,
}
#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
#[expect(
    clippy::struct_excessive_bools,
    reason = "independent impact flags in versioned wire schema"
)]
pub struct ReplayDetail {
    pub index: usize,
    pub category: String,
    pub access_expansion: bool,
    pub compatibility_impact: bool,
    pub outside_evidence: bool,
    pub active: SimulationReport,
    pub candidate: SimulationReport,
}
#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct ReplayJob {
    pub schema_version: String,
    pub id: String,
    pub status: String,
    pub stale: bool,
    pub identity: PolicyIdentity,
    pub counts: BTreeMap<String, usize>,
    pub details: Vec<ReplayDetail>,
    pub skipped_audit_ids: Vec<String>,
    pub reason_code: Option<String>,
    pub duration_ms: u64,
}
#[derive(Debug, Clone, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct SimulationError {
    pub reason_code: String,
    pub validation: Option<ValidationReport>,
}
