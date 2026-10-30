# Contributing to Wanaku

Thank you for your interest in contributing to Wanaku. This page directs contributors to the development guides and the minimum validation workflow.

## Getting Started

Use [Getting Started](../get-started/quickstart.md) to run Wanaku and verify an MCP request. It is an operator quick start, not a contributor setup guide.

For implementation work, read the guide that matches the area you plan to change:

## Quick Links

- [Architecture Overview](../get-started/architecture.md): filter order, request flow, registries, routing, and feature integration.
- [Feature crates](../extend/feature-crates.md): the `Feature` trait and the feature lifecycle.
- [Build a feature crate](../extend/build-a-feature.md): a custom feature tutorial.
- [Configuration Reference](../reference/configuration.md): environment variables, pipeline configuration, and Wanaku bootstrap configuration.
- [Admin UI Development](admin-ui.md): React development, generated API clients, tests, and UI conventions.
- [Container Image Build](container-build.md): build the Wanaku container image locally with the Cargo toolchain.
- [Plugin Development](../extend/plugin-development-guide.md): the implemented Web UI plugin contract and local test workflow.
- [Evaluators](../govern/evaluators.md): evaluator configuration, runtime management, and internals.
- [Write evaluator actions in JavaScript](../extend/evaluator-actions-js.md) and [in Rust](../extend/evaluator-actions-rust.md): the action script guides.
- [Testing](testing.md): the evaluator test suites.

## Prerequisites

Install these tools before you build the workspace:

- Rust 1.96 or later. This is the minimum supported Rust version (MSRV).
- Node.js 22.18.0 or later.
- Yarn classic (1.x).
- `cargo-component` 0.21.1 and the `wasm32-wasip1` target. These build the evaluator WASM actions.

Install the evaluator build tools:

```bash
rustup target add wasm32-wasip1
cargo install cargo-component --version 0.21.1 --locked
```

The default `ui` feature of `wanaku-server` embeds the admin UI. When `ui/admin/dist/index.html` does not exist, `cargo build` runs `yarn install` and `yarn build` in `ui/admin`. If Yarn is not installed, the build fails with `yarn not found`.

`cargo build` also compiles the Rust evaluator actions into `actions/dist/`. If `cargo-component` is not installed, the build prints a `cargo:warning` and continues without the `.wasm` files. The committed `wanaku.yaml` refers to these files, so the server does not start with it. The build skips an action when its `.wasm` file already exists. To rebuild an action after you change it, delete its file in `actions/dist/`.

## Contributor Workflow

1. Build the workspace:

   ```bash
   cargo build
   ```

   To build the server without the admin UI and without Yarn, use the headless build:

   ```bash
   cargo build --no-default-features -p wanaku-server
   ```

2. Run the Rust tests:

   ```bash
   cargo test
   ```

3. Run the evaluator example action tests. These tests need the `.wasm` files in `actions/dist/`:

   ```bash
   cargo test -p wanaku-feature-evaluator --lib --test example_actions -- --ignored
   ```

4. Check formatting:

   ```bash
   cargo fmt --check
   ```

5. Run Clippy for the default build and the headless build:

   ```bash
   cargo clippy --all-targets -- -D warnings
   cargo clippy --all-targets --no-default-features -p wanaku-server -- -D warnings
   ```

The `.github/workflows/pr-build.yml` workflow runs the same checks for each pull request.

Add meaningful tests for new behavior. Update the relevant documentation when you add or change a feature.

For admin UI changes, follow the [E2E Tests](admin-ui.md#e2e-tests) section in Admin UI Development. At minimum, cover the page title, add flow, and delete flow when the page supports those actions.

Before you submit a pull request, review the diff for unrelated changes. Keep related corrections in the existing commit when the project maintainers ask for amended commits.

## Code of Conduct

We are committed to providing a welcoming and inclusive environment. Please be respectful and professional in all interactions.

## Questions?

If you have questions about contributing, please open an issue on GitHub or join our community discussions.

## License

By contributing to Wanaku, you agree that your contributions will be licensed under the Apache 2.0 License.
