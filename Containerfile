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
COPY features/evaluator/Cargo.toml features/evaluator/Cargo.toml
COPY features/intercept/Cargo.toml features/intercept/Cargo.toml
COPY features/mcp-metadata/Cargo.toml features/mcp-metadata/Cargo.toml
COPY features/metrics/Cargo.toml features/metrics/Cargo.toml
COPY features/plugins/Cargo.toml features/plugins/Cargo.toml

RUN mkdir -p types/src infra/src filters/src server/src \
    features/evaluator/src features/intercept/src features/mcp-metadata/src features/metrics/src features/plugins/src \
    ui/admin/dist \
    && echo '//! stub' > types/src/lib.rs \
    && echo '//! stub' > infra/src/lib.rs \
    && echo '//! stub' > filters/src/lib.rs \
    && echo '//! stub' > server/src/lib.rs \
    && echo '//! stub' > features/evaluator/src/lib.rs \
    && echo '//! stub' > features/intercept/src/lib.rs \
    && echo '//! stub' > features/mcp-metadata/src/lib.rs \
    && echo '//! stub' > features/metrics/src/lib.rs \
    && echo '//! stub' > features/plugins/src/lib.rs \
    && printf '//! stub\nfn main() {}\n' > server/src/main.rs

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
COPY --from=ui-builder /ui/dist /src/ui/admin/dist

RUN find types/src infra/src filters/src server/src features \
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

RUN microdnf install -y ca-certificates shadow-utils \
    && microdnf clean all \
    && groupadd -r wanaku \
    && useradd -r -g wanaku -d /nonexistent -s /sbin/nologin wanaku \
    && mkdir -p /etc/wanaku /data/registry

COPY --from=builder --chown=root:root --chmod=0555 \
    /usr/local/bin/wanaku-server /usr/local/bin/wanaku-server

RUN chown wanaku:wanaku /data/registry

USER wanaku:wanaku

WORKDIR /etc/wanaku

EXPOSE 8080 8081 8083

HEALTHCHECK --interval=5s --timeout=3s --start-period=5s \
    CMD curl -sf http://127.0.0.1:8080/healthz || exit 1

ENTRYPOINT ["wanaku-server"]
