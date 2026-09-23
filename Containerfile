# syntax=docker/dockerfile:1

ARG VARIANT=full

# ------------------------------------------------------------------------------
# Stage 1: Build Admin UI (or empty stub for headless)
# ------------------------------------------------------------------------------

FROM node:22 AS ui-builder-full
WORKDIR /ui
COPY ui/admin/package.json ui/admin/yarn.lock ./
RUN yarn install --frozen-lockfile
COPY ui/admin/ .
RUN yarn build

FROM busybox AS ui-builder-headless
RUN mkdir -p /ui/dist

FROM ui-builder-${VARIANT} AS ui-builder

# ------------------------------------------------------------------------------
# Stage 2: Build Rust binary
# ------------------------------------------------------------------------------

FROM registry.fedoraproject.org/fedora:44 AS builder
ARG VARIANT=full

RUN dnf install -y gcc gcc-c++ openssl-devel pkgconf-pkg-config cmake make curl \
    && dnf clean all

RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --default-toolchain 1.96.0
ENV PATH="/root/.cargo/bin:${PATH}"

WORKDIR /src

# ------------------------------------------------------------------------------
# Cache Build
# ------------------------------------------------------------------------------

COPY Cargo.toml Cargo.lock ./
COPY types/Cargo.toml types/Cargo.toml
COPY infra/Cargo.toml infra/Cargo.toml
COPY filters/Cargo.toml filters/Cargo.toml
COPY server/Cargo.toml server/Cargo.toml
COPY features/action-policy/Cargo.toml features/action-policy/Cargo.toml
COPY features/audit/Cargo.toml features/audit/Cargo.toml
COPY features/evaluator/Cargo.toml features/evaluator/Cargo.toml
COPY features/intercept/Cargo.toml features/intercept/Cargo.toml
COPY features/mcp-metadata/Cargo.toml features/mcp-metadata/Cargo.toml
COPY features/metrics/Cargo.toml features/metrics/Cargo.toml
COPY features/plugins/Cargo.toml features/plugins/Cargo.toml
COPY xtask/Cargo.toml xtask/Cargo.toml

RUN mkdir -p types/src infra/src filters/src server/src \
    features/action-policy/src features/audit/src features/evaluator/src features/intercept/src features/mcp-metadata/src features/metrics/src features/plugins/src \
    xtask/src \
    ui/admin/dist \
    && echo '//! stub' > types/src/lib.rs \
    && echo '//! stub' > infra/src/lib.rs \
    && echo '//! stub' > filters/src/lib.rs \
    && echo '//! stub' > server/src/lib.rs \
    && echo '//! stub' > features/action-policy/src/lib.rs \
    && echo '//! stub' > features/audit/src/lib.rs \
    && echo '//! stub' > features/evaluator/src/lib.rs \
    && echo '//! stub' > features/intercept/src/lib.rs \
    && echo '//! stub' > features/mcp-metadata/src/lib.rs \
    && echo '//! stub' > features/metrics/src/lib.rs \
    && echo '//! stub' > features/plugins/src/lib.rs \
    && printf '//! stub\nfn main() {}\n' > server/src/main.rs \
    && printf '//! stub\nfn main() {}\n' > xtask/src/main.rs

RUN --mount=type=cache,target=/root/.cargo/registry \
    --mount=type=cache,target=/src/target \
    if [ "$VARIANT" = "headless" ]; then \
      cargo build --release -p wanaku-server --no-default-features; \
    else \
      cargo build --release -p wanaku-server; \
    fi

# ------------------------------------------------------------------------------
# Real Build
# ------------------------------------------------------------------------------

COPY types/src types/src
COPY infra/src infra/src
COPY filters/src filters/src
COPY server/src server/src
COPY features features
COPY xtask/src xtask/src
COPY --from=ui-builder /ui/dist /src/ui/admin/dist

RUN find types/src infra/src filters/src server/src features xtask/src \
    -name '*.rs' -exec touch {} +

RUN --mount=type=cache,target=/root/.cargo/registry \
    --mount=type=cache,target=/src/target \
    if [ "$VARIANT" = "headless" ]; then \
      cargo build --release -p wanaku-server --no-default-features; \
    else \
      cargo build --release -p wanaku-server; \
    fi \
    && cp target/release/wanaku-server /usr/local/bin/wanaku-server

# ------------------------------------------------------------------------------
# Stage 3: Runtime
# ------------------------------------------------------------------------------

FROM registry.fedoraproject.org/fedora-minimal:44

LABEL org.opencontainers.image.source="https://github.com/wanaku-ai/wanaku" \
    org.opencontainers.image.description="Wanaku MCP proxy server" \
    org.opencontainers.image.licenses="Apache-2.0"

# OpenShift runs pods under a namespace-allocated UID (> 1000000000) that is
# always in the root group (GID 0).  To satisfy the restricted-v2 SCC we must:
#   1. Not hard-code a UID — OpenShift injects one at runtime.
#   2. Grant group-write (GID 0) access to every directory the process writes.
#   3. Not set fsGroup / runAsUser in the image — let OpenShift assign them.
RUN microdnf install -y ca-certificates \
    && microdnf clean all \
    && mkdir -p /etc/wanaku /data/registry \
    && chown -R 0:0 /etc/wanaku /data/registry \
    && chmod -R g=u  /etc/wanaku /data/registry

ENV WANAKU_PERSIST_PATH=/data/registry

COPY --from=builder --chown=root:root --chmod=0555 \
    /usr/local/bin/wanaku-server /usr/local/bin/wanaku-server

# Non-zero UID satisfies "must not run as root" policies; GID 0 is the
# OpenShift-compatible group.  The actual UID is overridden at runtime.
USER 1001:0

WORKDIR /etc/wanaku

EXPOSE 8080 8081 8083

HEALTHCHECK --interval=5s --timeout=3s --start-period=5s \
    CMD curl -sf http://127.0.0.1:8080/healthz || exit 1

ENTRYPOINT ["wanaku-server"]
