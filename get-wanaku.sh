#!/usr/bin/env bash
set -euo pipefail

REPO="wanaku-ai/wanaku"
INSTALL_DIR="${WANAKU_INSTALL_DIR:-$HOME/bin}"

info()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn()  { printf '\033[1;33mWARN:\033[0m %s\n' "$*"; }
error() { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

detect_platform() {
    local os arch
    os="$(uname -s)"
    arch="$(uname -m)"

    case "$os" in
        Darwin)
            case "$arch" in
                arm64|aarch64) PLATFORM="osx-aarch_64" ;;
                *)             PLATFORM="" ;;
            esac
            ;;
        Linux)
            case "$arch" in
                x86_64|amd64) PLATFORM="linux-x86_64" ;;
                *)            PLATFORM="" ;;
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
    VERSION="$(curl -sL "https://api.github.com/repos/${REPO}/releases/latest" \
        | grep '"tag_name"' \
        | sed -E 's/.*"tag_name": *"([^"]+)".*/\1/')"

    if [ -z "$VERSION" ]; then
        error "Could not determine latest version. Check your internet connection or GitHub API rate limits."
    fi

    VERSION_NUM="${VERSION#v}"
    info "Latest version: $VERSION"
}

build_download_url() {
    local base="https://github.com/${REPO}/releases/download/${VERSION}"

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
    curl -sL -o "${TMPDIR}/${ARTIFACT}" "$DOWNLOAD_URL"

    info "Downloading checksums..."
    curl -sL -o "${TMPDIR}/checksums_sha256.txt" "$CHECKSUMS_URL"

    if grep -q "$ARTIFACT" "${TMPDIR}/checksums_sha256.txt" 2>/dev/null; then
        info "Verifying SHA-256 checksum..."
        cd "$TMPDIR"
        if command -v sha256sum >/dev/null 2>&1; then
            grep "$ARTIFACT" checksums_sha256.txt | sha256sum -c --quiet -
        elif command -v shasum >/dev/null 2>&1; then
            grep "$ARTIFACT" checksums_sha256.txt | shasum -a 256 -c --quiet -
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

    if ! echo "$PATH" | tr ':' '\n' | grep -qx "$INSTALL_DIR"; then
        warn "$INSTALL_DIR is not in your PATH. Add it with:"
        warn "  export PATH=\"${INSTALL_DIR}:\$PATH\""
    fi
}

main() {
    detect_platform
    fetch_latest_version
    build_download_url
    download_and_verify
    install_wanaku
    info "Wanaku CLI installed successfully!"
    "${INSTALL_DIR}/wanaku" --version 2>/dev/null || true
}

main "$@"
