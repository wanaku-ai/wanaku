---
title: Release Wanaku
description: "Release Wanaku from an X.Y.x branch: create the branch, start the next version on main, set the release version, and push the tag."
---

# Release Wanaku

This page shows how to cut a Wanaku release from a release branch and how to start the next development version on `main`.

## Prerequisites

- Write access to the `wanaku-ai/wanaku` repository.
- The `cargo set-version` command from [cargo-edit](https://github.com/killercup/cargo-edit). Install it with `cargo install cargo-edit`.
- Yarn 1, to set the admin UI package version.

## Version files

A version change updates these files together:

- The workspace package version in `Cargo.toml`.
- The Wanaku package versions in `Cargo.lock`.
- The admin UI package version in `ui/admin/package.json`.

The `cargo set-version` command updates `Cargo.toml` and `Cargo.lock`. The `--exclude xtask` option keeps the version of the `xtask` helper crate. The `yarn version` command updates `ui/admin/package.json`.

The release workflow builds with `--locked`. If `Cargo.lock` does not match `Cargo.toml`, the release build fails.

See [Version Convention](index.md#version-convention) for the `-dev`, `-alpha.N`, `-beta.N`, and `-rc.N` suffixes.

## Create the release branch

Releases come from release branches with the `X.Y.x` name, for example `0.4.x`. Do this step only for the first release of a minor version.

```shell
git checkout main
git pull origin main
git checkout -b 0.4.x
git push origin 0.4.x
```

## Start the next development version on main

After you create the release branch, go back to `main` and set the next development version. Use the next minor version with the `-dev` suffix. For example, after you create the `0.4.x` branch, set `main` to `0.5.0-dev`.

```shell
git checkout main
cargo set-version --workspace --exclude xtask 0.5.0-dev
yarn --cwd ui/admin version --new-version 0.5.0-dev --no-git-tag-version
git commit -a -m "Version bump to 0.5.0-dev"
git push origin main
```

## Set the release version

On the release branch, remove the prerelease suffix. For the first release of the `0.4.x` branch, the version changes from `0.4.0-dev` to `0.4.0`. For a patch release, the version changes from `0.4.1-dev` to `0.4.1`.

```shell
export RELEASE_BRANCH=0.4.x
export RELEASE_VERSION=0.4.0
export NEXT_DEVELOPMENT_VERSION=0.4.1-dev
git checkout ${RELEASE_BRANCH}
git pull origin ${RELEASE_BRANCH}
cargo set-version --workspace --exclude xtask ${RELEASE_VERSION}
yarn --cwd ui/admin version --new-version ${RELEASE_VERSION} --no-git-tag-version
git commit -a -m "Version bump to ${RELEASE_VERSION}"
git push origin ${RELEASE_BRANCH}
```

## Push the release tag

The `release` workflow starts when you push a tag with the `X.Y.Z` format. The tag name is the release version without a `v` prefix.

```shell
git tag ${RELEASE_VERSION}
git push origin ${RELEASE_VERSION}
```

The workflow builds the archives for four platforms, pushes the `wanaku-server` and `wanaku-server-headless` container images, and creates the GitHub release with JReleaser.

## Start the next patch version on the release branch

After you push the tag, set the release branch to the next patch version with the `-dev` suffix.

```shell
cargo set-version --workspace --exclude xtask ${NEXT_DEVELOPMENT_VERSION}
yarn --cwd ui/admin version --new-version ${NEXT_DEVELOPMENT_VERSION} --no-git-tag-version
git commit -a -m "Version bump to ${NEXT_DEVELOPMENT_VERSION}"
git push origin ${RELEASE_BRANCH}
```

## Next steps

- [Contributing to Wanaku](index.md): the version convention and the checks to run.
- [Build container images](container-build.md): build and push the container images locally.
