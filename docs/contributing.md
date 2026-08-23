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

Add meaningful tests for new behavior. Update the relevant documentation when you add or change a feature.

For admin UI changes, follow the E2E workflow in [Admin UI Development](contributing-admin-ui.md). At minimum, cover the page title, add flow, and delete flow when the page supports those actions.

Before you submit a pull request, review the diff for unrelated changes. Keep related corrections in the existing commit when the project maintainers ask for amended commits.

## Code of Conduct

We are committed to providing a welcoming and inclusive environment. Please be respectful and professional in all interactions.

## Questions?

If you have questions about contributing, please open an issue on GitHub or join our community discussions.

## License

By contributing to Wanaku, you agree that your contributions will be licensed under the Apache 2.0 License.
