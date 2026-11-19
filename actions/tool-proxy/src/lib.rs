//! A tool handler that inspects and invokes a registered MCP tool.

mod bindings {
    wit_bindgen::generate!({
        path: "../../features/tool-handler/wit/tool-handler.wit",
        world: "tool-handler",
    });
}

use bindings::Guest;
use bindings::wanaku::tool_handler::{catalog, log, mcp, types};

struct ToolProxy;

impl Guest for ToolProxy {
    fn handle(request: types::Request) -> Result<String, types::HostError> {
        // The host checks the handler grant and the originating request scope.
        let _definition = catalog::get_tool("backend", "echo")?;
        log::info("Invoking backend/echo through the Wanaku MCP host API");
        // Return the complete MCP result, including structured data and isError.
        mcp::call_tool("backend", "echo", &request.arguments)
    }
}

bindings::export!(ToolProxy with_types_in bindings);
