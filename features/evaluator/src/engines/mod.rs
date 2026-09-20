//! Engine adapters. Each module owns the configuration and runtime behavior
//! for one evaluator engine. The parent evaluator feature owns the shared
//! pipeline, WASM processor, revision, and management API behavior.

pub mod llm;
pub mod system_one;
