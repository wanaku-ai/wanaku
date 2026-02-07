<!--
Sync Impact Report
==================
Version change: N/A → 1.0.0 (initial ratification)
Modified principles: N/A (initial creation)
Added sections:
  - Core Principles (5 principles)
  - Quality Standards section
  - Development Workflow section
  - Governance section
Removed sections: N/A
Templates requiring updates:
  - .specify/templates/plan-template.md: ✅ Compatible (Constitution Check section exists)
  - .specify/templates/spec-template.md: ✅ Compatible (success criteria align with performance principle)
  - .specify/templates/tasks-template.md: ✅ Compatible (test phases align with testing principle)
Follow-up TODOs: None
-->

# Wanaku Constitution

## Core Principles

### I. Code Quality First

All code contributions MUST adhere to consistent style, structure, and maintainability standards:

- Code MUST pass all static analysis checks (Checkstyle, SpotBugs) before merge
- Code MUST follow established project patterns; deviations require documented justification
- Dependencies MUST be managed through proper Maven BOM/versioning; no hardcoded versions in child modules
- Dead code, unused imports, and commented-out code MUST be removed
- All public APIs MUST include Javadoc with parameter/return documentation
- Complex logic MUST include inline comments explaining the "why" not the "what"

**Rationale**: Consistent, maintainable code reduces cognitive load, accelerates onboarding, and prevents technical debt accumulation.

### II. Testing Standards

All production code MUST be accompanied by appropriate test coverage:

- New features MUST include unit tests covering core logic paths
- Integration tests MUST exist for gRPC service contracts, MCP protocol handlers, and cross-component interactions
- Tests MUST be independent and repeatable; no reliance on external services without proper mocking/containers
- Test names MUST clearly describe the scenario being tested (Given-When-Then or equivalent)
- Flaky tests MUST be fixed or quarantined immediately; never ignored
- Critical paths (authentication, routing, tool invocation) MUST have contract tests

**Rationale**: Tests are executable specifications that prevent regressions and document expected behavior.

### III. User Experience Consistency

All user-facing interfaces MUST provide a consistent, predictable experience:

- CLI commands MUST follow the established verb-noun pattern (`wanaku <resource> <action>`)
- Error messages MUST be actionable: describe what went wrong and suggest remediation
- API responses MUST use consistent status codes and error formats across all endpoints
- Configuration options MUST have sensible defaults; zero-config startup for basic use cases
- Breaking changes to CLI or API MUST follow deprecation cycle: warn → deprecate → remove
- Documentation MUST be updated synchronously with feature changes

**Rationale**: Predictable interfaces reduce user friction and support requests.

### IV. Performance Requirements

System performance MUST meet defined thresholds under normal operating conditions:

- Router request latency MUST NOT exceed 100ms p95 for local tool invocations
- Memory footprint MUST remain stable under sustained load; no memory leaks over 24h operation
- Startup time MUST remain under 5 seconds for JVM mode, under 100ms for native mode
- Database/storage operations MUST use connection pooling and batch operations where applicable
- Performance-critical paths MUST be profiled before and after significant changes
- Native builds MUST be tested for functionality parity with JVM builds

**Rationale**: AI agents depend on responsive tool execution; latency directly impacts user-perceived quality.

### V. Security by Default

Security MUST be built-in, not bolted-on:

- All external inputs MUST be validated before processing
- Secrets MUST never appear in logs, error messages, or exception traces
- Authentication MUST be enforced for all non-health-check endpoints in production
- Dependencies MUST be regularly scanned for known vulnerabilities (Dependabot, Snyk)
- Capability services MUST use least-privilege access patterns
- Container images MUST use non-root users and minimal base images

**Rationale**: As a router handling AI agent requests, Wanaku is a high-value security target.

## Quality Standards

### Code Review Requirements

- All changes MUST be submitted via pull request
- PRs MUST pass CI checks (build, test, lint) before review
- PRs MUST include tests for new functionality
- Reviewers MUST verify adherence to constitution principles
- Large changes SHOULD be broken into reviewable increments

### Definition of Done

A feature is complete when:

1. Implementation matches specification
2. Unit and integration tests pass
3. Documentation updated (if user-facing)
4. No new warnings introduced
5. Reviewed and approved by maintainer

## Development Workflow

### Branching Strategy

- `main` branch is always deployable
- Feature branches follow pattern: `ci-issue-[issue-number]`
- PRs target `main` unless backporting

### Commit Guidelines

- Commits MUST have descriptive messages explaining the change
- Related changes SHOULD be atomic (one logical change per commit)
- Commits MUST NOT break the build on `main`

### Continuous Integration

- All PRs trigger automated build and test pipeline
- Native builds tested on merge to `main`
- Container images built and tagged on release

## Governance

This constitution supersedes informal practices. All development decisions SHOULD reference these principles.

### Amendment Process

1. Propose change via issue or discussion
2. Document rationale and impact
3. Obtain maintainer approval
4. Update constitution with new version
5. Communicate changes to contributors

### Versioning Policy

- MAJOR: Principle removal or incompatible redefinition
- MINOR: New principle or section added
- PATCH: Clarifications and wording improvements

### Compliance

- Code reviews MUST verify principle adherence
- Exceptions require documented justification in PR description
- Repeated violations warrant process review

**Version**: 1.0.0 | **Ratified**: 2026-01-31 | **Last Amended**: 2026-01-31
