# Contributing to Wanaku

Thank you for your interest in contributing to Wanaku. This page directs contributors to the development guides and the minimum validation workflow.

## Getting Started

Use [Getting Started](getting-started.md) to run Wanaku and verify an MCP request. It is an operator quick start, not a contributor setup guide.

For implementation work, read the guide that matches the area you plan to change:

## Quick Links

- [Architecture Overview](architecture.md): filter order, request flow, registries, routing, and feature integration.
- [Feature Development](features.md): the `Feature` trait and a custom feature tutorial.
- [Configuration Reference](configuration.md): environment variables, pipeline configuration, and Wanaku bootstrap configuration.
- [Admin UI Development](contributing-admin-ui.md): React development, generated API clients, tests, and UI conventions.
- [Plugin Development](plugin-development-guide.md): the implemented Web UI plugin contract and local test workflow.
- [Evaluator Engine](evaluator-engine.md): evaluator configuration and JavaScript or Rust action scripts.

## Contributor Workflow

1. Build the workspace:

   ```bash
   cargo build
   ```

2. Run the Rust tests:

   ```bash
   cargo test
   ```

3. Check formatting:

   ```bash
   cargo fmt --check
   ```

4. Run Clippy:

   ```bash
   cargo clippy --all-targets --all-features -- -D warnings
   ```
   
5. Build WASM evaluators 

   ```bash
   cargo build -p wanaku-feature-evaluator
   ```

Add meaningful tests for new behavior. Update the relevant documentation when you add or change a feature.

For admin UI changes, follow the E2E workflow in [Admin UI Development](contributing-admin-ui.md). At minimum, cover the page title, add flow, and delete flow when the page supports those actions.

Before you submit a pull request, review the diff for unrelated changes. Keep related corrections in the existing commit when the project maintainers ask for amended commits.

## Build Time

The workspace is configured to keep a clean development build short.

- The `dev` profile keeps line tables only for workspace crates. Backtraces show file names and line numbers for Wanaku code.
- The `dev` profile has no debug information for dependencies. A debugger cannot show local variables in dependency code.
- The server does not compile the Praxis policy engine by default. The Praxis `policy` filter (Cedar, Rego, CEL) is not available in a default build.
- The evaluator enables only the Wasmtime and WASI features that it uses.

To build the server with the Praxis `policy` filter, enable the `policy-engine` feature:

```bash
cargo build -p wanaku-server --features policy-engine
```

To get full debug information for one dependency, add a profile override to your local `Cargo.toml`:

```toml
[profile.dev.package.wasmtime]
debug = true
```

To find the slowest crates in a build, use Cargo timings:

```bash
cargo build -p wanaku-server --timings
```

## Code of Conduct

We are committed to providing a welcoming and inclusive environment. Please be respectful and professional in all interactions.

## Questions?

If you have questions about contributing, please open an issue on GitHub or join our community discussions.

## License

By contributing to Wanaku, you agree that your contributions will be licensed under the Apache 2.0 License.
