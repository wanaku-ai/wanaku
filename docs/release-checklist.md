# Wanaku Release Checklist

This checklist covers the full multi-repository release process. Repositories must be released in
a specific order due to dependency chains.

## Release Order

```text
1. wanaku-capabilities-java-sdk    (no upstream deps)
2. wanaku                          (depends on SDK)
3. camel-integration-capability    (depends on SDK + Wanaku)
4. wanaku-demos                    (depends on SDK + Wanaku; docs reference specific versions)
5. wanaku-docs                     (depends on all of the above)
```

---

## Phase 0: Pre-Release Preparation

### Version Planning

- [ ] Determine the release version for each repository:
  - [ ] `wanaku-capabilities-java-sdk`: ______
  - [ ] `wanaku`: ______
  - [ ] `camel-integration-capability`: ______
  - [ ] `wanaku-demos`: ______
- [ ] Determine the next development (SNAPSHOT) version for each repository
- [ ] Confirm all CI builds are green on `main` for every repository

### Dependency Audit

- [ ] Verify `wanaku/parent/pom.xml` has the correct `wanaku-capabilities-sdk.version`
  property pointing to the SDK version being released (not a SNAPSHOT)
- [ ] Verify `camel-integration-capability/pom.xml` has the correct SDK version
- [ ] Check for any other SNAPSHOT dependencies that should be released first

---

## Phase 1: Release `wanaku-capabilities-java-sdk`

### Trigger

```shell
export SDK_VERSION=<version>
export SDK_NEXT_VERSION=<next-version>
gh workflow run release -R wanaku-ai/wanaku-capabilities-java-sdk \
  -f currentDevelopmentVersion=${SDK_VERSION} \
  -f nextDevelopmentVersion=${SDK_NEXT_VERSION}
```

### Verify

- [ ] GitHub Actions workflow completed successfully
- [ ] Tag `wanaku-capabilities-java-sdk-${SDK_VERSION}` exists
- [ ] GitHub Release `v${SDK_VERSION}` was created
- [ ] Artifacts appear on [Maven Central](https://central.sonatype.com/search?q=ai.wanaku.sdk)
  (can take up to 30 minutes)
- [ ] javadoc.io caches are warm (automated by workflow)

---

## Phase 2: Release `wanaku`

### Pre-Release Checks

- [ ] Update `wanaku-capabilities-sdk.version` in `parent/pom.xml` to the released
  SDK version (if not already done)
- [ ] Run a full build: `mvn verify`
- [ ] Confirm all tests pass

### Helm Chart (commonly forgotten)

- [ ] Build the operator module: the Helm chart is generated in
  `apps/wanaku-operator/target/helm/kubernetes/wanaku-operator/`
- [ ] Copy the generated CRDs from `apps/wanaku-operator/target/kubernetes/` to
  `apps/wanaku-operator/deploy/helm/wanaku-operator/crds/`
- [ ] Update `apps/wanaku-operator/deploy/helm/wanaku-operator/Chart.yaml` version
  to match the release version (remove `-SNAPSHOT`)
- [ ] Update `app.kubernetes.io/version` labels in **all** Helm templates -- these are
  hardcoded and not auto-updated by Maven. Affected files:
  - `templates/deployment.yaml` (3 occurrences)
  - `templates/clusterrole.yaml` (4 occurrences)
  - `templates/clusterrolebinding.yaml` (3 occurrences)
  - `templates/service.yaml` (2 occurrences)
  - `templates/serviceaccount.yaml` (1 occurrence)
  - `templates/rolebinding.yaml` (1 occurrence)
- [ ] Verify the Helm chart values reference the correct container image tag in
  `apps/wanaku-operator/deploy/helm/wanaku-operator/values.yaml`
- [ ] Commit any Helm chart changes before triggering the release

### Service Templates

The service templates (`services/service-templates/src/main/services/`) use versionless
Maven coordinates in their `*.dependencies.txt` files, so they do not require version
updates for a Wanaku release. However:

- [ ] Verify no `*.dependencies.txt` file has a hardcoded SNAPSHOT version:

  ```shell
  grep -r "SNAPSHOT" services/service-templates/src/main/services/
  ```

- [ ] Confirm the service-templates ZIP is included in the JReleaser distributions
  (defined in `jreleaser.yml` under `service-templates`)

### Trigger Maven Release

```shell
export PREVIOUS_VERSION=<previous-version>
export CURRENT_VERSION=<version>
export NEXT_VERSION=<next-version>
gh workflow run release \
  -f previousDevelopmentVersion=${PREVIOUS_VERSION} \
  -f currentDevelopmentVersion=${CURRENT_VERSION} \
  -f nextDevelopmentVersion=${NEXT_VERSION}
```

This workflow will:

1. Run `mvn release:prepare` (updates all pom.xml versions, commits auto-generated files)
2. Update `jbang-catalog.json` version reference
3. Recreate the git tag at the correct commit (`HEAD~2`)
4. Run `mvn -Pdist release:perform` to publish to Maven Central
5. Warm javadoc.io caches

### Auto-Committed Files

The `commitFiles` profile auto-commits these version-sensitive files during release:

- `apps/ui/admin/**/*.ts` (generated TypeScript API types)
- `apps/wanaku-router-backend/src/main/webui/openapi.json`
- `apps/wanaku-router-backend/src/main/webui/openapi.yaml`
- `jbang-catalog.json`

### Verify Maven Release

- [ ] GitHub Actions workflow completed successfully
- [ ] Tag `wanaku-${CURRENT_VERSION}` exists and points to the correct commit
- [ ] Artifacts appear on [Maven Central](https://central.sonatype.com/search?q=ai.wanaku)
- [ ] `jbang-catalog.json` references the new version

### Trigger Artifact Release

Wait for Maven Central artifacts to be available (~30 minutes), then:

```shell
gh workflow run release-artifacts -f currentDevelopmentVersion=${CURRENT_VERSION}
```

This workflow will:

1. Build native executables (Linux x86_64, Linux aarch64, macOS aarch64)
2. Build and push container images to `quay.io/wanaku/` with platform-specific tags
3. Run JReleaser to create GitHub Release with native binaries and ZIP distributions
4. Create multi-arch container manifests via `build-manifests.sh`

### Verify Artifact Release

- [ ] GitHub Release page has all expected assets:
  - [ ] CLI: native binaries (Linux x86_64, macOS aarch64) + Java ZIP
  - [ ] Router backend ZIP
  - [ ] Tool services ZIPs (http, exec)
  - [ ] Service templates ZIP
  - [ ] Performance tooling ZIPs (noop, static-file)
- [ ] Container images available on quay.io:
  - [ ] `quay.io/wanaku/wanaku-router-backend:${CURRENT_VERSION}`
  - [ ] `quay.io/wanaku/wanaku-tool-service-http:${CURRENT_VERSION}`
  - [ ] `quay.io/wanaku/wanaku-tool-service-exec:${CURRENT_VERSION}`
  - [ ] `quay.io/wanaku/wanaku-operator:${CURRENT_VERSION}`
  - [ ] `quay.io/wanaku/wanaku-tool-performance-noop:${CURRENT_VERSION}`
  - [ ] `quay.io/wanaku/wanaku-provider-performance-static-file:${CURRENT_VERSION}`
- [ ] Multi-arch manifests are published (verify with `podman manifest inspect`)
- [ ] JBang installation works: `jbang app install wanaku@wanaku-ai/wanaku`

### Post-Release

- [ ] Verify the Helm chart `Chart.yaml` version was bumped to the next SNAPSHOT
  in the development branch

---

## Phase 3: Release `camel-integration-capability`

### Pre-Release Checks

- [ ] Update the SDK version in `pom.xml` to the released SDK version
- [ ] Confirm the wanaku version dependency (if any) points to the released version
- [ ] Run full build: `mvn verify`
- [ ] Confirm all tests pass

### Trigger

```shell
export CIC_VERSION=<version>
export CIC_NEXT_VERSION=<next-version>
gh workflow run release -R wanaku-ai/camel-integration-capability \
  -f currentDevelopmentVersion=${CIC_VERSION} \
  -f nextDevelopmentVersion=${CIC_NEXT_VERSION}
```

### Trigger Artifact Release

```shell
gh workflow run release-artifacts -R wanaku-ai/camel-integration-capability \
  -f currentDevelopmentVersion=${CIC_VERSION}
```

### Verify

- [ ] GitHub Actions workflows completed successfully
- [ ] GitHub Release created
- [ ] Container image published to `quay.io/wanaku/camel-integration-capability:${CIC_VERSION}`
- [ ] Multi-arch manifests created

---

## Phase 4: Release `wanaku-demos`

### Pre-Release Version Updates

The demos reference specific versions of the SDK and CLI in documentation and pom.xml files.
These are **not** auto-updated -- they must be changed manually.

- [ ] Update SDK version in demo pom.xml files:
  - `4.02-exposing-existing-routes/sample-routes/camel-core-examples/cat-facts-example/pom.xml`
    (`capabilities-runtime-camel-plugin` version)
- [ ] Update SDK archetype version references in READMEs:
  - `4.01-plain-java-capability/README.md` (`-DarchetypeVersion=` and `-Dwanaku-sdk-version=`)
  - `4.02-exposing-existing-routes/.../cat-facts-jbang-example/README.md`
    (`capabilities-runtime-camel-plugin:X.Y.Z`)
- [ ] Update Wanaku CLI download links in READMEs:
  - `2.01-introduction-to-capabilities/README.md`
  - `2.02-service-catalogs/README.md`
- [ ] Update the version table in the root `README.md`
- [ ] Verify no SNAPSHOT versions remain:

  ```shell
  grep -r "SNAPSHOT" --include="*.md" --include="*.xml" .
  ```

  (demo pom.xml files intentionally use `1.0-SNAPSHOT` for the demo project itself -- only
  check that **wanaku dependency versions** are not SNAPSHOT)

### Trigger

The release workflow only creates a git tag (no Maven publish or artifact build):

```shell
export DEMOS_VERSION=<version>
gh workflow run release -R wanaku-ai/wanaku-demos \
  -f currentDevelopmentVersion=${DEMOS_VERSION}
```

### Verify

- [ ] Tag `wanaku-demos-${DEMOS_VERSION}` exists
- [ ] Demo instructions work against the released Wanaku version

---

## Phase 5: Release `wanaku-docs`

### Update Versions in Makefile

The docs repository aggregates content from all other repositories by cloning specific
version branches. The versions are **hardcoded in the Makefile** and must be updated manually.

- [ ] Update the Wanaku version branch references (e.g., `wanaku-${CURRENT_VERSION}`)
- [ ] Update the SDK version branch references
- [ ] Update the camel-integration-capability version branch references
- [ ] Update the wanaku-demos version references (if a demos release was made)
- [ ] Update the homepage (`index.md`) version references

### Trigger

- [ ] Push changes to `main` (auto-triggers deploy workflow), or:

  ```shell
  gh workflow run deploy -R wanaku-ai/wanaku-docs
  ```

### Verify

- [ ] Docs build completed successfully
- [ ] <https://wanaku.ai/docs> reflects the new version
- [ ] Version-specific pages are accessible (e.g., `/version/wanaku-${CURRENT_VERSION}/`)
- [ ] Download links on the homepage point to the correct release artifacts

---

## Phase 6: Announcements & Cleanup

- [ ] Verify all GitHub Releases have proper changelogs
- [ ] Announce the release (blog post, social media, community channels)
- [ ] Close the GitHub milestone (if one was used)
- [ ] Update any open issues that were resolved in this release

---

## Common Pitfalls

| Problem | Cause | Prevention |
|---------|-------|------------|
| Helm chart has stale CRDs | CRDs are auto-generated in `target/kubernetes/` but not copied to `deploy/helm/.../crds/` | Always rebuild operator and copy CRDs before release |
| Helm `Chart.yaml` still shows SNAPSHOT | Version is not auto-updated by Maven release plugin | Manually update before triggering release |
| `jbang-catalog.json` has wrong version | `sed` replacement uses `previousDevelopmentVersion` which must match exactly | Double-check the previous version parameter |
| Container manifests missing | `release-artifacts` workflow failed on one platform | Check all matrix jobs; re-run failed platform |
| SDK not found on Maven Central | Published but not yet indexed | Wait ~30 minutes; check [staging](https://central.sonatype.com/publishing/deployments) |
| Docs show old version | Makefile version refs not updated | Update Makefile before triggering docs deploy |
| `camel-integration-capability` build fails | SDK SNAPSHOT dependency not yet released | Always release SDK first and update the version |
| OpenAPI spec version mismatch | `openapi.json` in webui not regenerated | Run `mvn verify` before release to regenerate |
| Helm templates have SNAPSHOT in `app.kubernetes.io/version` | 14 hardcoded version labels across 6 template files, not managed by Maven | `grep -r SNAPSHOT apps/wanaku-operator/deploy/helm/` before release |
| Service template ships with SNAPSHOT dep | A `*.dependencies.txt` may have a pinned SNAPSHOT version | `grep -r SNAPSHOT services/service-templates/src/main/services/` before release |
| Demos reference old SDK version | pom.xml and README files have hardcoded SDK versions | Update all version refs in `wanaku-demos` before tagging |
| Demos CLI download links stale | README files link to a specific CLI release tag | Update download URLs in `2.01-*` and `2.02-*` READMEs |
| `version.txt` stale in Makefile | Reads from `target/` which requires a build | Run `mvn install` before using Makefile targets |
