# syntax=docker/dockerfile:1

# ------------------------------------------------------------------------------
# Stage 1: Build Admin UI
# ------------------------------------------------------------------------------

FROM node:22 AS ui-builder
WORKDIR /ui
COPY ui/admin/package.json ui/admin/yarn.lock ./
RUN yarn install --frozen-lockfile
COPY ui/admin/ .
RUN yarn build

# ------------------------------------------------------------------------------
# Stage 2: Build Rust binary
# ------------------------------------------------------------------------------

FROM registry.fedoraproject.org/fedora:42 AS builder

RUN dnf install -y gcc gcc-c++ openssl-devel pkgconf-pkg-config cmake make protobuf-compiler curl \
    && dnf clean all

RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --default-toolchain 1.96.0
ENV PATH="/root/.cargo/bin:${PATH}"

WORKDIR /src

# ------------------------------------------------------------------------------
# Cache Build
# ------------------------------------------------------------------------------

COPY Cargo.toml Cargo.lock ./
COPY apis/Cargo.toml apis/Cargo.toml
COPY filters/Cargo.toml filters/Cargo.toml
COPY server/Cargo.toml server/Cargo.toml
COPY features/chat/Cargo.toml features/chat/Cargo.toml
COPY features/intercept/Cargo.toml features/intercept/Cargo.toml
COPY features/mcp-metadata/Cargo.toml features/mcp-metadata/Cargo.toml
COPY features/safety/Cargo.toml features/safety/Cargo.toml

COPY apis/build.rs apis/build.rs
COPY apis/src/proto apis/src/proto

RUN mkdir -p apis/src filters/src server/src \
    features/chat/src features/intercept/src features/mcp-metadata/src features/safety/src \
    && echo '//! stub' > apis/src/lib.rs \
    && echo '//! stub' > filters/src/lib.rs \
    && echo '//! stub' > server/src/lib.rs \
    && echo '//! stub' > features/chat/src/lib.rs \
    && echo '//! stub' > features/intercept/src/lib.rs \
    && echo '//! stub' > features/mcp-metadata/src/lib.rs \
    && echo '//! stub' > features/safety/src/lib.rs \
    && printf '//! stub\nfn main() {}\n' > server/src/main.rs

RUN --mount=type=cache,target=/root/.cargo/registry \
    --mount=type=cache,target=/src/target \
    cargo build --release -p wanaku-praxis-proxy

# ------------------------------------------------------------------------------
# Real Build
# ------------------------------------------------------------------------------

COPY apis/src apis/src
COPY filters/src filters/src
COPY server/src server/src
COPY features features
COPY --from=ui-builder /ui/dist /src/ui/admin/dist

RUN find apis/src filters/src server/src features \
    -name '*.rs' -exec touch {} +

RUN --mount=type=cache,target=/root/.cargo/registry \
    --mount=type=cache,target=/src/target \
    cargo build --release -p wanaku-praxis-proxy \
    && cp target/release/wanaku-praxis /usr/local/bin/wanaku-praxis

# ------------------------------------------------------------------------------
# Stage 3: Runtime
# ------------------------------------------------------------------------------

FROM registry.fedoraproject.org/fedora-minimal:42

LABEL org.opencontainers.image.source="https://github.com/wanaku-ai/wanaku-praxis" \
    org.opencontainers.image.description="Wanaku Praxis MCP proxy server" \
    org.opencontainers.image.licenses="Apache-2.0"

RUN microdnf install -y ca-certificates shadow-utils \
    && microdnf clean all \
    && groupadd -r wanaku \
    && useradd -r -g wanaku -d /nonexistent -s /sbin/nologin wanaku \
    && mkdir -p /etc/wanaku-praxis /data/registry

COPY --from=builder --chown=root:root --chmod=0555 \
    /usr/local/bin/wanaku-praxis /usr/local/bin/wanaku-praxis

RUN chown wanaku:wanaku /data/registry

USER wanaku:wanaku

WORKDIR /etc/wanaku-praxis

EXPOSE 8080 8081 8083

HEALTHCHECK --interval=5s --timeout=3s --start-period=5s \
    CMD curl -sf http://127.0.0.1:8080/healthz || exit 1

ENTRYPOINT ["wanaku-praxis"]
