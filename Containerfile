# syntax=docker/dockerfile:1

# ------------------------------------------------------------------------------
# Stage 1: Build
# ------------------------------------------------------------------------------

FROM rust:1.96-alpine AS builder

ENV OPENSSL_STATIC=1

RUN apk add --no-cache musl-dev openssl-dev openssl-libs-static pkgconf cmake make g++ protoc

WORKDIR /src

# ------------------------------------------------------------------------------
# Cache Build
# ------------------------------------------------------------------------------

COPY Cargo.toml Cargo.lock ./
COPY apis/Cargo.toml apis/Cargo.toml
COPY filters/Cargo.toml filters/Cargo.toml
COPY server/Cargo.toml server/Cargo.toml

COPY apis/build.rs apis/build.rs
COPY apis/src/proto apis/src/proto

RUN mkdir -p apis/src filters/src server/src \
    && echo '//! stub' > apis/src/lib.rs \
    && echo '//! stub' > filters/src/lib.rs \
    && echo '//! stub' > server/src/lib.rs \
    && printf '//! stub\nfn main() {}\n' > server/src/main.rs

RUN --mount=type=cache,target=/usr/local/cargo/registry \
    --mount=type=cache,target=/src/target \
    cargo build --release -p wanaku-praxis-proxy

# ------------------------------------------------------------------------------
# Real Build
# ------------------------------------------------------------------------------

COPY apis/src apis/src
COPY filters/src filters/src
COPY server/src server/src

RUN find apis/src filters/src server/src \
    -name '*.rs' -exec touch {} +

RUN --mount=type=cache,target=/usr/local/cargo/registry \
    --mount=type=cache,target=/src/target \
    cargo build --release -p wanaku-praxis-proxy \
    && cp target/release/wanaku-praxis /usr/local/bin/wanaku-praxis

# ------------------------------------------------------------------------------
# Stage 2: Runtime
# ------------------------------------------------------------------------------

FROM alpine:3.23

LABEL org.opencontainers.image.source="https://github.com/wanaku-ai/wanaku-praxis" \
    org.opencontainers.image.description="Wanaku Praxis MCP proxy server" \
    org.opencontainers.image.licenses="Apache-2.0"

RUN apk add --no-cache ca-certificates \
    && addgroup -S wanaku \
    && adduser -S -G wanaku -h /nonexistent -s /sbin/nologin wanaku \
    && mkdir -p /etc/wanaku-praxis /data/registry

COPY --from=builder --chown=root:root --chmod=0555 \
    /usr/local/bin/wanaku-praxis /usr/local/bin/wanaku-praxis

RUN chown wanaku:wanaku /data/registry

USER wanaku:wanaku

WORKDIR /etc/wanaku-praxis

EXPOSE 8081 8082 9090

HEALTHCHECK --interval=5s --timeout=3s --start-period=5s \
    CMD wget -qO- http://127.0.0.1:9090/healthz || exit 1

ENTRYPOINT ["wanaku-praxis"]
