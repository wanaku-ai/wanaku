/// The result of a WASM action script execution.
/// Determined by which response.* function the guest called.
#[derive(Debug, Clone)]
pub enum ActionResult {
    /// No response function was called — default behavior.
    Pass,
    /// Guest called response.block(reason).
    Block(String),
    /// Guest called response.warn(message).
    Warn(String),
    /// Guest called response.filter_tools(names).
    FilterTools(Vec<String>),
    /// Guest called response.set_metadata(key, value).
    SetMetadata(String, String),
}
