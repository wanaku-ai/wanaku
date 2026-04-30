# wanaku Development Guidelines

Last updated: 2026-04-30

## Active Technologies

- Java 21 (Quarkus), TypeScript (React 19 + Vite), Picocli, Carbon React, Orval, java.util.zip
- Kubernetes Operator: Java Operator SDK (JOSDK) with Quarkus extension
- Authentication: Keycloak (OIDC), with `noauth` profile for unauthenticated access
- Persistence: Infinispan (soft index data store)

## Project Structure

```text
apps/
  wanaku-cli/              # CLI (Picocli + Quarkus)
  wanaku-router-backend/   # Router backend (Quarkus REST)
  wanaku-operator/         # Kubernetes operator (JOSDK)
  wanaku-jbang/            # JBang integration
  ui/admin/                # Admin UI (React + Vite + Carbon)
archetypes/                # Maven archetypes for tools/providers
capabilities/              # Built-in capability services
capabilities-quarkus-sdk/  # Quarkus SDK for capabilities
core/
  core-util/               # Shared utilities
  core-exchange/           # Message exchange types
  core-services-api/       # Service API interfaces
  core-service-discovery/  # Service discovery (capabilities)
  core-mcp-client/         # MCP client (router)
deploy/                    # Deployment scripts and configs
docs/                      # Documentation
tests/                     # Integration, MCP server, and load tests
```

## Commands

```shell
# Full build (from root)
mvn verify

# Build with coverage
mvn verify -Pcoverage

# Frontend only (from apps/ui/admin/)
npm run dev          # Dev server
npm run build        # Production build (Orval + TypeScript + Vite)
npm run lint         # ESLint

# Native CLI build
make cli-native

# Install CLI to ~/bin
make install
```

## Code Style

- Java: Follow standard Quarkus conventions, no Records or Lombok unless already present
- TypeScript: ESLint enforced, Carbon Design System components
- REST API responses use `WanakuResponse<T>` wrapper: `{"data": ..., "error": ...}`
- Frontend `customFetch` wraps responses again, so actual data is at `result.data.data`

## Operator

- CRDs: `WanakuRouter`, `WanakuCapability`, `WanakuServiceCatalog` (all `wanaku.ai/v1alpha1`)
- Helm chart: `apps/wanaku-operator/deploy/helm/wanaku-operator/`
- When adding new CRDs, RBAC rules must be added to the Helm chart (see `docs/contributing.md`)
- CRD manifests are auto-generated during build in `target/kubernetes/`, must be copied to `crds/`
- Router internal URL pattern: `http://internal-{routerRef}:8080`

## Service Catalogs

- CLI workflow: `init` -> `expose` -> `package` -> `deploy`
- `wanaku service package` creates Base64-encoded ZIP for operator/ConfigMap use
- `wanaku service deploy` packages and deploys directly to router REST API
- Route ID extraction uses indentation-aware parsing (route-level only, ignores step-level IDs)

<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
