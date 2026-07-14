#!/usr/bin/env bash
set -euo pipefail

REPO="wanaku-ai/wanaku"
INSTALL_DIR="${WANAKU_INSTALL_DIR:-$HOME/bin}"

info()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn()  { printf '\033[1;33mWARN:\033[0m %s\n' "$*"; }
error() { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

# Minimum Java version required to run Wanaku.
REQUIRED_JAVA_VERSION=21

check_java_version() {
    if ! command -v java >/dev/null 2>&1; then
        error "Java is not installed. Wanaku requires Java ${REQUIRED_JAVA_VERSION} or later. Please install Java ${REQUIRED_JAVA_VERSION}+ and try again."
    fi

    local version_output major
    version_output="$(java -version 2>&1)"

    # Parse the major version from strings like:
    #   openjdk version "21.0.2" 2024-01-16   -> 21
    #   java version "1.8.0_392"              -> 8 (legacy 1.x scheme)
    major="$(echo "$version_output" | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"

    if [ "$major" = "1" ]; then
        major="$(echo "$version_output" | head -1 | sed -E 's/.*version "1\.([0-9]+).*/\1/')"
    fi

    if [ -z "$major" ] || ! printf '%s' "$major" | grep -Eq '^[0-9]+$'; then
        error "Could not determine the installed Java version. Wanaku requires Java ${REQUIRED_JAVA_VERSION} or later."
    fi

    if [ "$major" -lt "$REQUIRED_JAVA_VERSION" ]; then
        error "Java $major detected, but Wanaku requires Java ${REQUIRED_JAVA_VERSION} or later. Please upgrade your Java installation and try again."
    fi

    info "Detected Java $major (>= ${REQUIRED_JAVA_VERSION})"
}

detect_platform() {
    local os arch
    os="$(uname -s)"
    arch="$(uname -m)"

    case "$os" in
        Darwin)
            case "$arch" in
                arm64|aarch64) PLATFORM="osx-aarch_64" ;;
                *) PLATFORM="" ;;
            esac
            ;;
        Linux)
            case "$arch" in
                x86_64|amd64) PLATFORM="linux-x86_64" ;;
                *) PLATFORM="" ;;
            esac
            ;;
        *)
            PLATFORM=""
            ;;
    esac

    if [ -z "$PLATFORM" ]; then
        if command -v java >/dev/null 2>&1; then
            info "No native binary for $os/$arch — falling back to Java-based archive"
            PLATFORM="java"
        else
            error "No native binary for $os/$arch and Java is not installed"
        fi
    fi
}

fetch_latest_version() {
    info "Fetching latest release version..."
    local response
    response="$(curl -fsL "https://api.github.com/repos/${REPO}/releases/latest")" \
        || error "Could not fetch release info. Check your internet connection or GitHub API rate limits."

    if command -v jq >/dev/null 2>&1; then
        VERSION="$(echo "$response" | jq -r '.tag_name')"
    else
        VERSION="$(echo "$response" | grep '"tag_name"' | sed -E 's/.*"tag_name": *"([^"]+)".*/\1/')"
    fi

    if [ -z "$VERSION" ]; then
        error "Could not determine latest version. Check your internet connection or GitHub API rate limits."
    fi

    VERSION_NUM="${VERSION#v}"
    info "Latest version: $VERSION"
}

build_download_url() {
    local tag="${VERSION}"
    if echo "$VERSION_NUM" | grep -q 'SNAPSHOT'; then
        tag="early-access"
    fi
    local base="https://github.com/${REPO}/releases/download/${tag}"

    if [ "$PLATFORM" = "java" ]; then
        ARTIFACT="wanaku-cli-${VERSION_NUM}.zip"
    else
        ARTIFACT="wanaku-cli-${VERSION_NUM}-${PLATFORM}.zip"
    fi

    DOWNLOAD_URL="${base}/${ARTIFACT}"
    CHECKSUMS_URL="${base}/checksums_sha256.txt"
}

download_and_verify() {
    TMPDIR="$(mktemp -d)"
    trap 'rm -rf "$TMPDIR"' EXIT

    info "Downloading $ARTIFACT..."
    curl -fsL -o "${TMPDIR}/${ARTIFACT}" "$DOWNLOAD_URL" \
        || error "Failed to download $ARTIFACT"

    info "Downloading checksums..."
    curl -fsL -o "${TMPDIR}/checksums_sha256.txt" "$CHECKSUMS_URL" \
        || error "Failed to download checksums"

    if grep -Fq " $ARTIFACT" "${TMPDIR}/checksums_sha256.txt" 2>/dev/null; then
        info "Verifying SHA-256 checksum..."
        cd "$TMPDIR"
        if command -v sha256sum >/dev/null 2>&1; then
            grep -F " $ARTIFACT" checksums_sha256.txt | sha256sum -c --quiet -
        elif command -v shasum >/dev/null 2>&1; then
            grep -F " $ARTIFACT" checksums_sha256.txt | shasum -a 256 -c --quiet -
        else
            warn "No sha256sum or shasum found — skipping checksum verification"
        fi
        cd - >/dev/null
    else
        warn "No checksum entry for $ARTIFACT — skipping verification"
    fi
}

install_wanaku() {
    info "Extracting to ${INSTALL_DIR}..."
    mkdir -p "$INSTALL_DIR"

    unzip -qo "${TMPDIR}/${ARTIFACT}" -d "$TMPDIR"

    if [ "$PLATFORM" = "java" ]; then
        install_java
    else
        install_native
    fi

    if ! echo "$PATH" | tr ':' '\n' | grep -Fxq "$INSTALL_DIR"; then
        warn "$INSTALL_DIR is not in your PATH. Add it with:"
        warn "  export PATH=\"${INSTALL_DIR}:\$PATH\""
    fi
}

install_native() {
    local bin_dir
    bin_dir="$(find "$TMPDIR" -type d -name bin | head -1)"

    if [ -z "$bin_dir" ]; then
        error "Unexpected archive layout — no bin/ directory found"
    fi

    for f in wanaku wanaku-cli; do
        if [ -f "${bin_dir}/${f}" ]; then
            install -m 750 "${bin_dir}/${f}" "${INSTALL_DIR}/${f}"
        fi
    done
}

install_java() {
    local extract_dir
    extract_dir="$(find "$TMPDIR" -maxdepth 1 -type d -name "wanaku-cli-*" | head -1)"

    if [ -z "$extract_dir" ] || [ ! -f "${extract_dir}/quarkus-run.jar" ]; then
        error "Unexpected Java archive layout — quarkus-run.jar not found"
    fi

    local java_home="${INSTALL_DIR}/wanaku-java"
    rm -rf "$java_home"
    cp -R "$extract_dir" "$java_home"

    cat > "${INSTALL_DIR}/wanaku" <<'WRAPPER'
#!/bin/sh
# --add-opens is included unconditionally so the installed wrapper works across
# all supported Java versions (21+), including Java 25 which tightened
# strong encapsulation of java.lang internals. The flag is idempotent on JVMs
# where the package is already open.
installDir="$(dirname "$0")/wanaku-java"
exec java --add-opens=java.base/java.lang=ALL-UNNAMED -jar "${installDir}/quarkus-run.jar" "$@"
WRAPPER
    chmod 750 "${INSTALL_DIR}/wanaku"

    cat > "${INSTALL_DIR}/wanaku-cli" <<'WRAPPER'
#!/bin/sh
# --add-opens is included unconditionally so the installed wrapper works across
# all supported Java versions (21+), including Java 25 which tightened
# strong encapsulation of java.lang internals. The flag is idempotent on JVMs
# where the package is already open.
installDir="$(dirname "$0")/wanaku-java"
exec java --add-opens=java.base/java.lang=ALL-UNNAMED -jar "${installDir}/quarkus-run.jar" "$@"
WRAPPER
    chmod 750 "${INSTALL_DIR}/wanaku-cli"
}

main() {
    check_java_version
    detect_platform
    fetch_latest_version
    build_download_url
    download_and_verify
    install_wanaku
    info "Wanaku CLI installed successfully!"
    "${INSTALL_DIR}/wanaku" --version 2>/dev/null || true
}

main "$@"
