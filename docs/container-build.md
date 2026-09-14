# Container Image Build

Wanaku provides first-class support for building container images through the Cargo toolchain.
Use `cargo build-image` or `make build-image` to build the image locally, or target a specific
runtime environment with the `--target` flag.

## Prerequisites

Install the tool that matches your target environment.

| Target | Required tool | Install reference |
|---|---|---|
| `docker` (default) | `docker` (falls back to `podman`) | [Docker](https://docs.docker.com/get-docker/) / [Podman](https://podman.io/getting-started/installation) |
| `minikube` | `docker` + `minikube` | [Minikube](https://minikube.sigs.k8s.io/docs/start/) |
| `openshift` | `podman` + `oc` CLI, logged in to a cluster | [Podman](https://podman.io/getting-started/installation) / OpenShift CLI |

## Commands

### `cargo build-image`

Build the full image (includes the embedded admin UI).

```bash
cargo build-image
```

The tag is always suffixed with the host architecture, for example:
- `quay.io/wanaku/wanaku-server:latest-x86_64` on an x86_64 machine
- `quay.io/wanaku/wanaku-server:latest-aarch64` on a Linux ARM64 machine

The image is pushed to the registry after the build. Exception: the `minikube` target
loads the image into the cluster daemon directly with the plain tag, and does not push.

### `cargo build-image-headless`

Build the headless image (no embedded admin UI).

```bash
cargo build-image-headless
```

This is a shorthand for `cargo build-image --variant headless`. The same arch-suffix
behaviour applies.

### `cargo build-image-manifest`

Assemble a multi-arch manifest list from existing arch-specific images.

```bash
cargo build-image-manifest --tag quay.io/wanaku/wanaku-server:myrelease
```

This command requires that both `<tag>-aarch64` and `<tag>-x86_64` are already
in the registry. It assembles them into a manifest list at `<tag>` using
`podman manifest create` + `podman manifest push --all`.

`podman` must be on `PATH`. The `minikube` target is not supported for manifest assembly.

### Custom tag

Use `--tag` to set a custom image tag. Defaults to `quay.io/wanaku/wanaku-server:latest`.

```bash
cargo build-image --tag registry.example.com/myorg/wanaku-server:v1.2.3
# pushes: registry.example.com/myorg/wanaku-server:v1.2.3-x86_64  (on x86_64)
```

For the `openshift` target, the tag suffix (the part after the last `:`) is extracted
and used as the tag on the OpenShift registry push. For example, `--tag ...:v1.2.3`
pushes as `<registry>/<namespace>/wanaku-server:v1.2.3-<arch>`.

## Multi-arch builds

Multi-arch support uses the native two-pass approach — no QEMU emulation.
Each machine builds only its own native architecture.

**Step 1 — build and push the arch-specific image on each machine:**

```bash
# On an x86_64 machine:
cargo build-image --tag quay.io/wanaku/wanaku-server:myrelease
# pushes: quay.io/wanaku/wanaku-server:myrelease-x86_64

# On an aarch64 machine:
cargo build-image --tag quay.io/wanaku/wanaku-server:myrelease
# pushes: quay.io/wanaku/wanaku-server:myrelease-aarch64
```

**Step 2 — assemble the manifest list** (run once, on either machine, after both arch
images are in the registry):

```bash
cargo build-image-manifest --tag quay.io/wanaku/wanaku-server:myrelease
# creates manifest list: quay.io/wanaku/wanaku-server:myrelease → aarch64 + x86_64
```

The headless variant follows the same pattern:

```bash
cargo build-image-headless --tag quay.io/wanaku/wanaku-server-headless:myrelease
cargo build-image-manifest --variant headless --tag quay.io/wanaku/wanaku-server-headless:myrelease
```

### Relationship to CI

The `main-build.yml` CI workflow runs on a matrix of `ubuntu-latest` (x86_64) and
`ubuntu-24.04-arm` (aarch64) runners. Each runner calls `cargo build-image` and pushes
its arch-specific image. A separate `createManifests` job then calls
`cargo build-image-manifest` to assemble the final manifest list.

The tag pattern in CI is `<registry>/<group>/wanaku-server:<branch>`, so the
arch-specific tags become `<branch>-x86_64` and `<branch>-aarch64`, and the manifest
list is assembled at `<branch>`.

## `--target` flag

Pass `--target` to select the build environment. The default is `docker`.

```bash
cargo build-image --target docker      # default
cargo build-image --target minikube
cargo build-image --target openshift
```

Both `build-image` and `build-image-headless` accept `--target`.

```bash
cargo build-image-headless --target minikube
```

## Target details

### `docker` (default)

Runs the following command using `docker` (or `podman` if docker is not on `PATH`).

```
docker build -f Containerfile --build-arg VARIANT=full -t <tag>-<arch> .
docker push <tag>-<arch>
```

After the push, the command prints the next steps for multi-arch manifest assembly.

### `minikube`

Loads the image directly into Minikube's internal image store.
No registry push is required. The xtask runs `minikube docker-env --shell none`,
parses the returned environment variables, and injects them into the `docker build`
subprocess. You do not need to run `eval $(minikube docker-env)` manually.

The Minikube cluster must be running before you invoke this command.

```bash
minikube start          # if not already running
cargo build-image --target minikube
```

The plain tag (no arch suffix) is used so the image is immediately available to pods.

```bash
kubectl run wanaku --image=quay.io/wanaku/wanaku-server:latest --image-pull-policy=Never
```

> **Note:** `cargo build-image-manifest` does not support the `minikube` target.
> Minikube's internal daemon serves a single-arch cluster; a multi-arch manifest is not
> meaningful there.

### `openshift`

Builds the image locally with Podman, then pushes the final runtime image to the
OpenShift internal registry. The Rust compilation runs on your machine, not on the
cluster.

> **Why not `oc start-build --from-dir`?**
>
> S2I binary builds have two fundamental problems for this project:
>
> 1. **Ephemeral storage:** the Containerfile performs a full Rust compilation inside the
>    build pod. It needs >10 GB of ephemeral storage. OpenShift cluster nodes typically
>    enforce a 5–6 GB limit, so the pod is evicted before the build finishes.
> 2. **Build args ignored:** `oc start-build --from-dir` silently drops `--build-arg`.
>    The `VARIANT` arg is never forwarded to the Containerfile.
>
> The correct approach: build locally with Podman (cargo caches exist on your machine),
> then push only the final ~100 MB runtime image to the registry.

#### What the xtask does

1. Checks that you are logged in with `oc whoami`.
2. Resolves the internal registry hostname via `oc get route/default-route -n openshift-image-registry`.
3. Resolves the current namespace with `oc project -q`.
4. Obtains the auth token with `oc whoami -t`.
5. Logs `podman` into the registry (`podman login --tls-verify=false`).
6. Builds the image locally with `podman`, tagging it with both the local `--tag` and the
   registry reference `<registry>/<namespace>/wanaku-server:<tag-suffix>-<arch>`.
7. Pushes only the final runtime image to the OpenShift registry (`podman push --tls-verify=false`).
8. Prints the full image reference for use in a `Deployment` or `oc new-app`.

Podman is required (not Docker) because it supports `--tls-verify=false` on both
`login` and `push`, which is needed when the internal registry uses a self-signed
certificate.

#### Usage

You must be logged in and have the internal registry exposed.

```bash
# Expose the internal registry (one-time cluster admin step)
oc patch configs.imageregistry.operator.openshift.io/cluster \
  --patch '{"spec":{"defaultRoute":true}}' --type=merge

# Log in and build
oc login <cluster-url>
cargo build-image --target openshift
```

The xtask prints the image reference at the end:

```
Registry reference : default-route-openshift-image-registry.apps.example.com/myproject/wanaku-server:latest-x86_64
```

To push a specific version tag:

```bash
cargo build-image --target openshift --tag quay.io/wanaku/wanaku-server:v0.3.0
# pushes as: <registry>/<namespace>/wanaku-server:v0.3.0-<arch>
```

To assemble a multi-arch manifest after both arch images are pushed:

```bash
cargo build-image-manifest --target openshift --tag quay.io/wanaku/wanaku-server:v0.3.0
```

## Make targets

Both commands are also available as Make targets. Use the `TARGET` variable to select
the environment.

```bash
make build-image                        # docker, full variant
make build-image TARGET=minikube        # minikube, full variant
make build-image TARGET=openshift       # openshift, full variant

make build-image-headless               # docker, headless variant
make build-image-headless TARGET=minikube
```
