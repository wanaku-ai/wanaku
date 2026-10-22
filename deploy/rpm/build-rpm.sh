#!/usr/bin/env bash
# Builds the Wanaku server RPM from a GitHub release archive.
#
# Usage: deploy/rpm/build-rpm.sh [release-tag]
#
# The default release tag is v<version>. Use "early-access" for the rolling
# early-access release. The RPMs are written to deploy/rpm/build/RPMS.
set -euo pipefail

rpm_dir="$(cd "$(dirname "$0")" && pwd)"
spec="${rpm_dir}/wanaku.spec"
version="$(rpmspec -q --srpm --qf '%{version}' "$spec")"
release_tag="${1:-v${version}}"
topdir="${rpm_dir}/build"
sources="${topdir}/SOURCES"

mkdir -p "$sources"
archive="wanaku-server-${version}-linux-$(uname -m).tar.gz"
curl -fL -o "${sources}/${archive}" \
    "https://github.com/wanaku-ai/wanaku/releases/download/${release_tag}/${archive}"
cp "${rpm_dir}/../systemd/wanaku.service" "${rpm_dir}/wanaku.sysusers" "$sources/"

rpmbuild -bb "$spec" \
    --define "_topdir ${topdir}" \
    --define "release_tag ${release_tag}"
