use std::io::Write;

fn main() {
    let spec = wanaku_server::openapi::openapi_json();
    std::io::stdout()
        .write_all(&spec)
        .unwrap_or_else(|e| eprintln!("failed to write OpenAPI spec: {e}"));
}
