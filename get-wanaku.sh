#!/usr/bin/env bash
set -euo pipefail

REPO="wanaku-ai/wanaku"
RELEASE_TAG="${WANAKU_RELEASE:-early-access}"
INSTALL_DIR="${WANAKU_INSTALL_DIR:-$HOME/bin}"

info()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn()  { printf '\033[1;33mWARN:\033[0m %s\n' "$*"; }
error() { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

require_command() {
    command -v "$1" >/dev/null 2>&1 || error "$1 is required but was not found"
}

detect_platform() {
    local os arch
    os="$(uname -s)"
    arch="$(uname -m)"

    case "${os}/${arch}" in
        Linux/x86_64|Linux/amd64) PLATFORM="linux-x86_64" ;;
        Linux/aarch64|Linux/arm64) PLATFORM="linux-aarch64" ;;
        Darwin/x86_64|Darwin/amd64) PLATFORM="macos-x86_64" ;;
        Darwin/arm64|Darwin/aarch64) PLATFORM="macos-aarch64" ;;
        *) error "Wanaku does not provide a release for ${os}/${arch}" ;;
    esac
}

resolve_release() {
    info "Resolving Wanaku ${RELEASE_TAG} for ${PLATFORM}..."
    local response urls
    response="$(curl -fsSL "https://api.github.com/repos/${REPO}/releases/tags/${RELEASE_TAG}")" \
        || error "Could not fetch release ${RELEASE_TAG}"

    urls="$(printf '%s\n' "$response" \
        | sed -n 's/.*"browser_download_url": "\([^"]*\)".*/\1/p')"
    DOWNLOAD_URL="$(printf '%s\n' "$urls" \
        | grep -- "-${PLATFORM}\\.tar\\.gz$" \
        | head -n 1 || true)"
    CHECKSUMS_URL="$(printf '%s\n' "$urls" \
        | grep -- '/checksums_sha256\.txt$' \
        | head -n 1 || true)"

    [ -n "$DOWNLOAD_URL" ] || error "Release ${RELEASE_TAG} has no ${PLATFORM} archive"
    [ -n "$CHECKSUMS_URL" ] || error "Release ${RELEASE_TAG} has no checksum file"
    ARTIFACT="${DOWNLOAD_URL##*/}"
}

download_and_verify() {
    INSTALL_TMP="$(mktemp -d)"
    trap 'rm -rf "$INSTALL_TMP"' EXIT

    info "Downloading ${ARTIFACT}..."
    curl -fsSL -o "${INSTALL_TMP}/${ARTIFACT}" "$DOWNLOAD_URL" \
        || error "Failed to download ${ARTIFACT}"
    curl -fsSL -o "${INSTALL_TMP}/checksums_sha256.txt" "$CHECKSUMS_URL" \
        || error "Failed to download checksums"

    grep -F " ${ARTIFACT}" "${INSTALL_TMP}/checksums_sha256.txt" >/dev/null \
        || error "No checksum found for ${ARTIFACT}"

    info "Verifying SHA-256 checksum..."
    if command -v sha256sum >/dev/null 2>&1; then
        (cd "$INSTALL_TMP" && grep -F " ${ARTIFACT}" checksums_sha256.txt | sha256sum -c --quiet -)
    elif command -v shasum >/dev/null 2>&1; then
        (cd "$INSTALL_TMP" && grep -F " ${ARTIFACT}" checksums_sha256.txt | shasum -a 256 -c --quiet -)
    else
        error "sha256sum or shasum is required to verify the download"
    fi
}

install_wanaku() {
    local binary
    mkdir -p "${INSTALL_TMP}/extract" "$INSTALL_DIR"
    tar -xzf "${INSTALL_TMP}/${ARTIFACT}" -C "${INSTALL_TMP}/extract"
    binary="$(find "${INSTALL_TMP}/extract" -type f -name wanaku-server -print | head -n 1)"
    [ -n "$binary" ] || error "Unexpected archive layout: wanaku-server binary not found"

    install -m 0755 "$binary" "${INSTALL_DIR}/wanaku-server"
    info "Wanaku installed at ${INSTALL_DIR}/wanaku-server"

    if ! printf '%s' "$PATH" | tr ':' '\n' | grep -Fxq "$INSTALL_DIR"; then
        warn "$INSTALL_DIR is not in your PATH. Add it with:"
        warn "  export PATH=\"${INSTALL_DIR}:\$PATH\""
    fi

    "${INSTALL_DIR}/wanaku-server" --version 2>/dev/null || true
}

main() {
    require_command curl
    require_command tar
    require_command install
    detect_platform
    resolve_release
    download_and_verify
    install_wanaku
}

main "$@"
